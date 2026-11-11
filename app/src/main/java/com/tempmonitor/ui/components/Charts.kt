package com.tempmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.ui.theme.tempColor

/**
 * Graficos em Canvas puro (sem biblioteca externa).
 * Desenhar com DrawScope evita recomposicao de dezenas de nos por ponto:
 * apenas o Canvas e redesenhado a cada tick de 1 Hz.
 */

@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = tempColor(points.lastOrNull()),
) {
    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / span) * (size.height - 4.dp.toPx()) - 2.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        val lastY = size.height - ((points.last() - min) / span) * (size.height - 4.dp.toPx()) - 2.dp.toPx()
        drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(size.width, lastY))
    }
}

@Composable
fun FullChart(
    points: List<HistoryPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = tempColor(points.lastOrNull()?.avgCelsius)
    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            if (points.size < 2) return@Canvas
            val min = points.minOf { it.avgCelsius }
            val max = points.maxOf { it.maxCelsius }
            val span = (max - min).coerceAtLeast(2f)
            val padTop = 8.dp.toPx()
            val usableH = size.height - padTop * 2
            val stepX = size.width / (points.size - 1)

            // grade horizontal discreta
            for (i in 0..3) {
                val y = padTop + usableH * i / 3f
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y), end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun yOf(v: Float) = padTop + (1f - (v - min) / span) * usableH

            // serie de picos (max) atras, mais transparente
            val maxPath = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                if (i == 0) maxPath.moveTo(x, yOf(p.maxCelsius)) else maxPath.lineTo(x, yOf(p.maxCelsius))
            }
            drawPath(maxPath, color = lineColor.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx()))

            // serie principal (media)
            val avgPath = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                if (i == 0) avgPath.moveTo(x, yOf(p.avgCelsius)) else avgPath.lineTo(x, yOf(p.avgCelsius))
            }
            drawPath(avgPath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        if (points.isNotEmpty()) {
            Text(
                text = "max " + String.format(java.util.Locale.US, "%.0f", points.maxOf { it.maxCelsius }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
            Text(
                text = "min " + String.format(java.util.Locale.US, "%.0f", points.minOf { it.avgCelsius }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}
