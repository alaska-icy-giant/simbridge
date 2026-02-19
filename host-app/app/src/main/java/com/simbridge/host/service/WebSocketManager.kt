package com.simbridge.host.service

import android.util.Log
import com.google.gson.Gson
import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.Prefs
import com.simbridge.host.data.WsMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketManager(
    private val prefs: Prefs,
    private val onMessage: (WsMessage) -> Unit,
    private val onStatusChange: (ConnectionStatus) -> Unit,
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val PING_INTERVAL_SEC = 30L
        private const val MAX_BACKOFF_SEC = 30L
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WS
        .build()

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val retryCount = AtomicInteger(0)

    private var webSocket: WebSocket? = null
    private var reconnectFuture: ScheduledFuture<*>? = null
    @Volatile private var intentionalClose = false

    fun connect() {
        intentionalClose = false
        doConnect()
    }

    fun disconnect() {
        intentionalClose = true
        synchronized(this) {
            reconnectFuture?.cancel(false)
            reconnectFuture = null
        }
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        onStatusChange(ConnectionStatus.DISCONNECTED)
    }

    fun send(message: WsMessage) {
        val json = gson.toJson(message)
        Log.d(TAG, "TX: $json")
        webSocket?.send(json)
    }

    private fun doConnect() {
        onStatusChange(ConnectionStatus.CONNECTING)
        val serverUrl = prefs.serverUrl
        val token = prefs.token
        val deviceId = prefs.deviceId
        if (serverUrl.isBlank() || token.isBlank() || deviceId < 0) {
            Log.w(TAG, "No server URL, token, or device ID configured")
            onStatusChange(ConnectionStatus.DISCONNECTED)
            return
        }

        // Convert http(s):// to ws(s):// and use the host device endpoint
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/host/$deviceId?token=$token"

        Log.i(TAG, "Connecting to $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, WsListener())
    }

    private fun scheduleReconnect() {
        if (intentionalClose) return
        val attempt = retryCount.getAndIncrement()
        val delaySec = minOf(1L shl attempt, MAX_BACKOFF_SEC)
        Log.i(TAG, "Reconnecting in ${delaySec}s (attempt ${attempt + 1})")
        onStatusChange(ConnectionStatus.CONNECTING)
        synchronized(this) {
            reconnectFuture = scheduler.schedule({ doConnect() }, delaySec, TimeUnit.SECONDS)
        }
    }

    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Connected")
            retryCount.set(0)
            onStatusChange(ConnectionStatus.CONNECTED)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "RX: $text")
            try {
                val msg = gson.fromJson(text, WsMessage::class.java)
                onMessage(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $text", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Server closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Closed: $code $reason")
            this@WebSocketManager.webSocket = null
            if (!intentionalClose) {
                scheduleReconnect()
            } else {
                onStatusChange(ConnectionStatus.DISCONNECTED)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Connection failed: ${t.message}", t)
            this@WebSocketManager.webSocket = null
            onStatusChange(ConnectionStatus.DISCONNECTED)
            scheduleReconnect()
        }
    }
}
