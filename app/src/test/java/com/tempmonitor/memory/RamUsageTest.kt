package com.tempmonitor.memory

import com.tempmonitor.domain.model.RamUsage
import com.tempmonitor.ui.theme.formatBytes
import com.tempmonitor.ui.theme.formatPercent
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB

class RamUsageTest {

    @Test fun fracaoUsadaELivreDerivamDoTotal() {
        val ram = RamUsage(totalBytes = 8 * GIB, usedBytes = 6 * GIB)
        assertEquals(0.75f, ram.usedFraction, 0.0001f)
        assertEquals(2 * GIB, ram.freeBytes)
    }

    @Test fun totalZeroNaoDividePorZero() {
        val ram = RamUsage(totalBytes = 0L, usedBytes = 0L)
        assertEquals(0f, ram.usedFraction, 0.0001f)
        assertEquals(0L, ram.freeBytes)
    }

    @Test fun valoresForaDaFaixaSaoLimitados() {
        val ram = RamUsage(totalBytes = 4 * GIB, usedBytes = 8 * GIB)
        assertEquals(1f, ram.usedFraction, 0.0001f)
        assertEquals(0L, ram.freeBytes)
    }

    @Test fun rotulosDeMemoria() {
        assertEquals("12.0 GB", formatBytes(12 * GIB))
        assertEquals("512 MB", formatBytes(512 * MIB))
        assertEquals("0 MB", formatBytes(0L))
    }

    @Test fun rotuloDePercentual() {
        assertEquals("75%", formatPercent(0.7549f))
        assertEquals("100%", formatPercent(1.4f))
        assertEquals("0%", formatPercent(-0.2f))
    }
}
