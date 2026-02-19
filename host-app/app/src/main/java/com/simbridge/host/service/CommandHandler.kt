package com.simbridge.host.service

import android.util.Log
import com.simbridge.host.data.LogEntry
import com.simbridge.host.data.WsMessage

class CommandHandler(
    private val smsHandler: SmsHandler,
    private val callHandler: CallHandler,
    private val simInfoProvider: SimInfoProvider,
    private val sendEvent: (WsMessage) -> Unit,
    private val addLog: (LogEntry) -> Unit,
) {

    companion object {
        private const val TAG = "CommandHandler"
    }

    /**
     * Dispatches an incoming command message to the appropriate handler.
     */
    fun handleCommand(message: WsMessage) {
        val cmd = message.cmd ?: return
        addLog(LogEntry(direction = "IN", summary = "CMD: $cmd ${message.to ?: ""}"))
        Log.i(TAG, "Handling command: $cmd")

        when (cmd) {
            "SEND_SMS" -> {
                val to = message.to
                val body = message.body
                if (to == null || body == null) {
                    sendError(message.reqId, "SEND_SMS requires 'to' and 'body'")
                    return
                }
                smsHandler.sendSms(to, body, message.sim, message.reqId)
            }

            "MAKE_CALL" -> {
                val to = message.to
                if (to == null) {
                    sendError(message.reqId, "MAKE_CALL requires 'to'")
                    return
                }
                callHandler.makeCall(to, message.sim, message.reqId)
            }

            "HANG_UP" -> {
                callHandler.hangUp(message.reqId)
            }

            "GET_SIMS" -> {
                val sims = simInfoProvider.getActiveSimCards()
                sendEvent(
                    WsMessage(
                        type = "event",
                        event = "SIM_INFO",
                        sims = sims,
                        reqId = message.reqId,
                    )
                )
            }

            else -> {
                Log.w(TAG, "Unknown command: $cmd")
                sendError(message.reqId, "Unknown command: $cmd")
            }
        }
    }

    private fun sendError(reqId: String?, errorMsg: String) {
        sendEvent(
            WsMessage(
                type = "event",
                event = "ERROR",
                status = "error",
                body = errorMsg,
                reqId = reqId,
            )
        )
    }
}
