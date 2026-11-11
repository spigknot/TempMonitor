package com.tempmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TempMonitorColors = darkColorScheme(
    primary = Color(0xFFFF7043),
    onPrimary = Color(0xFF1A0E08),
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF141B24),
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF1C2634),
    onSurfaceVariant = Color(0xFF9FB0C3),
    error = Color(0xFFEF5350),
)

/** Cor semantica por faixa termica (usada em numeros, sparklines e graficos). */
fun tempColor(celsius: Float?): Color = when {
    celsius == null -> Color(0xFF9FB0C3)
    celsius < 45f -> Color(0xFF66BB6A)
    celsius < 60f -> Color(0xFFFFCA28)
    celsius < 75f -> Color(0xFFFF7043)
    else -> Color(0xFFEF5350)
}

fun formatTemp(celsius: Float?): String =
    if (celsius == null) "--.- C" else String.format(java.util.Locale.US, "%.1f C", celsius)

fun formatHz(hz: Long?): String {
    if (hz == null) return ""
    return when {
        hz >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2f GHz", hz / 1e9)
        hz >= 1_000_000L -> String.format(java.util.Locale.US, "%.0f MHz", hz / 1e6)
        hz >= 1_000L -> String.format(java.util.Locale.US, "%.0f kHz", hz / 1e3)
        else -> hz.toString() + " Hz"
    }
}

@Composable
fun TempMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TempMonitorColors, content = content)
}
