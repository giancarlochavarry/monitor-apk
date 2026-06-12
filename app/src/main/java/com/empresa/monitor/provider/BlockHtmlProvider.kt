package com.empresa.monitor.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Provides a local HTML page that blocks access to configured websites.
 * Used in combination with a local proxy or VPN to display block pages.
 *
 * KidsGuard uses this pattern: content://com.empresa.monitor.blocker/web_block.html
 */
class BlockHtmlProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("Read only")

        val blockHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Acceso Restringido</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, sans-serif;
                        background: #f5f5f5;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .card {
                        background: white;
                        border-radius: 16px;
                        padding: 40px 32px;
                        margin: 20px;
                        max-width: 400px;
                        text-align: center;
                        box-shadow: 0 2px 20px rgba(0,0,0,0.1);
                    }
                    .icon {
                        font-size: 64px;
                        margin-bottom: 20px;
                    }
                    h1 {
                        font-size: 22px;
                        color: #333;
                        margin-bottom: 12px;
                    }
                    p {
                        font-size: 15px;
                        color: #666;
                        line-height: 1.5;
                        margin-bottom: 8px;
                    }
                    .url {
                        background: #f0f0f0;
                        padding: 10px;
                        border-radius: 8px;
                        font-family: monospace;
                        font-size: 13px;
                        color: #888;
                        margin: 16px 0;
                        word-break: break-all;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">🚫</div>
                    <h1>Acceso Restringido</h1>
                    <p>Esta página ha sido bloqueada por la política de uso corporativo.</p>
                    <div class="url" id="blockedUrl">Cargando...</div>
                    <p style="font-size: 13px; color: #999;">Contacte al administrador del sistema para más información.</p>
                </div>
                <script>
                    try {
                        var url = window.location.href;
                        if (url && url.length > 30) {
                            document.getElementById('blockedUrl').textContent = url;
                        }
                    } catch(e) {}
                </script>
            </body>
            </html>
        """.trimIndent()

        try {
            val file = File(context?.cacheDir, "web_block.html")
            file.writeText(blockHtml)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            throw FileNotFoundException("Could not create block page")
        }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?,
                        selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String = "text/html"
}
