package com.simbridge.host.fixtures

import com.simbridge.host.data.WsMessage

/**
 * Recording fake for SmsHandler. Captures all calls for assertion in tests.
 */
class FakeSmsHandler {

    data class SendCall(
        val to: String,
        val body: String,
        val simSlot: Int?,
        val reqId: String?,
    )

    val sendCalls = mutableListOf<SendCall>()
    var shouldThrow: Exception? = null

    fun sendSms(to: String, body: String, simSlot: Int?, reqId: String?) {
        if (shouldThrow != null) throw shouldThrow!!
        sendCalls.add(SendCall(to, body, simSlot, reqId))
    }
}
