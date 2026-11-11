package com.tempmonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.SensorKind
import com.tempmonitor.domain.model.ThermalNode
import com.tempmonitor.ui.theme.formatTemp
import com.tempmonitor.ui.theme.tempColor

/**
 * Fallback manual: lista TODOS os nos brutos descobertos (id, type, leitura
 * atual) e permite reatribuir a categoria de qualquer um. A escolha e
 * persistida em DataStore e sobrevive ao rescan.
 */
@Composable
fun RawNodesDialog(
    nodes: List<ThermalNode>,
    readings: Map<String, Float?>,
    onReassign: (nodeId: String, category: SensorCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nos termicos brutos (" + nodes.size + ")") },
        text = {
            if (nodes.isEmpty()) {
                Text("Nenhum no descoberto. Verifique o root e toque em Reescanear.")
            } else {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(nodes, key = { it.id }) { node ->
                        RawNodeRow(
                            node = node,
                            celsius = readings[node.id],
                            onReassign = onReassign,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun RawNodeRow(
    node: ThermalNode,
    celsius: Float?,
    onReassign: (nodeId: String, category: SensorCategory) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.id,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "type: " + node.type + "  |  " + node.tempPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (node.manuallyAssigned) {
                    Text(
                        "atribuido manualmente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (node.kind == SensorKind.TEMP) {
                Text(
                    formatTemp(celsius),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = tempColor(celsius),
                )
            }
        }
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text("Categoria: " + categoryTitle(node.category))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SensorCategory.values().forEach { category ->
                    DropdownMenuItem(
                        text = { Text(categoryTitle(category)) },
                        onClick = {
                            menuOpen = false
                            onReassign(node.id, category)
                        },
                    )
                }
            }
        }
    }
}
