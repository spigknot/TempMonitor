package com.tempmonitor.history

import com.tempmonitor.data.history.ThermalHistoryStore
import com.tempmonitor.domain.model.HistoryPoint
import com.tempmonitor.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalHistoryStoreTest {

    @Test fun sparklineMantemUltimos30() {
        val store = ThermalHistoryStore()
        repeat(60) { i -> store.add("cpu", i * 1_000L, 40f + i) }
        val spark = store.sparkline("cpu")
        assertEquals(30, spark.size)
        assertEquals(99f, spark.last(), 0.001f)
    }

    @Test fun janelaCurtaRetornaAmostrasPuras() {
        val store = ThermalHistoryStore()
        val now = 100_000L
        for (i in 0..59) store.add("cpu", now - 59_000 + i * 1_000L, 50f)
        val points = store.query("cpu", TimeRange.S10, now)
        assertTrue(points.size in 10..11)
        assertTrue(points.all { it.avgCelsius == 50f })
    }

    @Test fun downsampleLimitaPontosEPreservaPico() {
        val points = (0 until 10_000).map { i ->
            HistoryPoint(i * 1_000L, 40f, if (i == 5_555) 99f else 41f)
        }
        val out = ThermalHistoryStore.downsample(points, 200)
        assertTrue(out.size <= 200)
        assertEquals(99f, out.maxOf { it.maxCelsius }, 0.001f)
    }

    @Test fun janelaLongaUsaBucketsComPico() {
        val store = ThermalHistoryStore()
        for (m in 0 until 180) {
            store.add("gpu", m * 60_000L, if (m == 100) 88f else 55f)
        }
        val now = 180 * 60_000L
        val points = store.query("gpu", TimeRange.H6, now)
        assertTrue(points.isNotEmpty())
        assertTrue(points.size <= 200)
        assertEquals(88f, points.maxOf { it.maxCelsius }, 0.001f)
    }

    @Test fun mergeCombinaSensoresDaSecao() {
        val store = ThermalHistoryStore()
        val a = listOf(HistoryPoint(0, 40f, 45f), HistoryPoint(60_000, 42f, 47f))
        val b = listOf(HistoryPoint(0, 50f, 60f), HistoryPoint(60_000, 52f, 62f))
        val merged = store.merge(listOf(a, b), TimeRange.M5)
        assertEquals(2, merged.size)
        assertEquals(45f, merged[0].avgCelsius, 0.001f)
        assertEquals(60f, merged[0].maxCelsius, 0.001f)
    }
}
