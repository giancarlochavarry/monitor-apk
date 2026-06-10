package com.empresa.monitor.service.monitoring

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    reportBattery()
                } catch (_: Exception) {}
                delay(300_000) // every 5 minutes
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun reportBattery() {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val status = context.registerReceiver(null, filter)?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        repository.sendBattery(level, isCharging)
    }
}
