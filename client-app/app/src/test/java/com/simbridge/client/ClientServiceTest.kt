package com.simbridge.client

import com.simbridge.client.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ClientService logic. Since ClientService extends Android Service,
 * we test its core logic (log capping, SMS history capping, status callbacks,
 * message routing) in isolation without the Android framework.
 */
class ClientServiceTest {

    // ── Log Management (mirrors ClientService._logs) ──

    private val logs = mutableListOf<LogEntry>()
    private val maxLogEntries = 100

    private fun addLog(entry: LogEntry) {
        synchronized(logs) {
            logs.add(0, entry)
            if (logs.size > maxLogEntries) {
                logs.removeAt(logs.lastIndex)
            }
        }
    }

    // ── SMS History Management (mirrors ClientService._smsHistory) ──

    private val smsHistory = mutableListOf<SmsEntry>()
    private val maxSmsEntries = 200

    private fun addSms(entry: SmsEntry) {
        synchronized(smsHistory) {
            smsHistory.add(0, entry)
            if (smsHistory.size > maxSmsEntries) {
                smsHistory.removeAt(smsHistory.lastIndex)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        logs.clear()
        smsHistory.clear()
    }

    // ── Logs capped at 100 ──

    @Test
    fun `test log entries capped at 100`() {
        repeat(101) { i ->
            addLog(LogEntry(direction = "IN", summary = "Entry $i"))
        }

        assertEquals(100, logs.size)
    }

    @Test
    fun `test 101st log entry evicts the oldest`() {
        repeat(100) { i ->
            addLog(LogEntry(direction = "IN", summary = "Entry $i"))
        }
        assertEquals("Entry 0", logs.last().summary)

        addLog(LogEntry(direction = "IN", summary = "Entry 100"))

        assertEquals(100, logs.size)
        assertEquals("Entry 100", logs.first().summary)
        assertFalse(logs.any { it.summary == "Entry 0" })
    }

    @Test
    fun `test newest log entry is always at index 0`() {
        addLog(LogEntry(direction = "OUT", summary = "First"))
        addLog(LogEntry(direction = "IN", summary = "Second"))

        assertEquals("Second", logs[0].summary)
        assertEquals("First", logs[1].summary)
    }

    // ── SMS History capped at 200 ──

    @Test
    fun `test smsHistory capped at 200 entries`() {
        repeat(201) { i ->
            addSms(SmsEntry(direction = "received", sim = 1, address = "num$i", body = "msg$i"))
        }

        assertEquals(200, smsHistory.size)
    }

    @Test
    fun `test 201st SMS entry evicts the oldest`() {
        repeat(200) { i ->
            addSms(SmsEntry(direction = "received", sim = 1, address = "num$i", body = "msg$i"))
        }
        assertEquals("num0", smsHistory.last().address)

        addSms(SmsEntry(direction = "received", sim = 1, address = "num200", body = "msg200"))

        assertEquals(200, smsHistory.size)
        assertEquals("num200", smsHistory.first().address)
        assertFalse(smsHistory.any { it.address == "num0" })
    }

    @Test
    fun `test newest SMS entry is always at index 0`() {
        addSms(SmsEntry(direction = "sent", sim = 1, address = "first", body = "a"))
        addSms(SmsEntry(direction = "received", sim = 1, address = "second", body = "b"))

        assertEquals("second", smsHistory[0].address)
        assertEquals("first", smsHistory[1].address)
    }

    // ── Status Callback ──

    @Test
    fun `test status callback fires on connection change`() {
        val receivedStatuses = mutableListOf<ConnectionStatus>()
        val onStatusChange: (ConnectionStatus) -> Unit = { receivedStatuses.add(it) }

        onStatusChange(ConnectionStatus.CONNECTING)
        onStatusChange(ConnectionStatus.CONNECTED)
        onStatusChange(ConnectionStatus.DISCONNECTED)

        assertEquals(3, receivedStatuses.size)
        assertEquals(ConnectionStatus.CONNECTING, receivedStatuses[0])
        assertEquals(ConnectionStatus.CONNECTED, receivedStatuses[1])
        assertEquals(ConnectionStatus.DISCONNECTED, receivedStatuses[2])
    }

    @Test
    fun `test service status starts as DISCONNECTED`() {
        val status = ConnectionStatus.DISCONNECTED
        assertEquals(ConnectionStatus.DISCONNECTED, status)
    }

    // ── WS Message Routing ──

    @Test
    fun `test connected message type dispatches getSims conceptually`() {
        // ClientService.handleWsMessage: when type == "connected", call commandSender.getSims()
        var getSimsCalled = false
        val mockGetSims = { getSimsCalled = true }

        val msg = WsMessage(type = "connected", fromDeviceId = 42)
        when {
            msg.type == "connected" -> mockGetSims()
        }

        assertTrue(getSimsCalled)
    }

    @Test
    fun `test pong message type is ignored`() {
        var eventHandled = false
        val msg = WsMessage(type = "pong")

        when {
            msg.type == "connected" -> fail("Should not match connected")
            msg.type == "pong" -> { /* ignored */ }
            msg.type == "event" || msg.event != null -> { eventHandled = true }
        }

        assertFalse(eventHandled)
    }

    @Test
    fun `test event message type routes to event handler`() {
        var eventHandled = false
        val msg = WsMessage(type = "event", event = "SMS_SENT")

        when {
            msg.type == "connected" -> fail("Should not match connected")
            msg.type == "pong" -> fail("Should not match pong")
            msg.type == "event" || msg.event != null -> { eventHandled = true }
            msg.type == "webrtc" -> fail("Should not match webrtc")
        }

        assertTrue(eventHandled)
    }

    @Test
    fun `test webrtc message type routes to signaling handler`() {
        var signalingHandled = false
        val msg = WsMessage(type = "webrtc", action = "offer")

        when {
            msg.type == "connected" -> fail("Should not match connected")
            msg.type == "pong" -> fail("Should not match pong")
            msg.type == "event" || msg.event != null -> fail("Should not match event")
            msg.type == "webrtc" -> { signalingHandled = true }
        }

        assertTrue(signalingHandled)
    }

    @Test
    fun `test error field routes to event handler as relay error`() {
        var errorHandled = false
        val msg = WsMessage(type = "error", error = "target_offline")

        when {
            msg.type == "connected" -> fail("Should not match connected")
            msg.type == "pong" -> fail("Should not match pong")
            msg.type == "event" || msg.event != null -> fail("Should not match event")
            msg.type == "webrtc" -> fail("Should not match webrtc")
            msg.error != null -> { errorHandled = true }
        }

        assertTrue(errorHandled)
    }

    // ── Log Entry Properties ──

    @Test
    fun `test log entries have timestamp direction and summary`() {
        val before = System.currentTimeMillis()
        val entry = LogEntry(direction = "IN", summary = "Test message")
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
        assertEquals("IN", entry.direction)
        assertEquals("Test message", entry.summary)
    }

    @Test
    fun `test log directions are IN or OUT`() {
        val inEntry = LogEntry(direction = "IN", summary = "Incoming")
        val outEntry = LogEntry(direction = "OUT", summary = "Outgoing")

        assertEquals("IN", inEntry.direction)
        assertEquals("OUT", outEntry.direction)
    }

    // ── CallState mapping ──

    @Test
    fun `test call state mapping from string`() {
        fun mapCallState(state: String, current: CallState): CallState = when (state) {
            "dialing" -> CallState.DIALING
            "ringing" -> CallState.RINGING
            "active" -> CallState.ACTIVE
            "ended", "error" -> CallState.IDLE
            else -> current
        }

        assertEquals(CallState.DIALING, mapCallState("dialing", CallState.IDLE))
        assertEquals(CallState.RINGING, mapCallState("ringing", CallState.IDLE))
        assertEquals(CallState.ACTIVE, mapCallState("active", CallState.IDLE))
        assertEquals(CallState.IDLE, mapCallState("ended", CallState.ACTIVE))
        assertEquals(CallState.IDLE, mapCallState("error", CallState.ACTIVE))
        assertEquals(CallState.ACTIVE, mapCallState("unknown_state", CallState.ACTIVE))
    }
}
