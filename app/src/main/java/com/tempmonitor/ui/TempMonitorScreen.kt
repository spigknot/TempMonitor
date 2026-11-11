package com.tempmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.ui.components.HeroHeader
import com.tempmonitor.ui.components.RawNodesDialog
import com.tempmonitor.ui.components.SensorSectionCard

/**
 * Tela unica rolavel (single-screen): hero + 4 cards em acordeao + controles.
 * Cards colapsados nao compoem o detalhe; apenas o expandido le historico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMonitorScreen(viewModel: TempMonitorViewModel) {
    val state by viewModel.thermalState.collectAsState()
    val ui by viewModel.uiLocal.collectAsState()
    val nodes by viewModel.allNodes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TempMonitor") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroHeader(state = state) }

            state.error?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.scanning) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(
                            "  Descobrindo sensores em /sys/class/thermal e /sys/class/power_supply...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Quatro secoes modulares. O historico so e consultado para o card expandido.
            for (category in listOf(
                SensorCategory.CPU, SensorCategory.GPU, SensorCategory.NPU, SensorCategory.BATTERY
            )) {
                item(key = category.name) {
                    val expanded = ui.expanded == category
                    SensorSectionCard(
                        summary = state.section(category),
                        expanded = expanded,
                        range = ui.range,
                        history = if (expanded) viewModel.sectionHistory(category, ui.range) else emptyList(),
                        batteryApiCelsius = state.batteryApiCelsius,
                        onToggle = { viewModel.onToggleExpand(category) },
                        onRangeSelected = viewModel::onRangeSelected,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Historico em segundo plano", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Foreground Service para faixas > 5 min com a tela desligada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ui.backgroundEnabled,
                            onCheckedChange = viewModel::onToggleBackground,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::onRescan) { Text("Reescanear sensores") }
                        OutlinedButton(onClick = { viewModel.onShowRawNodes(true) }) { Text("Nos brutos") }
                    }
                    Text(
                        "Sensores mapeados: " + state.nodes.size +
                            "  |  Atualizacao: 1 Hz (shell root persistente)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (ui.showRawNodes) {
        RawNodesDialog(
            nodes = nodes,
            readings = state.readings.mapValues { it.value.celsius },
            onReassign = viewModel::onReassign,
            onDismiss = { viewModel.onShowRawNodes(false) },
        )
    }
}
