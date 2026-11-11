package com.tempmonitor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.SectionSummary
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.TimeRange
import com.tempmonitor.ui.theme.formatHz
import com.tempmonitor.ui.theme.formatTemp
import com.tempmonitor.ui.theme.tempColor

fun categoryTitle(category: SensorCategory): String = when (category) {
    SensorCategory.CPU -> "CPU"
    SensorCategory.GPU -> "GPU"
    SensorCategory.NPU -> "NPU / DSP"
    SensorCategory.BATTERY -> "BATERIA"
    SensorCategory.UNKNOWN -> "OUTROS"
}

fun categoryIcon(category: SensorCategory): ImageVector = when (category) {
    SensorCategory.CPU -> Icons.Filled.Memory
    SensorCategory.GPU -> Icons.Filled.SportsEsports
    SensorCategory.NPU -> Icons.Filled.Psychology
    SensorCategory.BATTERY -> Icons.Filled.BatteryFull
    SensorCategory.UNKNOWN -> Icons.Filled.Memory
}

/**
 * Card modular em acordeao (master-detail inline).
 *
 * Desempenho: o detalhamento (lista de sensores + grafico completo) so e
 * composto dentro de AnimatedVisibility quando expandido. Cards colapsados
 * leem apenas pico + sparkline de 30 s, entao o tick de 1 Hz nao recompoe
 * nenhum no de UI invisivel.
 */
@Composable
fun SensorSectionCard(
    summary: SectionSummary?,
    expanded: Boolean,
    range: TimeRange,
    history: List<HistoryPoint>,
    batteryApiCelsius: Float?,
    onToggle: () -> Unit,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = summary?.category ?: SensorCategory.UNKNOWN
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.animateContentSize().padding(16.dp)) {
            // Cabecalho sempre visivel (estado colapsado)
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(category), contentDescription = null, tint = tempColor(summary?.peakCelsius))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(categoryTitle(category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = summary?.hottestLabel?.let { "Mais quente: $it" } ?: "Sem sensores mapeados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatTemp(summary?.peakCelsius),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = tempColor(summary?.peakCelsius),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Recolher" else "Expandir",
                )
            }

            if (summary != null && summary.sparkline.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(points = summary.sparkline)
                Text(
                    text = "ultimos 30 s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Detalhe: SO existe na composicao quando expandido.
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    TimeRangeSelector(selected = range, onSelected = onRangeSelected)
                    Spacer(Modifier.height(8.dp))
                    if (history.size >= 2) {
                        FullChart(points = history)
                    } else {
                        Text(
                            "Coletando amostras para o grafico...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (summary != null) SectionDetail(summary = summary, batteryApiCelsius = batteryApiCelsius)
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(selected: TimeRange, onSelected: (TimeRange) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(TimeRange.values().toList()) { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelected(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun SectionDetail(summary: SectionSummary, batteryApiCelsius: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        summary.avgCelsius?.let {
            DetailRow("Media da secao", formatTemp(it), it)
        }
        if (summary.category == SensorCategory.GPU && summary.extraHz != null) {
            DetailRow("Clock GPU", formatHz(summary.extraHz), null)
        }
        if (summary.category == SensorCategory.BATTERY && batteryApiCelsius != null) {
            DetailRow("Pack (BatteryManager)", formatTemp(batteryApiCelsius), batteryApiCelsius)
        }

        if (summary.category == SensorCategory.CPU) {
            // CPU discriminada por nucleo, agrupada por cluster.
            val grouped = summary.readings.groupBy { it.first.cluster ?: "Nucleos" }
            grouped.forEach { (cluster, pairs) ->
                Text(
                    cluster,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                pairs.sortedBy { it.first.label }.forEach { (node, reading) ->
                    DetailRow(node.label, formatTemp(reading.celsius), reading.celsius, subtitle = node.type)
                }
            }
        } else {
            summary.readings.sortedBy { it.first.label }.forEach { (node, reading) ->
                DetailRow(node.label, formatTemp(reading.celsius), reading.celsius, subtitle = node.type)
            }
        }

        if (summary.readings.isEmpty() && summary.category != SensorCategory.BATTERY) {
            Text(
                "Nenhum sensor desta categoria foi mapeado pela heuristica. " +
                    "Use 'Nos brutos' para reatribuir manualmente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, celsius: Float?, subtitle: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null && subtitle != label) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (celsius != null) tempColor(celsius) else MaterialTheme.colorScheme.onSurface,
        )
    }
}
