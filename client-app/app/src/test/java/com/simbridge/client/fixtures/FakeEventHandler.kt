package com.simbridge.client.fixtures

import com.simbridge.client.data.WsMessage

/**
 * Recording fake for EventHandler. Captures handleEvent invocations
 * so tests can verify which messages were dispatched.
 */
class FakeEventHandler {

    data class HandleEventRecord(
        val message: WsMessage,
    )

    val handleEventCalls = mutableListOf<HandleEventRecord>()

    fun handleEvent(message: WsMessage) {
        handleEventCalls.add(HandleEventRecord(message))
    }

    val callCount: Int get() = handleEventCalls.size

    fun lastMessage(): WsMessage? = handleEventCalls.lastOrNull()?.message

    fun messagesWithEvent(event: String): List<WsMessage> =
        handleEventCalls.map { it.message }.filter { it.event == event }
}
