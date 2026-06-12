package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact",
    indices = [Index(value = ["phone"], unique = true)]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "phone")
    val phone: String,

    @ColumnInfo(name = "phone_type")
    val phoneType: Int? = null, // 1=home, 2=mobile, 3=work, etc.

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,

    @ColumnInfo(name = "photo_base64")
    val photoBase64: String? = null,

    @ColumnInfo(name = "times_contacted")
    val timesContacted: Int = 0,

    @ColumnInfo(name = "last_time_contacted")
    val lastTimeContacted: Long? = null,

    @ColumnInfo(name = "starred")
    val starred: Boolean = false,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
