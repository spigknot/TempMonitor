package com.tempmonitor.data.scanner

import com.tempmonitor.domain.model.SensorCategory

/**
 * Classificacao heuristica de sensores a partir do campo 'type' de sysfs.
 * Puro e deterministico: nenhuma dependencia de Android, testavel em JVM.
 * A ordem importa: bateria e GPU/NPU sao testados antes de CPU porque
 * nomes como "cpuss"/"gpuss" sao parecidos e "ai" nao pode casar substrings.
 */
object SensorClassifier {

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

    private val aiRegex = Regex("""(^|[^a-z])ai([^a-z]|$)""")
    private val coreIndexRegex = Regex("""(?:cpu|core)[-_ ]?(\d{1,2})""")

    fun classify(typeRaw: String, fromPowerSupply: Boolean = false): SensorCategory {
        val t = typeRaw.lowercase().trim()
        if (fromPowerSupply) return SensorCategory.BATTERY
        if (batteryTerms.any { t.contains(it) }) return SensorCategory.BATTERY
        if (gpuTerms.any { t.contains(it) }) return SensorCategory.GPU
        if (npuTerms.any { t.contains(it) } || aiRegex.containsMatchIn(t)) return SensorCategory.NPU
        if (cpuTerms.any { t.contains(it) }) return SensorCategory.CPU
        return SensorCategory.UNKNOWN
    }

    /** Indice fisico do nucleo (0..n) quando o nome o expoe, ex.: cpu-0-0-usr, core3. */
    fun coreIndex(typeRaw: String): Int? =
        coreIndexRegex.find(typeRaw.lowercase())?.groupValues?.get(1)?.toIntOrNull()

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

    /**
     * Agrupamento de fallback quando nenhum nome informa o cluster
     * (convencao big.LITTLE classica 4+3+1 em SoCs de 8 nucleos).
     */
    fun fallbackCluster(coreIndex: Int?, totalCores: Int): String? {
        if (coreIndex == null) return null
        if (totalCores < 4) return "Nucleos"
        return when {
            coreIndex == totalCores - 1 && totalCores >= 8 -> "Prime / Oryon"
            coreIndex >= totalCores / 2 -> "Performance"
            else -> "Eficiencia"
        }
    }

    /** Rotulo amigavel exibido na UI. */
    fun friendlyLabel(typeRaw: String, category: SensorCategory): String {
        val t = typeRaw.lowercase()
        coreIndex(typeRaw)?.let { return "Core $it" }
        return when {
            t.contains("cell1") || t.contains("cell_1") -> "Celula 1"
            t.contains("cell2") || t.contains("cell_2") -> "Celula 2"
            t.contains("usb") || t.contains("connector") -> "Conector USB-C"
            t.contains("charger") || t.contains("chg") -> "CI de carga"
            category == SensorCategory.GPU -> "GPU"
            category == SensorCategory.NPU -> "NPU / DSP"
            category == SensorCategory.BATTERY -> "Bateria"
            category == SensorCategory.CPU -> typeRaw
            else -> typeRaw
        }
    }
}
