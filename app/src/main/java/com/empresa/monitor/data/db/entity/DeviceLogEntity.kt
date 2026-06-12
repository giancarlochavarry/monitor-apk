package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_log")
data class DeviceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "log_type")
    val logType: String, // location, camera, screenshot, audio, etc.

    @ColumnInfo(name = "data_json")
    val dataJson: String, // JSON with the actual data

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
