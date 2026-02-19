package com.simbridge.client.fixtures

import java.util.UUID

/**
 * Recording fake for CommandSender. Captures sendSms/makeCall/hangUp/getSims invocations
 * and returns generated reqIds for assertion.
 */
class FakeCommandSender {

    data class SendSmsRecord(
        val sim: Int,
        val to: String,
        val body: String,
        val reqId: String,
    )

    data class MakeCallRecord(
        val sim: Int,
        val to: String,
        val reqId: String,
    )

    data class HangUpRecord(
        val reqId: String,
    )

    data class GetSimsRecord(
        val reqId: String,
    )

    val sendSmsCalls = mutableListOf<SendSmsRecord>()
    val makeCallCalls = mutableListOf<MakeCallRecord>()
    val hangUpCalls = mutableListOf<HangUpRecord>()
    val getSimsCalls = mutableListOf<GetSimsRecord>()

    fun sendSms(sim: Int, to: String, body: String): String {
        val reqId = UUID.randomUUID().toString()
        sendSmsCalls.add(SendSmsRecord(sim, to, body, reqId))
        return reqId
    }

    fun makeCall(sim: Int, to: String): String {
        val reqId = UUID.randomUUID().toString()
        makeCallCalls.add(MakeCallRecord(sim, to, reqId))
        return reqId
    }

    fun hangUp(): String {
        val reqId = UUID.randomUUID().toString()
        hangUpCalls.add(HangUpRecord(reqId))
        return reqId
    }

    fun getSims(): String {
        val reqId = UUID.randomUUID().toString()
        getSimsCalls.add(GetSimsRecord(reqId))
        return reqId
    }
}
