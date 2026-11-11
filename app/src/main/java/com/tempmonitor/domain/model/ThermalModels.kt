package com.tempmonitor.domain.model

enum class SensorCategory { CPU, GPU, NPU, BATTERY, UNKNOWN }
enum class SensorKind { TEMP, FREQ }
enum class TempScale { MILLIDEGREE, DECIDEGREE, CELSIUS, AUTO }

data class ThermalNode(
    val id: String,
    val type: String,
    val tempPath: String,
    val category: SensorCategory,
    val kind: SensorKind = SensorKind.TEMP,
    val scale: TempScale = TempScale.AUTO,
    val label: String = type,
    val cluster: String? = null,
    val manuallyAssigned: Boolean = false,
)

data class SensorReading(
    val nodeId: String,
    val celsius: Float?,
    val rawValue: Long? = null,
    val timestampMs: Long,
)

data class HistoryPoint(
    val timestampMs: Long,
    val avgCelsius: Float,
    val maxCelsius: Float,
)

data class SectionSummary(
    val category: SensorCategory,
    val peakCelsius: Float?,
    val avgCelsius: Float?,
    val hottestLabel: String?,
    val readings: List<Pair<ThermalNode, SensorReading>>,
    val sparkline: List<Float>,
    val extraHz: Long? = null,
)

data class ThermalState(
    val timestampMs: Long = 0L,
    val rootGranted: Boolean = false,
    val scanning: Boolean = true,
    val nodes: List<ThermalNode> = emptyList(),
    val readings: Map<String, SensorReading> = emptyMap(),
    val sections: Map<SensorCategory, SectionSummary> = emptyMap(),
    val hotspotCelsius: Float? = null,
    val hotspotLabel: String? = null,
    val cpuAvgCelsius: Float? = null,
    val batteryCelsius: Float? = null,
    val batteryApiCelsius: Float? = null,
    val error: String? = null,
) {
    fun section(category: SensorCategory): SectionSummary? = sections[category]
}

enum class TimeRange(val label: String, val durationMs: Long) {
    S10("10s", 10_000),
    S30("30s", 30_000),
    M1("1m", 60_000),
    M5("5m", 5 * 60_000),
    M10("10m", 10 * 60_000),
    M30("30m", 30 * 60_000),
    H1("1h", 3_600_000),
    H6("6h", 6 * 3_600_000L),
    H12("12h", 12 * 3_600_000L),
    D1("1d", 24 * 3_600_000L);
    val isLong: Boolean get() = durationMs > 5 * 60_000L
}
