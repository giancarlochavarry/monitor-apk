package com.empresa.monitor.service.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/**
 * Records ambient audio and returns the audio file.
 * Requires RECORD_AUDIO permission (already in manifest).
 */
class AudioRecorder(private val context: Context) {

    /**
     * Record ambient audio for the specified duration (seconds).
     * Returns the recorded File, or null on failure.
     */
    fun record(seconds: Int = 10): File? {
        // Check permission
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val outputFile = createOutputFile() ?: return null
        var recorder: MediaRecorder? = null

        return try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            // Record for the specified duration
            Thread.sleep(seconds * 1000L)

            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            if (outputFile.exists() && outputFile.length() > 0) {
                outputFile
            } else {
                outputFile.delete()
                null
            }
        } catch (e: Exception) {
            // Cleanup on failure
            try { recorder?.release() } catch (_: Exception) {}
            outputFile.delete()
            null
        }
    }

    private fun createOutputFile(): File? {
        return try {
            val dir = context.cacheDir
            dir.mkdirs()
            File(dir, "ambient_${UUID.randomUUID()}.mp4")
        } catch (e: Exception) {
            null
        }
    }
}
