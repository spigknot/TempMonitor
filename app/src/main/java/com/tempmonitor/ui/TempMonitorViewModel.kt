package com.tempmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tempmonitor.TempMonitorApplication
import com.tempmonitor.di.AppContainer
import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.ThermalNode
import com.tempmonitor.domain.model.ThermalState
import com.tempmonitor.domain.model.TimeRange
import com.tempmonitor.service.ThermalMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel UDF/MVI-lite:
 *  - Estado unico e imutavel vem do repositorio (StateFlow 1 Hz).
 *  - Intencoes da UI entram apenas pelos metodos on*.
 *  - Estado local de UI (card expandido, faixa, dialogos) fica separado
 *    do estado termico para nao recompor secoes invisiveis.
 */
class TempMonitorViewModel(
    private val app: TempMonitorApplication,
    private val container: AppContainer,
) : ViewModel() {

    val thermalState: StateFlow<ThermalState> =
        container.observeThermalState().stateIn(
            viewModelScope, SharingStarted.Eagerly, ThermalState(scanning = true)
        )

    val allNodes: StateFlow<List<ThermalNode>> = container.thermalRepository.allNodes

    data class UiLocalState(
        val expanded: SensorCategory? = null,
        val range: TimeRange = TimeRange.S30,
        val showRawNodes: Boolean = false,
        val backgroundEnabled: Boolean = false,
    )

    private val _uiLocal = MutableStateFlow(UiLocalState())
    val uiLocal: StateFlow<UiLocalState> = _uiLocal.asStateFlow()

    init {
        viewModelScope.launch { container.thermalRepository.startMonitoring() }
        // Se o usuario parar o servico pelo botao da notificacao, o switch
        // da tela acompanha (fonte da verdade e o servico).
        viewModelScope.launch {
            ThermalMonitorService.running.collect { running ->
                _uiLocal.value = _uiLocal.value.copy(backgroundEnabled = running)
            }
        }
    }

    override fun onCleared() {
        // Libera a assinatura da UI; se o servico continuar ativo, a coleta segue.
        container.thermalRepository.stopMonitoring()
        super.onCleared()
    }

    fun onToggleExpand(category: SensorCategory) {
        _uiLocal.value = _uiLocal.value.copy(
            expanded = if (_uiLocal.value.expanded == category) null else category
        )
    }

    fun onRangeSelected(range: TimeRange) {
        _uiLocal.value = _uiLocal.value.copy(range = range)
    }

    fun onShowRawNodes(show: Boolean) {
        _uiLocal.value = _uiLocal.value.copy(showRawNodes = show)
    }

    fun onToggleBackground(enabled: Boolean) {
        _uiLocal.value = _uiLocal.value.copy(backgroundEnabled = enabled) // otimista; o servico confirma
        if (enabled) ThermalMonitorService.start(app) else ThermalMonitorService.stop(app)
    }

    fun onRescan() {
        viewModelScope.launch { container.thermalRepository.rescan() }
    }

    fun onReassign(nodeId: String, category: SensorCategory) {
        viewModelScope.launch { container.thermalRepository.reassignCategory(nodeId, category) }
    }

    /** Grafico completo do card expandido (ja com downsampling, <= 200 pontos). */
    fun sectionHistory(category: SensorCategory, range: TimeRange): List<HistoryPoint> =
        container.thermalRepository.sectionHistory(category, range)

    companion object {
        fun factory(app: TempMonitorApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TempMonitorViewModel(app, app.container) as T
            }
    }
}
