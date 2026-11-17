package com.tempmonitor.ui.theme

import java.util.Locale

/**
 * Formatadores puros (sem Android/Compose) para RAM e percentuais.
 * Base 1024 com rotulo curto: 12 GiB -> "12.0 GB", 512 MiB -> "512 MB".
 * Ficam fora do Theme.kt para poderem ser cobertos por teste JVM.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gib = bytes / GIB
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        String.format(Locale.US, "%.0f MB", bytes / MIB)
    }
}

fun formatPercent(fraction: Float): String =
    String.format(Locale.US, "%.0f%%", fraction.coerceIn(0f, 1f) * 100f)

private const val MIB = 1024.0 * 1024.0
private const val GIB = 1024.0 * 1024.0 * 1024.0
