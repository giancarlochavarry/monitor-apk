package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_log")
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_id")
    val eventId: Long,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "event_location")
    val eventLocation: String? = null,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long,

    @ColumnInfo(name = "all_day")
    val allDay: Boolean = false,

    @ColumnInfo(name = "duration")
    val duration: String? = null,

    @ColumnInfo(name = "rrule")
    val rrule: String? = null,

    @ColumnInfo(name = "organizer")
    val organizer: String? = null,

    @ColumnInfo(name = "status")
    val status: Int? = null, // 0=tentative, 1=confirmed, 2=cancelled

    @ColumnInfo(name = "calendar_name")
    val calendarName: String? = null,

    @ColumnInfo(name = "account_name")
    val accountName: String? = null,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
