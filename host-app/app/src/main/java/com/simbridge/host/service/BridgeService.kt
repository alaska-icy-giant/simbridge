package com.simbridge.host.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simbridge.host.MainActivity
import com.simbridge.host.R
import com.simbridge.host.SimBridgeApp
import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.LogEntry
import com.simbridge.host.data.Prefs
import com.simbridge.host.data.WsMessage
import com.simbridge.host.telecom.BridgeConnectionService
import com.simbridge.host.webrtc.SignalingHandler
import com.simbridge.host.webrtc.WebRtcManager

class BridgeService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOG_ENTRIES = 100
    }

    inner class LocalBinder : Binder() {
        val service: BridgeService get() = this@BridgeService
    }

    private val binder = LocalBinder()
    private lateinit var prefs: Prefs
    private lateinit var wsManager: WebSocketManager
    private lateinit var commandHandler: CommandHandler
    private lateinit var smsHandler: SmsHandler
    private lateinit var callHandler: CallHandler
    private lateinit var simInfoProvider: SimInfoProvider
    private lateinit var webRtcManager: WebRtcManager
    private lateinit var signalingHandler: SignalingHandler

    private val smsReceiver = SmsReceiver()

    private val mainHandler = Handler(Looper.getMainLooper())

    // Observable state — callbacks are always invoked on main thread
    @Volatile var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set
    var onStatusChange: ((ConnectionStatus) -> Unit)? = null
    var onLogEntry: ((LogEntry) -> Unit)? = null

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = synchronized(_logs) { _logs.toList() }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        prefs = Prefs(this)
        simInfoProvider = SimInfoProvider(this)

        // WebSocket
        wsManager = WebSocketManager(
            prefs = prefs,
            onMessage = ::handleWsMessage,
            onStatusChange = ::handleStatusChange,
        )

        // SMS
        smsHandler = SmsHandler(this, simInfoProvider, ::sendEvent)

        // Calls
        callHandler = CallHandler(this, simInfoProvider, ::sendEvent)

        // Command dispatcher
        commandHandler = CommandHandler(
            smsHandler = smsHandler,
            callHandler = callHandler,
            simInfoProvider = simInfoProvider,
            sendEvent = ::sendEvent,
            addLog = ::addLog,
        )

        // WebRTC
        webRtcManager = WebRtcManager(this)
        signalingHandler = SignalingHandler(webRtcManager, ::sendWsMessage)

        // SMS broadcast receiver
        smsReceiver.onSmsReceived = { event ->
            addLog(LogEntry(direction = "IN", summary = "SMS from ${event.from}"))
            sendEvent(event)
        }

        // H-14: Wire ConnectionService call state events to WebSocket
        BridgeConnectionService.onCallStateEvent = { state, address ->
            val event = WsMessage(
                type = "event",
                event = "CALL_STATE",
                state = state,
                from = address,
            )
            addLog(LogEntry(direction = "OUT", summary = "CALL_STATE: $state"))
            sendEvent(event)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
        registerSmsReceiver()
        wsManager.connect()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        BridgeConnectionService.onCallStateEvent = null
        wsManager.disconnect()
        webRtcManager.dispose()
        unregisterSmsReceiver()
        super.onDestroy()
    }

    fun reconnect() {
        wsManager.disconnect()
        wsManager.connect()
    }

    private fun handleWsMessage(message: WsMessage) {
        when (message.type) {
            "command" -> commandHandler.handleCommand(message)
            "webrtc" -> signalingHandler.handleSignaling(message)
            else -> Log.w(TAG, "Unknown message type: ${message.type}")
        }
    }

    private fun handleStatusChange(status: ConnectionStatus) {
        connectionStatus = status
        mainHandler.post { onStatusChange?.invoke(status) }

        val text = when (status) {
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.CONNECTING -> "Reconnecting..."
            ConnectionStatus.DISCONNECTED -> "Offline"
        }
        updateNotification(text)
    }

    private fun sendEvent(event: WsMessage) {
        addLog(LogEntry(direction = "OUT", summary = "${event.event} ${event.status ?: ""}"))
        wsManager.send(event)
    }

    private fun sendWsMessage(message: WsMessage) {
        wsManager.send(message)
    }

    private fun addLog(entry: LogEntry) {
        synchronized(_logs) {
            _logs.add(0, entry)
            if (_logs.size > MAX_LOG_ENTRIES) {
                _logs.removeAt(_logs.lastIndex)
            }
        }
        mainHandler.post { onLogEntry?.invoke(entry) }
    }

    private fun registerSmsReceiver() {
        try {
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            registerReceiver(smsReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS receiver", e)
        }
    }

    private fun unregisterSmsReceiver() {
        try {
            unregisterReceiver(smsReceiver)
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SimBridgeApp.CHANNEL_ID)
            .setContentTitle("SimBridge")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
