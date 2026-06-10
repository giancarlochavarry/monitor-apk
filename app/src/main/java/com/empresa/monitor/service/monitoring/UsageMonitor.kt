package com.empresa.monitor.service.monitoring

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.empresa.monitor.data.model.AppUsageRequest
import com.empresa.monitor.data.repository.MonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val end = System.currentTimeMillis()
                    val start = end - 3600_000 // última hora

                    val stats = usm.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, start, end
                    )

                    val pm = ctx.packageManager

                    stats?.forEach { usage ->
                        val totalTime = usage.totalTimeInForeground / 1000
                        if (totalTime > 0) {
                            val appName = try {
                                val appInfo = pm.getApplicationInfo(usage.packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                usage.packageName
                            }

                            repository.sendUsage(
                                AppUsageRequest(
                                    packageName = usage.packageName,
                                    appName = appName,
                                    durationSeconds = totalTime,
                                    recordedAt = Instant.now().toString()
                                )
                            )
                        }
                    }
                    delay(300_000) // cada 5 minutos
                } catch (e: Exception) {
                    delay(60_000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
