package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gmail_capture")
data class GmailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sender")
    val sender: String?,

    @ColumnInfo(name = "sender_email")
    val senderEmail: String?,

    @ColumnInfo(name = "recipient")
    val recipient: String?,

    @ColumnInfo(name = "subject")
    val subject: String?,

    @ColumnInfo(name = "body")
    val body: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "labels")
    val labels: String? = null,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)

@Entity(tableName = "outlook_capture")
data class OutlookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sender")
    val sender: String?,

    @ColumnInfo(name = "sender_email")
    val senderEmail: String?,

    @ColumnInfo(name = "recipient")
    val recipient: String?,

    @ColumnInfo(name = "subject")
    val subject: String?,

    @ColumnInfo(name = "body")
    val body: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
