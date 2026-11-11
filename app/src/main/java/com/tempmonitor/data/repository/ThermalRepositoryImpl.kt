package com.tempmonitor.data.repository

import com.tempmonitor.data.battery.BatteryReader
import com.tempmonitor.data.history.ThermalHistoryStore
import com.tempmonitor.data.local.SensorMappingStore
import com.tempmonitor.data.scanner.SysfsScanner
import com.tempmonitor.data.scanner.TempScaling
import com.tempmonitor.data.shell.RootShellExecutor
import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.SectionSummary
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.SensorKind
import com.tempmonitor.domain.model.SensorReading
import com.tempmonitor.domain.model.ThermalNode
import com.tempmonitor.domain.model.ThermalState
import com.tempmonitor.domain.model.TimeRange
import com.tempmonitor.domain.repository.ThermalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor de leitura em tempo real: 1 Hz compensado, Dispatchers.IO,
 * uma unica invocacao de shell por ciclo, sessao root persistente (libsu).
 */
class ThermalRepositoryImpl(
    private val shell: RootShellExecutor,
    private val scanner: SysfsScanner,
    private val store: SensorMappingStore,
    private val batteryReader: BatteryReader,
    private val history: ThermalHistoryStore = ThermalHistoryStore(),
) : ThermalRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val _state = MutableStateFlow(ThermalState(scanning = true))
    override val state: StateFlow<ThermalState> = _state.asStateFlow()

    private val _allNodes = MutableStateFlow<List<ThermalNode>>(emptyList())
    override val allNodes: StateFlow<List<ThermalNode>> = _allNodes.asStateFlow()

    @Volatile private var nodes: List<ThermalNode> = emptyList()

    override suspend fun startMonitoring() {
        if (loopJob?.isActive == true) return
        ensureDiscovered()
        loopJob = scope.launch { runLoop() }
    }

    override fun stopMonitoring() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun ensureDiscovered() {
        if (nodes.isNotEmpty()) return
        val cached = store.loadNodes()
        if (cached.isNotEmpty()) {
            nodes = cached
            _allNodes.value = cached
        }
        if (shell.isRootGranted()) {
            val scanned = scanner.scan(store.currentOverrides())
            if (scanned.isNotEmpty()) {
                nodes = scanned
                _allNodes.value = scanned
                store.saveNodes(scanned)
            }
        }
        _state.value = _state.value.copy(nodes = nodes, scanning = false)
    }

    override suspend fun rescan() {
        _state.value = _state.value.copy(scanning = true)
        withContext(Dispatchers.IO) {
            if (shell.isRootGranted()) {
                val scanned = scanner.scan(store.currentOverrides())
                if (scanned.isNotEmpty()) {
                    nodes = scanned
                    _allNodes.value = scanned
                    store.saveNodes(scanned)
                }
            }
        }
        _state.value = _state.value.copy(nodes = nodes, scanning = false)
    }

    override suspend fun reassignCategory(nodeId: String, category: SensorCategory) {
        store.setOverride(nodeId, category)
        nodes = nodes.map { if (it.id == nodeId) it.copy(category = category, manuallyAssigned = true) else it }
        _allNodes.value = nodes
        store.saveNodes(nodes)
        _state.value = _state.value.copy(nodes = nodes)
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val startedAt = System.currentTimeMillis()
            tick(startedAt)
            val elapsed = System.currentTimeMillis() - startedAt
            delay((TICK_MS - elapsed).coerceAtLeast(0L))
        }
    }

    private suspend fun tick(nowMs: Long) {
        val root = shell.isRootGranted()
        val batteryApi = batteryReader.packTemperatureCelsius()

        if (!root) {
            _state.value = ThermalState(
                timestampMs = nowMs,
                rootGranted = false,
                scanning = false,
                nodes = nodes,
                batteryApiCelsius = batteryApi,
                batteryCelsius = batteryApi,
                sections = batteryOnlySection(batteryApi),
                error = "Root nao concedido. Conceda acesso no Magisk/KernelSU/APatch; " +
                    "sem root, apenas a temperatura oficial da bateria esta disponivel.",
            )
            return
        }

        if (nodes.isEmpty()) ensureDiscovered()
        val ordered = nodes
        // UMA invocacao de shell por segundo para todos os nos mapeados.
        val raws = shell.readAll(ordered.map { it.tempPath })

        val readings = LinkedHashMap<String, SensorReading>()
        ordered.forEachIndexed { index, node ->
            val raw = raws.getOrNull(index)
            if (node.kind == SensorKind.FREQ) {
                readings[node.id] = SensorReading(node.id, celsius = null, rawValue = raw, timestampMs = nowMs)
            } else {
                val celsius = raw?.let { TempScaling.toCelsius(it, node.scale) }
                    ?.takeIf { TempScaling.isPlausible(it) }
                readings[node.id] = SensorReading(node.id, celsius, raw, nowMs)
                if (celsius != null) history.add(node.id, nowMs, celsius)
            }
        }

        val sections = buildSections(ordered, readings, batteryApi)
        val socReadings = listOfNotNull(
            sections[SensorCategory.CPU], sections[SensorCategory.GPU]
        ).flatMap { it.readings }
        val hotspot = socReadings.maxByOrNull { it.second.celsius ?: Float.MIN_VALUE }
            ?.takeIf { it.second.celsius != null }

        _state.value = ThermalState(
            timestampMs = nowMs,
            rootGranted = true,
            scanning = false,
            nodes = ordered,
            readings = readings,
            sections = sections,
            hotspotCelsius = hotspot?.second?.celsius,
            hotspotLabel = hotspot?.first?.let { node -> node.category.name + " - " + node.label },
            cpuAvgCelsius = sections[SensorCategory.CPU]?.avgCelsius,
            batteryCelsius = sections[SensorCategory.BATTERY]?.peakCelsius ?: batteryApi,
            batteryApiCelsius = batteryApi,
        )
    }

    private fun buildSections(
        all: List<ThermalNode>,
        readings: Map<String, SensorReading>,
        batteryApi: Float?,
    ): Map<SensorCategory, SectionSummary> {
        val result = LinkedHashMap<SensorCategory, SectionSummary>()
        for (category in listOf(SensorCategory.CPU, SensorCategory.GPU, SensorCategory.NPU, SensorCategory.BATTERY)) {
            val members = all.filter { it.category == category && it.kind == SensorKind.TEMP }
            val pairs = members.mapNotNull { n -> readings[n.id]?.let { r -> n to r } }
            val temps = pairs.mapNotNull { it.second.celsius }
            val hottest = pairs.maxByOrNull { it.second.celsius ?: Float.MIN_VALUE }
                ?.takeIf { it.second.celsius != null }
            val freqHz = all.firstOrNull { it.category == category && it.kind == SensorKind.FREQ }
                ?.let { readings[it.id]?.rawValue }

            var peak = if (temps.isEmpty()) null else temps.max()
            var avg = if (temps.isEmpty()) null else temps.average().toFloat()
            var hottestLabel = hottest?.first?.label
            if (category == SensorCategory.BATTERY && batteryApi != null && peak == null) {
                peak = batteryApi; avg = batteryApi; hottestLabel = "Pack (BatteryManager)"
            }
            result[category] = SectionSummary(
                category = category,
                peakCelsius = peak,
                avgCelsius = avg,
                hottestLabel = hottestLabel,
                readings = pairs,
                sparkline = history.sparklineFor(members.map { it.id }),
                extraHz = freqHz,
            )
        }
        return result
    }

    private fun batteryOnlySection(batteryApi: Float?): Map<SensorCategory, SectionSummary> {
        if (batteryApi == null) return emptyMap()
        return mapOf(
            SensorCategory.BATTERY to SectionSummary(
                category = SensorCategory.BATTERY,
                peakCelsius = batteryApi,
                avgCelsius = batteryApi,
                hottestLabel = "Pack (BatteryManager)",
                readings = emptyList(),
                sparkline = emptyList(),
            )
        )
    }

    override fun historyFor(nodeId: String, range: TimeRange): List<HistoryPoint> =
        history.query(nodeId, range, System.currentTimeMillis())

    override fun sectionHistory(category: SensorCategory, range: TimeRange): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        val memberIds = nodes.filter { it.category == category && it.kind == SensorKind.TEMP }.map { it.id }
        return history.merge(memberIds.map { history.query(it, range, now) }, range)
    }

    companion object { const val TICK_MS = 1_000L }
}
