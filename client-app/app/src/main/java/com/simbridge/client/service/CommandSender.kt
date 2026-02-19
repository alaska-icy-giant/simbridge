package com.simbridge.client.service

import com.simbridge.client.data.LogEntry
import com.simbridge.client.data.WsMessage
import java.util.UUID

/**
 * Builds and sends command messages to the Host via WebSocket.
 * Each command gets a unique req_id for tracking the response.
 */
class CommandSender(
    private val send: (WsMessage) -> Unit,
    private val addLog: (LogEntry) -> Unit,
) {

    fun sendSms(sim: Int, to: String, body: String): String {
        val reqId = UUID.randomUUID().toString()
        val msg = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            sim = sim,
            to = to,
            body = body,
            reqId = reqId,
        )
        addLog(LogEntry(direction = "OUT", summary = "SEND_SMS → $to"))
        send(msg)
        return reqId
    }

    fun makeCall(sim: Int, to: String): String {
        val reqId = UUID.randomUUID().toString()
        val msg = WsMessage(
            type = "command",
            cmd = "MAKE_CALL",
            sim = sim,
            to = to,
            reqId = reqId,
        )
        addLog(LogEntry(direction = "OUT", summary = "MAKE_CALL → $to"))
        send(msg)
        return reqId
    }

    fun hangUp(): String {
        val reqId = UUID.randomUUID().toString()
        val msg = WsMessage(
            type = "command",
            cmd = "HANG_UP",
            reqId = reqId,
        )
        addLog(LogEntry(direction = "OUT", summary = "HANG_UP"))
        send(msg)
        return reqId
    }

    fun getSims(): String {
        val reqId = UUID.randomUUID().toString()
        val msg = WsMessage(
            type = "command",
            cmd = "GET_SIMS",
            reqId = reqId,
        )
        addLog(LogEntry(direction = "OUT", summary = "GET_SIMS"))
        send(msg)
        return reqId
    }
}
