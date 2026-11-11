package com.tempmonitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.ThermalState
import com.tempmonitor.ui.theme.formatTemp
import com.tempmonitor.ui.theme.tempColor

/** Cabecalho global: pico termico (hotspot) do SoC no segundo atual. */
@Composable
fun HeroHeader(state: ThermalState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "PICO TERMICO GLOBAL (HOTSPOT)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    text = formatTemp(state.hotspotCelsius),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = tempColor(state.hotspotCelsius),
                )
            }
            Text(
                text = state.hotspotLabel?.let { "Componente mais quente: $it" }
                    ?: "Aguardando leitura dos sensores do SoC...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Media CPU: " + formatTemp(state.cpuAvgCelsius) +
                    "   |   Bateria: " + formatTemp(state.batteryCelsius),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (state.rootGranted) "Root: concedido - leitura sysfs a 1 Hz"
                else "Root: nao concedido - modo limitado (BatteryManager)",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.rootGranted) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}
