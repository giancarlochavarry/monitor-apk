package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_browser")
data class BlockBrowserEntity(
    @PrimaryKey
    @ColumnInfo(name = "host")
    val host: String,

    @ColumnInfo(name = "id")
    val id: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "block_phone")
data class BlockPhoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "phone_num")
    val phoneNum: String,

    @ColumnInfo(name = "id")
    val id: String = "",

    @ColumnInfo(name = "name")
    val name: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "block_wifi")
data class BlockWifiEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "bssid")
    val bssid: String? = null
)
