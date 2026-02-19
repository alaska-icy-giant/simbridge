package com.simbridge.client.service

import android.util.Log
import com.simbridge.client.data.*

/**
 * Processes incoming events from the Host (via relay).
 * Mirror of the Host's CommandHandler — this side receives results and notifications.
 */
class EventHandler(
    private val onSmsSent: (reqId: String?, status: String) -> Unit,
    private val onSmsReceived: (SmsEntry) -> Unit,
    private val onCallState: (state: String, sim: Int?, reqId: String?) -> Unit,
    private val onIncomingCall: (from: String, sim: Int?) -> Unit,
    private val onSimInfo: (List<SimInfo>) -> Unit,
    private val onError: (message: String, reqId: String?) -> Unit,
    private val addLog: (LogEntry) -> Unit,
) {
    companion object {
        private const val TAG = "EventHandler"
    }

    fun handleEvent(message: WsMessage) {
        val event = message.event ?: message.error
        addLog(LogEntry(direction = "IN", summary = "EVT: ${event ?: "unknown"}"))
        Log.i(TAG, "Event: $event")

        // Handle relay error messages (target_offline, no paired host, etc.)
        if (message.error != null) {
            onError(message.error, message.reqId)
            return
        }

        when (event) {
            "SMS_SENT" -> {
                onSmsSent(message.reqId, message.status ?: "unknown")
            }

            "INCOMING_SMS" -> {
                val entry = SmsEntry(
                    direction = "received",
                    sim = message.sim,
                    address = message.from ?: "unknown",
                    body = message.body ?: "",
                )
                onSmsReceived(entry)
            }

            "INCOMING_CALL" -> {
                onIncomingCall(message.from ?: "unknown", message.sim)
            }

            "CALL_STATE" -> {
                onCallState(
                    message.state ?: "unknown",
                    message.sim,
                    message.reqId,
                )
            }

            "SIM_INFO" -> {
                onSimInfo(message.sims ?: emptyList())
            }

            "ERROR" -> {
                onError(message.body ?: "Unknown error", message.reqId)
            }

            else -> {
                Log.w(TAG, "Unknown event: $event")
            }
        }
    }
}
