package com.tempmonitor.domain.repository

import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.ThermalNode
import com.tempmonitor.domain.model.ThermalState
import com.tempmonitor.domain.model.TimeRange
import kotlinx.coroutines.flow.StateFlow

interface ThermalRepository {
    val state: StateFlow<ThermalState>
    val allNodes: StateFlow<List<ThermalNode>>

    suspend fun startMonitoring()
    fun stopMonitoring()
    suspend fun rescan()
    suspend fun reassignCategory(nodeId: String, category: SensorCategory)
    fun historyFor(nodeId: String, range: TimeRange): List<HistoryPoint>
    fun sectionHistory(category: SensorCategory, range: TimeRange): List<HistoryPoint>

    companion object { const val MAX_CHART_POINTS = 200 }
}
