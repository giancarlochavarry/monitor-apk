package com.empresa.monitor.service.monitoring

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.empresa.monitor.data.model.ScreenshotRequest
import com.empresa.monitor.data.repository.MonitorRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MonitorRepository
) {
    private var job: Job? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun start(ctx: Context) {
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                processPendingScreenshots()
                delay(10_000)
            }
        }
    }

    suspend fun processBitmap(bitmap: Bitmap, appPackage: String?, appName: String?) {
        try {
            val imageFile = saveBitmap(bitmap)
            val ocrText = extractText(bitmap)

            if (imageFile == null) {
                repository.sendScreenshot(
                    ScreenshotRequest(
                        imageUrl = "local://pending/${UUID.randomUUID()}",
                        appPackage = appPackage,
                        appName = appName,
                        ocrText = ocrText,
                        capturedAt = Instant.now().toString()
                    )
                )
            } else {
                repository.sendScreenshot(
                    ScreenshotRequest(
                        imageUrl = imageFile.absolutePath,
                        appPackage = appPackage,
                        appName = appName,
                        ocrText = ocrText,
                        capturedAt = Instant.now().toString()
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun extractText(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) { } }
                    .addOnFailureListener { cont.resume(null) {} }
            }
            result?.text?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap): File? {
        return try {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "monitor_screenshots"
            )
            dir.mkdirs()
            val file = File(dir, "ss_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos)
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun processPendingScreenshots() {}

    fun stop() {
        job?.cancel()
        job = null
        recognizer.close()
    }
}
