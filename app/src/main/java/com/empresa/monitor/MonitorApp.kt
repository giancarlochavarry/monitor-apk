package com.empresa.monitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MonitorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Canal con importancia MINIMAL – casi invisible
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR, "Sistema",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Servicio corporativo"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(monitorChannel)

            // Canal para "alertas" internas (también silencioso)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS, "Alertas internas",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_MONITOR = "channel_monitor_corp"
        const val CHANNEL_ALERTS = "channel_alerts_corp"
        const val NOTIFICATION_ID = 1001
    }
}
