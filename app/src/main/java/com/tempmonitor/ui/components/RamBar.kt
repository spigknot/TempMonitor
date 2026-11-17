package com.tempmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.RamUsage
import com.tempmonitor.ui.theme.formatBytes
import com.tempmonitor.ui.theme.formatPercent
import com.tempmonitor.ui.theme.ramColor

/**
 * Barra de memoria RAM: total, usada e livre.
 *
 * [ram] vem do mesmo ThermalState emitido a 1 Hz pelo loop unico, entao a
 * barra anda junto das temperaturas sem um segundo timer. A fracao e animada
 * para o preenchimento deslizar em vez de saltar a cada tick.
 */
@Composable
fun RamBar(ram: RamUsage?, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = ram?.usedFraction ?: 0f,
        animationSpec = tween(durationMillis = 600),
        label = "ramFraction",
    )
    val accent by animateColorAsState(
        targetValue = ramColor(fraction),
        animationSpec = tween(durationMillis = 600),
        label = "ramAccent",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MEMORIA RAM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Total " + (ram?.let { formatBytes(it.totalBytes) } ?: "--"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = ram?.let { formatPercent(it.usedFraction) } ?: "--%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }

            Spacer(Modifier.height(12.dp))
            RamTrack(fraction = fraction, accent = accent)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Usada " + (ram?.let { formatBytes(it.usedBytes) } ?: "--"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Livre " + (ram?.let { formatBytes(it.freeBytes) } ?: "--"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Trilho arredondado + preenchimento em gradiente, com um brilho superior discreto. */
@Composable
private fun RamTrack(fraction: Float, accent: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val filledFraction = fraction.coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val radius = size.height / 2f
        drawRoundRect(color = track, cornerRadius = CornerRadius(radius, radius))
        if (filledFraction <= 0f) return@Canvas
        val filledWidth = (size.width * filledFraction).coerceAtLeast(size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(accent.copy(alpha = 0.55f), accent),
                endX = filledWidth,
            ),
            size = Size(filledWidth, size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                endY = size.height / 2f,
            ),
            size = Size(filledWidth, size.height / 2f),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}
