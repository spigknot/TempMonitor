package com.tempmonitor.scanner

import com.tempmonitor.data.scanner.TempScaling
import com.tempmonitor.domain.model.TempScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempScalingTest {
    @Test fun miligraus() = assertEquals(45.0f, TempScaling.toCelsius(45_000, TempScale.MILLIDEGREE), 0.001f)
    @Test fun decimos() = assertEquals(45.0f, TempScaling.toCelsius(450, TempScale.DECIDEGREE), 0.001f)
    @Test fun celsiusDireto() = assertEquals(45.0f, TempScaling.toCelsius(45, TempScale.CELSIUS), 0.001f)

    @Test fun autoDetectaEscala() {
        assertEquals(45.0f, TempScaling.toCelsius(45_000, TempScale.AUTO), 0.001f)
        assertEquals(45.0f, TempScaling.toCelsius(450, TempScale.AUTO), 0.001f)
        assertEquals(45.0f, TempScaling.toCelsius(45, TempScale.AUTO), 0.001f)
        assertEquals(-5.0f, TempScaling.toCelsius(-5_000, TempScale.AUTO), 0.001f)
    }

    @Test fun plausibilidade() {
        assertTrue(TempScaling.isPlausible(45f))
        assertFalse(TempScaling.isPlausible(450f))
        assertFalse(TempScaling.isPlausible(-100f))
    }
}
