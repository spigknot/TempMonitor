package com.tempmonitor.data.history

import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.TimeRange
import com.tempmonitor.domain.repository.ThermalRepository

/**
 * Historico em memoria com amostragem adaptativa.
 *
 * - Janelas curtas (<= 1 min): amostras puras de 1 Hz do buffer circular.
 * - Janelas medias/longas: buckets temporais com media + pico (max), o que
 *   preserva hotspots sem reter 86.400 amostras/dia por sensor.
 * - Toda consulta e limitada a [ThermalRepository.MAX_CHART_POINTS] pontos.
 *
 * Classe pura (sem Android) para teste em JVM. Nao e thread-safe por si so:
 * o repositorio a acessa sempre do mesmo dispatcher/confined scope.
 */
class ThermalHistoryStore(
    private val maxRawPerSensor: Int = 3_600,          // 1 h a 1 Hz
    private val bucketMs: Long = 60_000L,              // buckets de 1 min
    private val maxBucketsPerSensor: Int = 1_440,      // 24 h de buckets
) {
    private data class Bucket(var sum: Double = 0.0, var max: Float = Float.MIN_VALUE, var count: Int = 0) {
        fun add(v: Float) { sum += v; if (v > max) max = v; count++ }
        fun toPoint(ts: Long) = HistoryPoint(ts, (sum / count).toFloat(), max)
    }

    private val raw = HashMap<String, ArrayDeque<HistoryPoint>>()
    private val buckets = HashMap<String, LinkedHashMap<Long, Bucket>>()
    private val spark = HashMap<String, ArrayDeque<Float>>()

    fun add(nodeId: String, timestampMs: Long, celsius: Float) {
        val deque = raw.getOrPut(nodeId) { ArrayDeque() }
        deque.addLast(HistoryPoint(timestampMs, celsius, celsius))
        while (deque.size > maxRawPerSensor) deque.removeFirst()

        val s = spark.getOrPut(nodeId) { ArrayDeque() }
        s.addLast(celsius)
        while (s.size > 30) s.removeFirst()

        val key = timestampMs / bucketMs * bucketMs
        val map = buckets.getOrPut(nodeId) { LinkedHashMap() }
        map.getOrPut(key) { Bucket() }.add(celsius)
        while (map.size > maxBucketsPerSensor) map.remove(map.keys.first())
    }

    /** Ultimos 30 s puros para o sparkline do card colapsado. */
    fun sparkline(nodeId: String): List<Float> = spark[nodeId]?.toList().orEmpty()

    fun sparklineFor(nodeIds: Collection<String>): List<Float> {
        // Sparkline da secao = serie do sensor mais quente no instante atual.
        val hottest = nodeIds.maxByOrNull { spark[it]?.lastOrNull() ?: Float.MIN_VALUE } ?: return emptyList()
        return sparkline(hottest)
    }

    fun query(nodeId: String, range: TimeRange, nowMs: Long): List<HistoryPoint> {
        val from = nowMs - range.durationMs
        val source: List<HistoryPoint> = if (range.durationMs <= 60_000L) {
            raw[nodeId]?.filter { it.timestampMs >= from }.orEmpty()
        } else {
            // Para janelas medias usa raw enquanto houver cobertura; senao buckets.
            val rawWindow = raw[nodeId]?.filter { it.timestampMs >= from }.orEmpty()
            if (rawWindow.isNotEmpty() && nowMs - (rawWindow.firstOrNull()?.timestampMs ?: nowMs) < range.durationMs / 2) {
                rawWindow
            } else {
                buckets[nodeId]?.entries
                    ?.filter { it.key >= from }
                    ?.map { (ts, b) -> b.toPoint(ts) }
                    .orEmpty()
            }
        }
        return downsample(source, ThermalRepository.MAX_CHART_POINTS)
    }

    /** Mescla series de varios sensores (media das medias, max dos picos) por bucket temporal. */
    fun merge(series: List<List<HistoryPoint>>, range: TimeRange): List<HistoryPoint> {
        val flat = series.flatten()
        if (flat.isEmpty()) return emptyList()
        val width = maxOf(1L, range.durationMs / ThermalRepository.MAX_CHART_POINTS)
        return flat.groupBy { it.timestampMs / width * width }
            .entries.sortedBy { it.key }
            .map { (ts, pts) ->
                HistoryPoint(
                    timestampMs = ts,
                    avgCelsius = pts.map { it.avgCelsius }.average().toFloat(),
                    maxCelsius = pts.maxOf { it.maxCelsius },
                )
            }
            .takeLast(ThermalRepository.MAX_CHART_POINTS)
    }

    fun clear() { raw.clear(); buckets.clear(); spark.clear() }

    companion object {
        /** Reduz [points] a no maximo [maxPoints] preservando media e pico de cada grupo. */
        fun downsample(points: List<HistoryPoint>, maxPoints: Int): List<HistoryPoint> {
            if (points.size <= maxPoints) return points
            val groupSize = (points.size + maxPoints - 1) / maxPoints
            return points.chunked(groupSize).map { group ->
                HistoryPoint(
                    timestampMs = group.last().timestampMs,
                    avgCelsius = group.map { it.avgCelsius }.average().toFloat(),
                    maxCelsius = group.maxOf { it.maxCelsius },
                )
            }
        }
    }
}
