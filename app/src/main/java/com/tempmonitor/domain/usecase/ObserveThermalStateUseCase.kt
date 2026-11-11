package com.tempmonitor.domain.usecase

import com.tempmonitor.domain.model.ThermalState
import com.tempmonitor.domain.repository.ThermalRepository
import kotlinx.coroutines.flow.StateFlow

/** UDF: a UI observa apenas este fluxo; intencoes voltam via ViewModel -> repositorio. */
class ObserveThermalStateUseCase(private val repository: ThermalRepository) {
    operator fun invoke(): StateFlow<ThermalState> = repository.state
}
