package com.empresa.monitor.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * TensorFlow Lite image analysis service.
 * Analyzes images for drugs, NSFW content, and blood/violence.
 * Since we can't bundle real TF models in a clone, this provides:
 * 1. The interface structure matching KidsGuard
 * 2. Model download from OSS
 * 3. Analysis result format matching the original
 */
class ImageAnalyzer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deviceId: String? = null
    private val modelDir: File

    private var drugModelLoaded = false
    private var nsfwModelLoaded = false
    private var bloodyModelLoaded = false

    // TF model URLs (same as KidsGuard)
    private val modelUrls = mapOf(
        "drugs" to "https://kidsguard-demo.oss-us-west-1.aliyuncs.com/TensorflowModel/drugs.tflite",
        "nsfw" to "https://kidsguard-demo.oss-us-west-1.aliyuncs.com/TensorflowModel/image_sex.tflite",
        "bloody" to "https://kidsguard-demo.oss-us-west-1.aliyuncs.com/TensorflowModel/nsfw_bloody.tflite"
    )

    companion object {
        val DETECTION_SCORE_THRESHOLD = 0.45f
    }

    init {
        modelDir = File(context.filesDir, "tensorflow_models")
        modelDir.mkdirs()
    }

    fun setDeviceId(id: String) { deviceId = id }

    /**
     * Download TensorFlow Lite models from KidsGuard's OSS.
     * Called once during initial setup.
     */
    suspend fun downloadModels(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                for ((name, url) in modelUrls) {
                    val modelFile = File(modelDir, "$name.tflite")
                    if (modelFile.exists() && modelFile.length() > 1000) {
                        // Already downloaded
                        when (name) { "drugs" -> drugModelLoaded = true; "nsfw" -> nsfwModelLoaded = true; "bloody" -> bloodyModelLoaded = true }
                        continue
                    }

                    val connection = java.net.URL(url).openConnection()
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val inputStream = connection.getInputStream()
                    val outputStream = modelFile.outputStream()

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    when (name) { "drugs" -> drugModelLoaded = true; "nsfw" -> nsfwModelLoaded = true; "bloody" -> bloodyModelLoaded = true }
                }
                true
            } catch (e: Exception) { false }
        }
    }

    /**
     * Analyze a single image file.
     * Returns JSON matching KidsGuard's result format.
     */
    suspend fun analyzeImage(imagePath: String): JSONObject {
        val result = JSONObject()
        val modelsChecked = JSONObject()

        if (!drugModelLoaded && !nsfwModelLoaded && !bloodyModelLoaded) {
            result.put("has_analysis", false)
            result.put("reason", "models_not_loaded")
            return result
        }

        try {
            val bitmap = loadBitmap(imagePath)
            if (bitmap == null) {
                result.put("has_analysis", false)
                result.put("reason", "cannot_decode_image")
                return result
            }

            // Analyze with each loaded model
            if (drugModelLoaded) {
                val drugScore = runAnalysis(bitmap, "drugs")
                val drugDetected = drugScore > DETECTION_SCORE_THRESHOLD
                modelsChecked.put("drugs", JSONObject().apply {
                    put("score", drugScore)
                    put("detected", drugDetected)
                    put("label", if (drugDetected) "Possível droga detectada" else "Limpio")
                })
            }

            if (nsfwModelLoaded) {
                val nsfwScore = runAnalysis(bitmap, "nsfw")
                val nsfwDetected = nsfwScore > DETECTION_SCORE_THRESHOLD
                modelsChecked.put("nsfw", JSONObject().apply {
                    put("score", nsfwScore)
                    put("detected", nsfwDetected)
                    put("label", if (nsfwDetected) "Contenido sexual detectado" else "Limpio")
                })
            }

            if (bloodyModelLoaded) {
                val bloodyScore = runAnalysis(bitmap, "bloody")
                val bloodyDetected = bloodyScore > DETECTION_SCORE_THRESHOLD
                modelsChecked.put("bloody", JSONObject().apply {
                    put("score", bloodyScore)
                    put("detected", bloodyDetected)
                    put("label", if (bloodyDetected) "Violencia/sangre detectada" else "Limpio")
                })
            }

            result.put("has_analysis", true)
            result.put("models", modelsChecked)
            result.put("image_path", imagePath)

        } catch (e: Exception) {
            result.put("has_analysis", false)
            result.put("error", e.message)
        }

        return result
    }

    /**
     * Run TF Lite analysis on bitmap.
     * Returns confidence score (0.0 - 1.0).
     * NOTE: Actual TF Lite interpreter needs the TensorFlow Lite library.
     * This method provides the structure; real inference requires
     * org.tensorflow:tensorflow-lite dependency.
     */
    private fun runAnalysis(bitmap: Bitmap, modelName: String): Float {
        // Placeholder for actual TF Lite inference.
        // Real implementation would:
        // 1. Load Interpreter with model file
        // 2. Preprocess bitmap to model input size (e.g. 224x224)
        // 3. Run inference
        // 4. Post-process output
        // 5. Return confidence score
        //
        // For now, returns mock score based on average brightness
        // (just to demonstrate the pipeline structure)
        return mockAnalysisScore(bitmap)
    }

    private fun mockAnalysisScore(bitmap: Bitmap): Float {
        var totalBrightness = 0f
        val pixels = IntArray(100)
        bitmap.getPixels(pixels, 0, 10, 0, 0, 10, 10)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalBrightness += (r + g + b) / 3f
        }

        return (totalBrightness / (255f * pixels.size.coerceAtLeast(1)))
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, Uri.fromFile(file))
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * Batch scan recent photos for policy violations.
     * Mirrors KidsGuard's auto-scan feature.
     */
    suspend fun scanRecentPhotos(limit: Int = 20): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(0) ?: continue
                    val analysis = analyzeImage(path)

                    if (analysis.optBoolean("has_analysis")) {
                        val models = analysis.optJSONObject("models")
                        if (models != null) {
                            for (model in models.keys()) {
                                val modelResult = models.optJSONObject(model)
                                if (modelResult?.optBoolean("detected") == true) {
                                    results.add(analysis)
                                    break
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return results
    }

    fun isModelReady(): Boolean = drugModelLoaded || nsfwModelLoaded || bloodyModelLoaded

    fun destroy() { scope.cancel() }
}
