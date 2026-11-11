package com.tempmonitor.data.scanner

import com.tempmonitor.data.shell.RootShellExecutor
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.SensorKind
import com.tempmonitor.domain.model.TempScale
import com.tempmonitor.domain.model.ThermalNode

/**
 * Parser puro da saida do script de descoberta (testavel em JVM).
 * Linhas: TZ|<dir>|<type> ; PSF|<arquivo temp>|<pai> ; CLK|<arquivo clock>
 */
object ScanOutputParser {

    fun parse(
        lines: List<String>,
        overrides: Map<String, SensorCategory> = emptyMap(),
    ): List<ThermalNode> {
        val nodes = LinkedHashMap<String, ThermalNode>()

        for (line in lines) {
            val parts = line.split("|")
            when (parts.getOrNull(0)) {
                "TZ" -> {
                    val dir = parts.getOrNull(1)?.trim().orEmpty()
                    val type = parts.getOrNull(2)?.trim().orEmpty()
                    if (dir.isEmpty() || type.isEmpty()) continue
                    val id = dir.substringAfterLast('/')
                    val category = SensorClassifier.classify(type)
                    nodes["$dir/temp"] = ThermalNode(
                        id = id,
                        type = type,
                        tempPath = "$dir/temp",
                        category = category,
                        scale = TempScale.MILLIDEGREE,
                        label = SensorClassifier.friendlyLabel(type, category),
                        cluster = SensorClassifier.clusterHint(type),
                    )
                }
                "PSF" -> {
                    val file = parts.getOrNull(1)?.trim().orEmpty()
                    val parent = parts.getOrNull(2)?.trim().orEmpty()
                    if (file.isEmpty()) continue
                    val fileName = file.substringAfterLast('/')
                    if (!fileName.contains("temp")) continue
                    val type = "$parent/$fileName"
                    nodes[file] = ThermalNode(
                        id = "power_supply/$type",
                        type = type,
                        tempPath = file,
                        category = SensorCategory.BATTERY,
                        scale = if (fileName == "temp") TempScale.DECIDEGREE else TempScale.AUTO,
                        label = SensorClassifier.friendlyLabel(type, SensorCategory.BATTERY),
                    )
                }
                "CLK" -> {
                    val file = parts.getOrNull(1)?.trim().orEmpty()
                    if (file.isEmpty()) continue
                    nodes[file] = ThermalNode(
                        id = "clock:$file",
                        type = "gpu_clock",
                        tempPath = file,
                        category = SensorCategory.GPU,
                        kind = SensorKind.FREQ,
                        label = "Clock GPU",
                    )
                }
            }
        }

        val cpuCores = nodes.values.filter {
            it.category == SensorCategory.CPU && SensorClassifier.coreIndex(it.type) != null
        }
        val totalCores = cpuCores.size
        val result = nodes.values.map { node ->
            var n = node
            if (n.category == SensorCategory.CPU) {
                val idx = SensorClassifier.coreIndex(n.type)
                if (idx != null) {
                    n = n.copy(
                        label = "Core $idx",
                        cluster = n.cluster ?: SensorClassifier.fallbackCluster(idx, totalCores),
                    )
                }
            }
            overrides[n.id]?.let { forced ->
                val coreIdx = SensorClassifier.coreIndex(n.type)
                n = n.copy(
                    category = forced,
                    manuallyAssigned = true,
                    label = if (forced == SensorCategory.CPU && coreIdx != null) "Core $coreIdx" else n.label,
                )
            }
            n
        }
        return result.sortedWith(compareBy({ it.category.ordinal }, { it.label }))
    }
}

/** Descoberta dinamica: nenhum caminho de sensor e hardcoded no app. */
class SysfsScanner(private val shell: RootShellExecutor) {

    suspend fun scan(overrides: Map<String, SensorCategory> = emptyMap()): List<ThermalNode> {
        val lines = shell.exec(DISCOVERY_SCRIPT)
        return ScanOutputParser.parse(lines, overrides)
    }

    companion object {
        const val DISCOVERY_SCRIPT: String =
            "for z in /sys/class/thermal/thermal_zone*; do " +
            "[ -d \"\$z\" ] || continue; echo \"TZ|\$z|\$(cat \$z/type 2>/dev/null)\"; done; " +
            "for p in /sys/class/power_supply/*; do " +
            "[ -d \"\$p\" ] || continue; " +
            "for f in \$p/*temp* \$p/*/*temp*; do " +
            "[ -f \"\$f\" ] && echo \"PSF|\$f|\$(basename \$p)\"; done; done; " +
            "for f in /sys/class/kgsl/*/gpuclk /sys/class/kgsl/*/devfreq/cur_freq " +
            "/sys/class/devfreq/*gpu*/cur_freq /sys/class/devfreq/*kgsl*/cur_freq " +
            "/sys/class/devfreq/*mali*/cur_freq; do " +
            "[ -f \"\$f\" ] && echo \"CLK|\$f|\"; done"
    }
}
