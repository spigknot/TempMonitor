package com.tempmonitor.data.scanner

import com.tempmonitor.domain.model.SensorCategory

/**
 * Classificacao heuristica de sensores a partir do campo 'type' de sysfs.
 * Puro e deterministico: nenhuma dependencia de Android, testavel em JVM.
 *
 * Ordem importa:
 *  1. metricas nao-termometricas (trip/bcl/vbat...) -> UNKNOWN, nunca poluem o hotspot;
 *  2. bateria; 3. GPU; 4. NPU/DSP; 5. CPU.
 */
object SensorClassifier {

    /** Zona de CPU no formato Qualcomm cpu-<cluster>-<nucleo>-<sub>, ex.: cpu-0-2-1 / cpu-0-0-usr. */
    data class CpuZone(val cluster: Int, val core: Int, val sub: String?)

    private val batteryTerms = listOf(
        "battery", "batt", "bms", "charger", "charging", "cell1", "cell2",
        "cell_1", "cell_2", "usb", "connector", "chg", "fuel", "gauge"
    )
    private val gpuTerms = listOf("gpu", "gpuss", "kgsl", "g3d", "mali", "gfx", "adreno")
    private val npuTerms = listOf("npu", "cdsp", "nsp", "tpu", "hexagon", "adsp", "dsp", "aic", "neural")
    private val cpuTerms = listOf(
        "cpu", "cpuss", "kryo", "oryon", "cluster", "gold", "silver",
        "core", "prime", "platinum", "little", "big"
    )

    /**
     * Nos de sysfs que NAO sao termometros: limiares de protecao do hardware
     * (cpu-hw-trip-N espelha trip_point_0_temp, ex.: 95 C constante), limites de
     * corrente de bateria (bcl-lvl, ibat-lvl) e vbat. Trata-los como temperatura
     * faria o pico global marcar 95 C para sempre.
     */
    private val metaMetricTerms = listOf(
        "trip", "bcl", "ibat", "vbat", "limit", "threshold", "cooling",
        "shutdown", "mitigation", "warn", "alert"
    )
    private val levelSuffix = Regex(""".*-lvl\d+$""")

    private val aiRegex = Regex("""(^|[^a-z])ai([^a-z]|$)""")
    private val cpuZoneRegex = Regex("""^cpu[-_](\d{1,2})[-_](\d{1,2})(?:[-_]([a-z0-9]+))?$""")
    private val simpleCoreRegex = Regex("""^(?:cpu|core)[-_ ]?(\d{1,2})$""")

    fun isMetaMetric(typeRaw: String): Boolean {
        val t = typeRaw.lowercase().trim()
        if (levelSuffix.matches(t)) return true
        return metaMetricTerms.any { t.contains(it) }
    }

    fun classify(typeRaw: String, fromPowerSupply: Boolean = false): SensorCategory {
        val t = typeRaw.lowercase().trim()
        if (fromPowerSupply) return SensorCategory.BATTERY
        if (isMetaMetric(t)) return SensorCategory.UNKNOWN
        if (batteryTerms.any { t.contains(it) }) return SensorCategory.BATTERY
        if (gpuTerms.any { t.contains(it) }) return SensorCategory.GPU
        if (npuTerms.any { t.contains(it) } || aiRegex.containsMatchIn(t)) return SensorCategory.NPU
        if (cpuTerms.any { t.contains(it) }) return SensorCategory.CPU
        return SensorCategory.UNKNOWN
    }

    fun cpuZone(typeRaw: String): CpuZone? {
        val m = cpuZoneRegex.find(typeRaw.lowercase().trim()) ?: return null
        val cluster = m.groupValues[1].toIntOrNull() ?: return null
        val core = m.groupValues[2].toIntOrNull() ?: return null
        val sub = m.groupValues[3].ifEmpty { null }
        return CpuZone(cluster, core, sub)
    }

    /** Indice do nucleo quando o nome o expoe (padrao Qualcomm ou cpuN/coreN). */
    fun coreIndex(typeRaw: String): Int? {
        cpuZone(typeRaw)?.let { return it.core }
        val t = typeRaw.lowercase().trim()
        simpleCoreRegex.find(t)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    /** Cluster sugerido pelo nome; null quando o kernel nao informa. */
    fun clusterHint(typeRaw: String): String? {
        val t = typeRaw.lowercase()
        return when {
            t.contains("prime") || t.contains("oryon") || t.contains("platinum") -> "Prime / Oryon"
            t.contains("gold") || t.contains("performance") || t.contains("big") -> "Performance"
            t.contains("silver") || t.contains("efficiency") || t.contains("little") -> "Eficiencia"
            else -> null
        }
    }

    /** Agrupamento de fallback quando nenhum nome informa o cluster. */
    fun fallbackCluster(coreIndex: Int?, totalCores: Int): String? {
        if (coreIndex == null) return null
        if (totalCores < 4) return "Nucleos"
        return when {
            coreIndex == totalCores - 1 && totalCores >= 8 -> "Prime / Oryon"
            coreIndex >= totalCores / 2 -> "Performance"
            else -> "Eficiencia"
        }
    }

    /** Rotulo unico e legivel exibido na UI (preserva o esquema do kernel). */
    fun friendlyLabel(typeRaw: String, category: SensorCategory): String {
        val t = typeRaw.lowercase()
        cpuZone(typeRaw)?.let { z ->
            return if (z.sub != null) "CPU " + z.cluster + "." + z.core + "." + z.sub
            else "CPU " + z.cluster + "." + z.core
        }
        simpleCoreRegex.find(t)?.let { return "Core " + it.groupValues[1] }
        if (t.endsWith("/temp")) {
            return when (t.substringBefore("/temp")) {
                "battery" -> "Pack (BMS)"
                "usb" -> "Conector USB-C"
                "wireless" -> "Carregador wireless"
                else -> t.substringBefore("/temp")
            }
        }
        return when {
            t.contains("cell1") || t.contains("cell_1") -> "Celula 1"
            t.contains("cell2") || t.contains("cell_2") -> "Celula 2"
            t.contains("usb") || t.contains("connector") -> "Conector USB-C"
            t.contains("charger") || t.contains("chg") -> "CI de carga"
            else -> typeRaw
        }
    }
}
