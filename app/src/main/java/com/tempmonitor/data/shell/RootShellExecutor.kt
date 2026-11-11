package com.tempmonitor.data.shell

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Executor de shell root com sessao persistente.
 *
 * Regra de ouro do projeto: NUNCA criar um processo 'su' por ciclo.
 * O libsu mantem um shell interativo vivo durante todo o processo do app;
 * cada leitura de 1 Hz e submetida a esse mesmo shell.
 */
interface RootShellExecutor {
    suspend fun isRootGranted(): Boolean
    suspend fun exec(command: String): List<String>

    /** Le todos os [paths] em UMA unica invocacao de shell, na mesma ordem. */
    suspend fun readAll(paths: List<String>): List<Long?>
}

class LibSuRootShellExecutor : RootShellExecutor {

    override suspend fun isRootGranted(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    override suspend fun exec(command: String): List<String> = withContext(Dispatchers.IO) {
        // Shell.cmd(...).exec() reutiliza o shell principal persistente do libsu.
        Shell.cmd(command).exec().out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    override suspend fun readAll(paths: List<String>): List<Long?> {
        if (paths.isEmpty()) return emptyList()
        // Um unico pipeline: cat p1; echo SEP; cat p2; echo SEP; ...
        // Arquivo ilegivel gera segmento vazio -> null, sem desalinhar os demais.
        val command = paths.joinToString(separator = "; echo $SEPARATOR; ") { "cat '$it' 2>/dev/null" }
        val lines = exec(command)
        val segments = lines.joinToString("\n").split(SEPARATOR)
        return paths.indices.map { i ->
            segments.getOrNull(i)?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.toLongOrNull()
        }
    }

    companion object { const val SEPARATOR = "__TM_SEP__" }
}

/**
 * Fallback sem libsu: um unico processo 'su' interativo mantido vivo,
 * com protocolo de marcador (echo __TM_DONE__) para delimitar respostas.
 * Usado apenas se o shell do libsu nao puder ser criado.
 */
class PersistentSuShellExecutor : RootShellExecutor {
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null
    private val lock = Any()

    override suspend fun isRootGranted(): Boolean = withContext(Dispatchers.IO) {
        runCatching { exec("id -u").firstOrNull() == "0" }.getOrDefault(false)
    }

    override suspend fun exec(command: String): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            ensureProcess()
            val writer = stdin ?: return@synchronized emptyList()
            val reader = stdout ?: return@synchronized emptyList()
            writer.write(command); writer.newLine()
            writer.write("echo __TM_DONE__"); writer.newLine(); writer.flush()
            val out = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.trim() == "__TM_DONE__") break
                if (line.isNotBlank()) out += line.trim()
            }
            out
        }
    }

    override suspend fun readAll(paths: List<String>): List<Long?> {
        if (paths.isEmpty()) return emptyList()
        val command = paths.joinToString(separator = "; echo __TM_SEP__; ") { "cat '$it' 2>/dev/null" }
        val lines = exec(command)
        val segments = lines.joinToString("\n").split("__TM_SEP__")
        return paths.indices.map { i ->
            segments.getOrNull(i)?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.toLongOrNull()
        }
    }

    private fun ensureProcess() {
        if (process?.isAlive == true) return
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        process = p
        stdin = BufferedWriter(OutputStreamWriter(p.outputStream))
        stdout = BufferedReader(InputStreamReader(p.inputStream))
    }
}

/** Tenta libsu; se indisponivel, cai para o shell 'su' persistente proprio. */
class FallbackRootShellExecutor(
    private val primary: RootShellExecutor = LibSuRootShellExecutor(),
    private val fallback: RootShellExecutor = PersistentSuShellExecutor(),
) : RootShellExecutor {
    @Volatile private var useFallback = false
    private suspend fun <T> run(block: suspend (RootShellExecutor) -> T, default: T): T =
        if (useFallback) runCatching { block(fallback) }.getOrDefault(default)
        else runCatching { block(primary) }.getOrElse {
            useFallback = true
            runCatching { block(fallback) }.getOrDefault(default)
        }

    override suspend fun isRootGranted(): Boolean = run({ it.isRootGranted() }, false)
    override suspend fun exec(command: String): List<String> = run({ it.exec(command) }, emptyList())
    override suspend fun readAll(paths: List<String>): List<Long?> =
        run({ it.readAll(paths) }, List(paths.size) { null })
}
