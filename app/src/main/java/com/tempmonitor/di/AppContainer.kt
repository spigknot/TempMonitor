package com.tempmonitor.di

import android.content.Context
import com.tempmonitor.data.battery.BatteryReader
import com.tempmonitor.data.history.ThermalHistoryStore
import com.tempmonitor.data.local.SensorMappingStore
import com.tempmonitor.data.memory.MemoryReader
import com.tempmonitor.data.repository.ThermalRepositoryImpl
import com.tempmonitor.data.scanner.SysfsScanner
import com.tempmonitor.data.shell.FallbackRootShellExecutor
import com.tempmonitor.domain.repository.ThermalRepository
import com.tempmonitor.domain.usecase.ObserveThermalStateUseCase

/** DI manual simples (sem Hilt): um unico grafo, facil de testar e de auditar. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val shell = FallbackRootShellExecutor()
    val scanner = SysfsScanner(shell)
    val mappingStore = SensorMappingStore(appContext)
    val batteryReader = BatteryReader(appContext)
    val memoryReader = MemoryReader(appContext)
    val historyStore = ThermalHistoryStore()

    val thermalRepository: ThermalRepository = ThermalRepositoryImpl(
        shell = shell,
        scanner = scanner,
        store = mappingStore,
        batteryReader = batteryReader,
        memoryReader = memoryReader,
        history = historyStore,
    )

    val observeThermalState = ObserveThermalStateUseCase(thermalRepository)
}
