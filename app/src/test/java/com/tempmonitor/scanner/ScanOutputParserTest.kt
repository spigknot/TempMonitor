package com.tempmonitor.scanner

import com.tempmonitor.data.scanner.ScanOutputParser
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.SensorKind
import com.tempmonitor.domain.model.TempScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOutputParserTest {

    private val lines = listOf(
        "TZ|/sys/class/thermal/thermal_zone0|cpu-0-0-usr",
        "TZ|/sys/class/thermal/thermal_zone1|cpu-1-0-usr",
        "TZ|/sys/class/thermal/thermal_zone42|gpuss-0",
        "TZ|/sys/class/thermal/thermal_zone77|cdsp",
        "PSF|/sys/class/power_supply/battery/temp|battery",
        "PSF|/sys/class/power_supply/bms/cell1_temp|bms",
        "PSF|/sys/class/power_supply/battery/usb_temp|battery",
        "CLK|/sys/class/kgsl/kgsl-3d0/gpuclk",
        "linha-invalida",
    )

    @Test fun descobreECategoriza() {
        val nodes = ScanOutputParser.parse(lines)
        assertEquals(8, nodes.size)
        assertEquals(2, nodes.count { it.category == SensorCategory.CPU })
        assertEquals(1, nodes.count { it.category == SensorCategory.GPU && it.kind == SensorKind.TEMP })
        assertEquals(1, nodes.count { it.category == SensorCategory.NPU })
        assertEquals(3, nodes.count { it.category == SensorCategory.BATTERY })
        assertEquals(1, nodes.count { it.kind == SensorKind.FREQ })
    }

    @Test fun escalasCorretasPorOrigem() {
        val nodes = ScanOutputParser.parse(lines)
        assertTrue(nodes.filter { it.id.startsWith("thermal_zone") }.all { it.scale == TempScale.MILLIDEGREE })
        val pack = nodes.first { it.tempPath.endsWith("battery/temp") }
        assertEquals(TempScale.DECIDEGREE, pack.scale)
    }

    @Test fun rotulosEAgrupamentoDeCpu() {
        val nodes = ScanOutputParser.parse(lines)
        val core0 = nodes.first { it.type == "cpu-0-0-usr" }
        assertEquals("Core 0", core0.label)
        val cell1 = nodes.first { it.tempPath.contains("cell1_temp") }
        assertEquals("Celula 1", cell1.label)
        val usb = nodes.first { it.tempPath.contains("usb_temp") }
        assertEquals("Conector USB-C", usb.label)
    }

    @Test fun overrideManualVenceHeuristica() {
        val nodes = ScanOutputParser.parse(
            lines,
            overrides = mapOf("thermal_zone77" to SensorCategory.CPU),
        )
        val forced = nodes.first { it.id == "thermal_zone77" }
        assertEquals(SensorCategory.CPU, forced.category)
        assertTrue(forced.manuallyAssigned)
    }
}
