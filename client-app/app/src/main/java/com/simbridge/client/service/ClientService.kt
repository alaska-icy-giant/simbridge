package com.simbridge.client.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simbridge.client.MainActivity
import com.simbridge.client.R
import com.simbridge.client.SimBridgeClientApp
import com.simbridge.client.data.*
import com.simbridge.client.webrtc.ClientSignalingHandler
import com.simbridge.client.webrtc.ClientWebRtcManager

/**
 * Foreground service that maintains the WebSocket connection to the relay
 * and processes events from the paired Host device.
 */
class ClientService : Service() {

    companion object {
        private const val TAG = "ClientService"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOG_ENTRIES = 100
        private const val MAX_SMS_ENTRIES = 200
    }

    inner class LocalBinder : Binder() {
        val service: ClientService get() = this@ClientService
    }

    private val binder = LocalBinder()
    private lateinit var prefs: Prefs
    private lateinit var wsManager: WebSocketManager
    private lateinit var eventHandler: EventHandler
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var webRtcManager: ClientWebRtcManager
    private lateinit var signalingHandler: ClientSignalingHandler

    lateinit var commandSender: CommandSender
        private set

    // Observable state
    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set
    var hostSims: List<SimInfo> = emptyList()
        private set
    var callState: CallState = CallState.IDLE
        private set
    var callNumber: String? = null
        private set

    var onStatusChange: ((ConnectionStatus) -> Unit)? = null
    var onSimsUpdated: ((List<SimInfo>) -> Unit)? = null
    var onSmsReceived: ((SmsEntry) -> Unit)? = null
    var onCallStateChange: ((CallState, String?) -> Unit)? = null
    var onLogEntry: ((LogEntry) -> Unit)? = null

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs.toList()

    private val _smsHistory = mutableListOf<SmsEntry>()
    val smsHistory: List<SmsEntry> get() = _smsHistory.toList()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        prefs = Prefs(this)
        notificationHelper = NotificationHelper(this)

        wsManager = WebSocketManager(
            prefs = prefs,
            onMessage = ::handleWsMessage,
            onStatusChange = ::handleStatusChange,
        )

        commandSender = CommandSender(
            send = { wsManager.send(it) },
            addLog = ::addLog,
        )

        webRtcManager = ClientWebRtcManager(this)
        signalingHandler = ClientSignalingHandler(webRtcManager) { wsManager.send(it) }

        eventHandler = EventHandler(
            onSmsSent = ::handleSmsSent,
            onSmsReceived = ::handleSmsReceived,
            onCallState = ::handleCallState,
            onIncomingCall = ::handleIncomingCall,
            onSimInfo = ::handleSimInfo,
            onError = ::handleError,
            addLog = ::addLog,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
        wsManager.connect()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        wsManager.disconnect()
        webRtcManager.dispose()
        super.onDestroy()
    }

    fun reconnect() {
        wsManager.disconnect()
        wsManager.connect()
    }

    // ── WS message dispatch ──

    private fun handleWsMessage(message: WsMessage) {
        when {
            message.type == "connected" -> {
                Log.i(TAG, "Server confirmed connection: device ${message.fromDeviceId}")
                // Request SIM info on connect
                commandSender.getSims()
            }
            message.type == "pong" -> { /* keepalive response, ignore */ }
            message.type == "event" || message.event != null -> eventHandler.handleEvent(message)
            message.type == "webrtc" -> signalingHandler.handleSignaling(message)
            message.error != null -> eventHandler.handleEvent(message) // relay error
            else -> Log.w(TAG, "Unknown message type: ${message.type}")
        }
    }

    // ── Event callbacks ──

    private fun handleStatusChange(status: ConnectionStatus) {
        connectionStatus = status
        onStatusChange?.invoke(status)
        val text = when (status) {
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.CONNECTING -> "Reconnecting..."
            ConnectionStatus.DISCONNECTED -> "Offline"
        }
        updateNotification(text)
    }

    private fun handleSmsSent(reqId: String?, status: String) {
        addLog(LogEntry(direction = "IN", summary = "SMS_SENT: $status"))
        // Could match reqId to pending SMS and update status
    }

    private fun handleSmsReceived(entry: SmsEntry) {
        synchronized(_smsHistory) {
            _smsHistory.add(0, entry)
            if (_smsHistory.size > MAX_SMS_ENTRIES) _smsHistory.removeAt(_smsHistory.lastIndex)
        }
        onSmsReceived?.invoke(entry)
        notificationHelper.notifyIncomingSms(entry.address, entry.body)
    }

    private fun handleCallState(state: String, sim: Int?, reqId: String?) {
        callState = when (state) {
            "dialing" -> CallState.DIALING
            "ringing" -> CallState.RINGING
            "active" -> CallState.ACTIVE
            "ended", "error" -> {
                notificationHelper.cancelCallNotification()
                webRtcManager.closePeerConnection()
                CallState.IDLE
            }
            else -> callState
        }
        onCallStateChange?.invoke(callState, callNumber)
    }

    private fun handleIncomingCall(from: String, sim: Int?) {
        callState = CallState.RINGING
        callNumber = from
        onCallStateChange?.invoke(callState, from)
        notificationHelper.notifyIncomingCall(from)
    }

    private fun handleSimInfo(sims: List<SimInfo>) {
        hostSims = sims
        onSimsUpdated?.invoke(sims)
    }

    private fun handleError(message: String, reqId: String?) {
        addLog(LogEntry(direction = "IN", summary = "ERROR: $message"))
    }

    private fun addLog(entry: LogEntry) {
        synchronized(_logs) {
            _logs.add(0, entry)
            if (_logs.size > MAX_LOG_ENTRIES) _logs.removeAt(_logs.lastIndex)
        }
        onLogEntry?.invoke(entry)
    }

    // ── Notifications ──

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SimBridgeClientApp.CHANNEL_ID)
            .setContentTitle("SimBridge Client")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val n = buildNotification(text)
            val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.notify(NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
