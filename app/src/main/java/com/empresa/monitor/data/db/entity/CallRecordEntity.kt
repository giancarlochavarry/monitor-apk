package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_record")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "call_record_id")
    val callRecordId: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "contact_name")
    val contactName: String? = null,

    @ColumnInfo(name = "call_type")
    val callType: Int, // 1=incoming, 2=outgoing, 3=missed, 4=voicemail, 5=rejected, 6=blocked

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,

    @ColumnInfo(name = "call_date")
    val callDate: Long,

    @ColumnInfo(name = "has_audio_recording")
    val hasAudioRecording: Boolean = false,

    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String? = null,

    @ColumnInfo(name = "file_size")
    val fileSize: Long? = null,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
