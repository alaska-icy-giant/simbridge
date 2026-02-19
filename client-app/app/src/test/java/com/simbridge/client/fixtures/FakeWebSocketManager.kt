package com.simbridge.client.fixtures

import com.simbridge.client.data.ConnectionStatus
import com.simbridge.client.data.WsMessage

/**
 * Recording fake for WebSocketManager. Records connect/disconnect/send invocations
 * and allows tests to simulate status changes and incoming messages.
 */
class FakeWebSocketManager {

    var connectCount = 0
        private set
    var disconnectCount = 0
        private set

    val sentMessages = mutableListOf<WsMessage>()
    val sentRawMessages = mutableListOf<String>()

    private var onMessage: ((WsMessage) -> Unit)? = null
    private var onStatusChange: ((ConnectionStatus) -> Unit)? = null

    var currentStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set

    fun setCallbacks(
        onMessage: (WsMessage) -> Unit,
        onStatusChange: (ConnectionStatus) -> Unit,
    ) {
        this.onMessage = onMessage
        this.onStatusChange = onStatusChange
    }

    fun connect() {
        connectCount++
        simulateStatusChange(ConnectionStatus.CONNECTING)
    }

    fun disconnect() {
        disconnectCount++
        simulateStatusChange(ConnectionStatus.DISCONNECTED)
    }

    fun send(message: WsMessage) {
        sentMessages.add(message)
    }

    fun sendRaw(json: String) {
        sentRawMessages.add(json)
    }

    // ── Test helpers to simulate events ──

    fun simulateStatusChange(status: ConnectionStatus) {
        currentStatus = status
        onStatusChange?.invoke(status)
    }

    fun simulateIncomingMessage(message: WsMessage) {
        onMessage?.invoke(message)
    }

    fun simulateConnected() {
        simulateStatusChange(ConnectionStatus.CONNECTED)
    }

    fun simulateDisconnected() {
        simulateStatusChange(ConnectionStatus.DISCONNECTED)
    }
}
