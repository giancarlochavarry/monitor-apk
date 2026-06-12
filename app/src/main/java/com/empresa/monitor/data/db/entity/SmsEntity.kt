package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms",
    indices = [Index(value = ["sms_id"], unique = true)]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sms_id")
    val smsId: Long, // ID from system ContentProvider

    @ColumnInfo(name = "address")
    val address: String, // phone number

    @ColumnInfo(name = "contact_name")
    val contactName: String? = null,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "type")
    val type: Int, // 1=inbox, 2=sent, 3=draft, 4=outbox, 5=failed, 6=queued

    @ColumnInfo(name = "date")
    val date: Long, // timestamp

    @ColumnInfo(name = "date_sent")
    val dateSent: Long? = null,

    @ColumnInfo(name = "is_mms")
    val isMms: Boolean = false,

    @ColumnInfo(name = "mms_subject")
    val mmsSubject: String? = null,

    @ColumnInfo(name = "read")
    val read: Boolean = true,

    @ColumnInfo(name = "status")
    val status: Int = -1,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
