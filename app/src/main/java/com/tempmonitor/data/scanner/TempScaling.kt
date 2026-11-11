package com.tempmonitor.data.scanner

import com.tempmonitor.domain.model.TempScale

/**
 * Conversao de valores brutos de sysfs para graus Celsius.
 * Zonas termicas do kernel expoem miligraus; power_supply expoe decimos de grau.
 * AUTO usa magnitude para desempatar quando a origem e desconhecida.
 * Funcao pura (sem Android) para permitir teste unitario em JVM.
 */
object TempScaling {
    fun toCelsius(raw: Long, scale: TempScale): Float = when (scale) {
        TempScale.MILLIDEGREE -> raw / 1000f
        TempScale.DECIDEGREE -> raw / 10f
        TempScale.CELSIUS -> raw.toFloat()
        TempScale.AUTO -> auto(raw)
    }

    private fun auto(raw: Long): Float {
        val abs = kotlin.math.abs(raw)
        return when {
            abs >= 10_000L -> raw / 1000f   // 45000 -> 45.0 C (miligraus)
            abs >= 1_000L -> raw / 1000f    // 4500  -> 4.5? improvavel; zonas reais sao miligraus
            abs >= 200L -> raw / 10f        // 450   -> 45.0 C (decimos, power_supply)
            else -> raw.toFloat()           // ja em Celsius
        }
    }

    /** Valores fora desta faixa sao tratados como leitura invalida/ausente. */
    fun isPlausible(celsius: Float): Boolean = celsius in -30f..150f
}
