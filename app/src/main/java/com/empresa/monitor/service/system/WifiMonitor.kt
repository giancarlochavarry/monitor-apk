package com.empresa.monitor.service.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.WifiEntity
import kotlinx.coroutines.*

class WifiMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val wifiDao = db.wifiDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var receiver: BroadcastReceiver? = null

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            captureCurrentWifi()
        }

        registerWifiReceiver()
    }

    fun stopMonitoring() {
        isRunning = false
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        scope.cancel()
    }

    private fun registerWifiReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ConnectivityManager.CONNECTIVITY_ACTION,
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        scope.launch { captureCurrentWifi() }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    private suspend fun captureCurrentWifi() {
        withContext(Dispatchers.IO) {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return@withContext

                // Get current connection info
                val wifiInfo = wifiManager.connectionInfo ?: return@withContext
                val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return@withContext

                val bssid = wifiInfo.bssid
                val rssi = wifiInfo.rssi
                val frequency = wifiInfo.frequency
                val linkSpeed = wifiInfo.linkSpeed

                // Get IP
                val ipInt = wifiInfo.ipAddress
                val ipAddress = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xFF,
                    (ipInt shr 8) and 0xFF,
                    (ipInt shr 16) and 0xFF,
                    (ipInt shr 24) and 0xFF
                )

                // Get capabilities
                val configuredNetworks = wifiManager.configuredNetworks
                val capabilities = configuredNetworks
                    .firstOrNull { it.SSID == "\"$ssid\"" || it.SSID == ssid }
                    ?.allowedKeyManagement?.toString()

                val wifiEntity = WifiEntity(
                    ssid = ssid,
                    bssid = bssid,
                    capabilities = capabilities,
                    frequency = frequency,
                    rssi = rssi,
                    isConnected = true,
                    ipAddress = ipAddress,
                    linkSpeed = linkSpeed,
                    recordedAt = System.currentTimeMillis()
                )

                wifiDao.insert(wifiEntity)

                // Also scan nearby networks
                val scanResults = wifiManager.scanResults
                val nearbyWifis = scanResults
                    .filter { it.SSID.isNotEmpty() && it.SSID != ssid }
                    .distinctBy { it.BSSID }
                    .take(20)

                for (scan in nearbyWifis) {
                    val nearbyEntity = WifiEntity(
                        ssid = scan.SSID,
                        bssid = scan.BSSID,
                        capabilities = scan.capabilities,
                        frequency = scan.frequency,
                        rssi = scan.level,
                        isConnected = false,
                        recordedAt = System.currentTimeMillis()
                    )
                    wifiDao.insert(nearbyEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        stopMonitoring()
    }
}
