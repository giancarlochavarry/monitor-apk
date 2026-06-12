package com.empresa.monitor.service.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import com.empresa.monitor.data.db.MonitorDatabase
import kotlinx.coroutines.*

/**
 * Blocks access to configured WiFi networks.
 * Listens for WiFi state changes and disconnects from blocked networks.
 */
class BlockWifiReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {

                scope.launch {
                    checkAndBlockWifi(context)
                }
            }
        }
    }

    private suspend fun checkAndBlockWifi(context: Context) {
        try {
            val db = MonitorDatabase.getInstance(context)
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            val connectionInfo = wifiManager.connectionInfo ?: return
            val currentSsid = connectionInfo.ssid?.removeSurrounding("\"") ?: return
            val currentBssid = connectionInfo.bssid ?: ""

            // Check if current network is blocked
            if (currentBssid.isNotEmpty()) {
                val blocked = db.blockWifiDao().isBlocked(currentBssid)
                if (blocked != null) {
                    // Disconnect from this network
                    wifiManager.disconnect()
                }
            }

            // Also check SSID
            val allBlocked = db.blockWifiDao().getAll()
            if (allBlocked.any { it.name == currentSsid }) {
                wifiManager.disconnect()
            }
        } catch (e: Exception) { /* silent */ }
    }
}
