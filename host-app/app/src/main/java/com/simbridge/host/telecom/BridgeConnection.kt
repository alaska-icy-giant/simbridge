package com.simbridge.host.telecom

import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

/**
 * Represents a single call managed by SimBridge.
 * Reports call state changes that get forwarded as WS events.
 */
class BridgeConnection : Connection() {

    companion object {
        private const val TAG = "BridgeConnection"
    }

    var onStateChanged: ((String) -> Unit)? = null

    override fun onAnswer() {
        Log.i(TAG, "onAnswer")
        setActive()
        onStateChanged?.invoke("active")
    }

    override fun onReject() {
        Log.i(TAG, "onReject")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        onStateChanged?.invoke("ended")
        destroy()
    }

    override fun onDisconnect() {
        Log.i(TAG, "onDisconnect")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        onStateChanged?.invoke("ended")
        destroy()
    }

    override fun onAbort() {
        Log.i(TAG, "onAbort")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        onStateChanged?.invoke("ended")
        destroy()
    }

    override fun onHold() {
        Log.i(TAG, "onHold")
        setOnHold()
    }

    override fun onUnhold() {
        Log.i(TAG, "onUnhold")
        setActive()
    }

    override fun onStateChanged(state: Int) {
        super.onStateChanged(state)
        val stateName = when (state) {
            STATE_INITIALIZING -> "initializing"
            STATE_NEW -> "new"
            STATE_RINGING -> "ringing"
            STATE_DIALING -> "dialing"
            STATE_ACTIVE -> "active"
            STATE_HOLDING -> "holding"
            STATE_DISCONNECTED -> "ended"
            else -> "unknown"
        }
        Log.d(TAG, "State changed: $stateName")
    }
}
