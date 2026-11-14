package com.tempmonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.tempmonitor.MainActivity
import com.tempmonitor.R
import com.tempmonitor.TempMonitorApplication
import com.tempmonitor.domain.model.SensorCategory
import com.tempmonitor.domain.model.ThermalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Coleta em segundo plano (tela desligada) para historico de longa duracao.
 *
 * - Notificacao fixa com os picos resumidos: CPU / GPU / NPU / BAT.
 * - Acao "Parar" encerra o servico e a coleta em segundo plano.
 * - O repositorio usa contagem de assinantes: parar o servico NAO congela a
 *   tela do app se ela estiver aberta, e vice-versa.
 */
class ThermalMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotifyMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        _running.value = true
        startForeground(NOTIFICATION_ID, buildNotification("Coletando temperaturas..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handleStop()
            return START_NOT_STICKY
        }

        val repo = (application as TempMonitorApplication).container.thermalRepository
        scope.launch {
            repo.startMonitoring()
            repo.state.collectLatest { state ->
                val now = System.currentTimeMillis()
                if (now - lastNotifyMs >= NOTIFY_INTERVAL_MS) {
                    lastNotifyMs = now
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(summaryText(state)))
                }
            }
        }
        return START_STICKY
    }

    /** Acao do botao "Parar" da notificacao. */
    private fun handleStop() {
        val repo = (application as TempMonitorApplication).container.thermalRepository
        scope.launch { repo.stopMonitoring() }
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    /** "CPU: 51  GPU: 49  NPU: 48  BAT: 40" (graus inteiros, como pedido). */
    private fun summaryText(state: ThermalState): String {
        fun peak(category: SensorCategory): String? =
            state.section(category)?.peakCelsius?.let { it.roundToInt().toString() + "\u00B0" }

        val parts = listOfNotNull(
            peak(SensorCategory.CPU)?.let { "CPU: " + it },
            peak(SensorCategory.GPU)?.let { "GPU: " + it },
            peak(SensorCategory.NPU)?.let { "NPU: " + it },
            peak(SensorCategory.BATTERY)?.let { "BAT: " + it },
        )
        return if (parts.isEmpty()) "Coletando temperaturas..." else parts.joinToString("  ")
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopService = PendingIntent.getService(
            this, 1,
            Intent(this, ThermalMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("TempMonitor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_temp)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notif_stop, "Parar", stopService)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Monitoramento termico", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Coleta termica em segundo plano" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "thermal_monitor"
        private const val NOTIFICATION_ID = 42
        private const val NOTIFY_INTERVAL_MS = 5_000L
        const val ACTION_STOP = "com.tempmonitor.action.STOP_SERVICE"

        private val _running = MutableStateFlow(false)
        /** Estado observavel pelo app para manter o switch sincronizado. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ThermalMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ThermalMonitorService::class.java))
        }
    }
}
