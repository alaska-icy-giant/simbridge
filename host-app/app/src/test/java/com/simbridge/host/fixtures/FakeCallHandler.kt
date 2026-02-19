package com.simbridge.host.fixtures

/**
 * Recording fake for CallHandler. Captures makeCall and hangUp invocations.
 */
class FakeCallHandler {

    data class MakeCallRecord(
        val to: String,
        val simSlot: Int?,
        val reqId: String?,
    )

    data class HangUpRecord(
        val reqId: String?,
    )

    val makeCallCalls = mutableListOf<MakeCallRecord>()
    val hangUpCalls = mutableListOf<HangUpRecord>()

    fun makeCall(to: String, simSlot: Int?, reqId: String?) {
        makeCallCalls.add(MakeCallRecord(to, simSlot, reqId))
    }

    fun hangUp(reqId: String?) {
        hangUpCalls.add(HangUpRecord(reqId))
    }
}
