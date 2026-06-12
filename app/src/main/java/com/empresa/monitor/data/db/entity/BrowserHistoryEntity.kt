package com.empresa.monitor.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "browser_package")
    val browserPackage: String? = null, // e.g. com.android.chrome

    @ColumnInfo(name = "browser_name")
    val browserName: String? = null, // e.g. Chrome, Samsung Internet

    @ColumnInfo(name = "visit_count")
    val visitCount: Int? = null,

    @ColumnInfo(name = "last_visited")
    val lastVisited: Long? = null,

    @ColumnInfo(name = "bookmark")
    val bookmark: Boolean = false,

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false
)
