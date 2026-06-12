package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driving_record")
data class DrivingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "driving_id")
    val drivingId: String? = null,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "start_latitude")
    val startLatitude: Double? = null,

    @ColumnInfo(name = "start_longitude")
    val startLongitude: Double? = null,

    @ColumnInfo(name = "end_latitude")
    val endLatitude: Double? = null,

    @ColumnInfo(name = "end_longitude")
    val endLongitude: Double? = null,

    @ColumnInfo(name = "max_speed")
    val maxSpeed: Float? = null,

    @ColumnInfo(name = "avg_speed")
    val avgSpeed: Float? = null,

    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Float? = null,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)

@Entity(tableName = "geofence")
data class GeofenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "geofence_id")
    val geofenceId: String,

    @ColumnInfo(name = "name")
    val name: String? = null,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Float,

    @ColumnInfo(name = "transition_type")
    val transitionType: Int, // 1=enter, 2=exit, 4=dwell

    @ColumnInfo(name = "active")
    val active: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "geofence_report")
data class GeofenceReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "geofence_id")
    val geofenceId: String,

    @ColumnInfo(name = "geofence_name")
    val geofenceName: String? = null,

    @ColumnInfo(name = "transition_type")
    val transitionType: Int, // 1=enter, 2=exit, 4=dwell

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float? = null,

    @ColumnInfo(name = "triggered_at")
    val triggeredAt: Long,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
