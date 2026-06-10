package com.empresa.monitor.service.screenshot

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.empresa.monitor.service.monitoring.ScreenshotMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Service that captures screen using MediaProjection API.
 * Requires: user to accept the screen recording dialog once on app start.
 */
@AndroidEntryPoint
class ScreenshotService : Service() {

    @Inject lateinit var screenshotMonitor: ScreenshotMonitor

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var projectionManager: MediaProjectionManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val handlerThread = HandlerThread("ScreenshotThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        mediaProjection = projectionManager?.getMediaProjection(resultCode, data)
        setupImageReader()
        return START_STICKY
    }

    private fun setupImageReader() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            android.graphics.PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()
                bitmap?.let {
                    scope.launch {
                        screenshotMonitor.processBitmap(it, null, null)
                    }
                }
            }
        }, handler)

        mediaProjection?.createVirtualDisplay(
            "MonitorScreenshot",
            metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, handler
        )
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        imageReader?.close()
        handler?.looper?.quit()
        scope.cancel()
        super.onDestroy()
    }
}
