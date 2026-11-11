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
import com.tempmonitor.TempMonitorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coleta em segundo plano (tela desligada) para historico de longa duracao.
 * Desacoplado da UI: observa o repositório singleton e mantem uma
 * notificacao fixa de baixo impacto, atualizada no maximo a cada 5 s.
 */
class ThermalMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastNotifyMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("TempMonitor ativo", "Coletando temperaturas..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as TempMonitorApplication).container.thermalRepository
        scope.launch {
            repo.startMonitoring()
            repo.state.collectLatest { state ->
                val now = System.currentTimeMillis()
                if (now - lastNotifyMs >= 5_000L) {
                    lastNotifyMs = now
                    val text = state.hotspotCelsius?.let { hotspot ->
                        "Hotspot " + formatTemp(hotspot) +
                            (state.hotspotLabel?.let { label -> " - $label" } ?: "")
                    } ?: "Coletando temperaturas..."
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification("TempMonitor ativo", text))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setOngoing(true)
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

    private fun formatTemp(v: Float): String = String.format(java.util.Locale.US, "%.1f C", v)

    companion object {
        private const val CHANNEL_ID = "thermal_monitor"
        private const val NOTIFICATION_ID = 42

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
