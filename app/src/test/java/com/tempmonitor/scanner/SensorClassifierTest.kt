package com.tempmonitor.scanner

import com.tempmonitor.data.scanner.SensorClassifier
import com.tempmonitor.domain.model.SensorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorClassifierTest {

    @Test fun cpuPorNomeDeNucleo() {
        assertEquals(SensorCategory.CPU, SensorClassifier.classify("cpu-0-0-usr"))
        assertEquals(SensorCategory.CPU, SensorClassifier.classify("cpuss-0"))
        assertEquals(SensorCategory.CPU, SensorClassifier.classify("kryo-gold-0"))
        assertEquals(SensorCategory.CPU, SensorClassifier.classify("oryon-prime"))
    }

    @Test fun gpuNaoConfundeComCpuss() {
        assertEquals(SensorCategory.GPU, SensorClassifier.classify("gpuss-0"))
        assertEquals(SensorCategory.GPU, SensorClassifier.classify("kgsl-3d0"))
        assertEquals(SensorCategory.GPU, SensorClassifier.classify("mali-gpu"))
    }

    @Test fun npuEDsp() {
        assertEquals(SensorCategory.NPU, SensorClassifier.classify("npu-0"))
        assertEquals(SensorCategory.NPU, SensorClassifier.classify("cdsp"))
        assertEquals(SensorCategory.NPU, SensorClassifier.classify("hexagon-dsp"))
    }

    @Test fun aiIsoladoCasaMasSubstringNao() {
        assertEquals(SensorCategory.NPU, SensorClassifier.classify("ai-engine"))
        assertEquals(SensorCategory.UNKNOWN, SensorClassifier.classify("available-0"))
    }

    @Test fun bateriaEDesconhecido() {
        assertEquals(SensorCategory.BATTERY, SensorClassifier.classify("battery"))
        assertEquals(SensorCategory.BATTERY, SensorClassifier.classify("bms-temp"))
        assertEquals(SensorCategory.BATTERY, SensorClassifier.classify("qualquer", fromPowerSupply = true))
        assertEquals(SensorCategory.UNKNOWN, SensorClassifier.classify("pm8550-bcl"))
    }

    @Test fun indiceDeNucleoECluster() {
        assertEquals(0, SensorClassifier.coreIndex("cpu-0-0-usr"))
        assertEquals(7, SensorClassifier.coreIndex("cpu7"))
        assertNull(SensorClassifier.coreIndex("gpuss-0"))
        assertEquals("Prime / Oryon", SensorClassifier.clusterHint("oryon-prime"))
        assertEquals("Eficiencia", SensorClassifier.fallbackCluster(0, 8))
        assertEquals("Prime / Oryon", SensorClassifier.fallbackCluster(7, 8))
    }

    @Test fun rotulosAmigaveisDeBateria() {
        assertEquals("Celula 1", SensorClassifier.friendlyLabel("battery/cell1_temp", SensorCategory.BATTERY))
        assertEquals("Celula 2", SensorClassifier.friendlyLabel("bms/cell2_temp", SensorCategory.BATTERY))
        assertEquals("Conector USB-C", SensorClassifier.friendlyLabel("battery/usb_temp", SensorCategory.BATTERY))
        assertEquals("Core 3", SensorClassifier.friendlyLabel("cpu-3", SensorCategory.CPU))
    }
}
