package com.tempmonitor.data.memory

import android.app.ActivityManager
import android.content.Context
import com.tempmonitor.domain.model.RamUsage
import java.io.File

/**
 * RAM total/usada do sistema, lida no mesmo tick de 1 Hz das temperaturas.
 *
 * Fonte primaria: ActivityManager.MemoryInfo (API oficial, sem root, reflete
 * o que o Android considera memoria em uso). Fallback: /proc/meminfo, para
 * ROMs em que o binder devolve totalMem = 0. Nada aqui e assincrono: quem
 * chama (ThermalRepositoryImpl.tick) ja roda em Dispatchers.IO.
 */
class MemoryReader(context: Context) {

    private val activityManager: ActivityManager? =
        context.getSystemService(ActivityManager::class.java)

    fun ramUsage(): RamUsage? {
        activityManager?.let { am ->
            val info = ActivityManager.MemoryInfo()
            val read = runCatching { am.getMemoryInfo(info) }.isSuccess
            if (read && info.totalMem > 0L) {
                return RamUsage(
                    totalBytes = info.totalMem,
                    usedBytes = (info.totalMem - info.availMem).coerceIn(0L, info.totalMem),
                )
            }
        }
        return readProcMeminfo()
    }

    /** /proc/meminfo: valores em kB. MemAvailable cobre caches; sem ele, free + buffers + cached. */
    private fun readProcMeminfo(): RamUsage? = runCatching {
        val values = HashMap<String, Long>()
        File("/proc/meminfo").useLines { lines ->
            lines.forEach { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) return@forEach
                val kb = line.substring(colon + 1).trim().takeWhile { !it.isWhitespace() }.toLongOrNull()
                if (kb != null) values[line.substring(0, colon).trim()] = kb
            }
        }
        val totalKb = values["MemTotal"] ?: return@runCatching null
        if (totalKb <= 0L) return@runCatching null
        val availableKb = values["MemAvailable"]
            ?: ((values["MemFree"] ?: 0L) + (values["Buffers"] ?: 0L) + (values["Cached"] ?: 0L))
        val usedKb = (totalKb - availableKb).coerceIn(0L, totalKb)
        RamUsage(totalBytes = totalKb * 1024L, usedBytes = usedKb * 1024L)
    }.getOrNull()
}
