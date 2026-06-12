package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_record")
data class WifiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "ssid")
    val ssid: String,

    @ColumnInfo(name = "bssid")
    val bssid: String? = null,

    @ColumnInfo(name = "capabilities")
    val capabilities: String? = null,

    @ColumnInfo(name = "frequency")
    val frequency: Int? = null,

    @ColumnInfo(name = "rssi")
    val rssi: Int? = null,

    @ColumnInfo(name = "is_connected")
    val isConnected: Boolean = false,

    @ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,

    @ColumnInfo(name = "link_speed")
    val linkSpeed: Int? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
