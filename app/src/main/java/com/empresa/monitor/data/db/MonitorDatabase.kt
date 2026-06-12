package com.empresa.monitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.empresa.monitor.data.db.dao.*
import com.empresa.monitor.data.db.entity.*

@Database(
    entities = [
        SmsEntity::class,
        ContactEntity::class,
        CalendarEntity::class,
        WifiEntity::class,
        BrowserHistoryEntity::class,
        CallRecordEntity::class,
        GmailEntity::class,
        OutlookEntity::class,
        BlockBrowserEntity::class,
        BlockPhoneEntity::class,
        BlockWifiEntity::class,
        DrivingEntity::class,
        GeofenceEntity::class,
        GeofenceReportEntity::class,
        DeviceLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun contactDao(): ContactDao
    abstract fun calendarDao(): CalendarDao
    abstract fun wifiDao(): WifiDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun gmailDao(): GmailDao
    abstract fun outlookDao(): OutlookDao
    abstract fun blockBrowserDao(): BlockBrowserDao
    abstract fun blockPhoneDao(): BlockPhoneDao
    abstract fun blockWifiDao(): BlockWifiDao
    abstract fun deviceLogDao(): DeviceLogDao

    companion object {
        private const val DB_NAME = "monitor_db"

        @Volatile
        private var INSTANCE: MonitorDatabase? = null

        fun getInstance(context: Context): MonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonitorDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
