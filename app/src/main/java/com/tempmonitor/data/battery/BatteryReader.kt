package com.tempmonitor.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Fallback oficial sem root: temperatura do pack via BatteryManager.
 * E a unica fonte nao-sysfs do app, conforme a especificacao.
 */
class BatteryReader(private val context: Context) {

    /** Temperatura em Celsius, ou null se o dispositivo nao expoe o extra. */
    fun packTemperatureCelsius(): Float? {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: return null
        if (tenths == Int.MIN_VALUE) return null
        return tenths / 10f
    }

    fun chargeStatus(): String? {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
            BatteryManager.BATTERY_STATUS_FULL -> "Cheia"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            else -> null
        }
    }
}
