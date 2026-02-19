package com.simbridge.client

import com.simbridge.client.data.*
import com.simbridge.client.service.EventHandler
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventHandlerTest {

    private val smsSentCalls = mutableListOf<Pair<String?, String>>()
    private val smsReceivedCalls = mutableListOf<SmsEntry>()
    private val callStateCalls = mutableListOf<Triple<String, Int?, String?>>()
    private val incomingCallCalls = mutableListOf<Pair<String, Int?>>()
    private val simInfoCalls = mutableListOf<List<SimInfo>>()
    private val errorCalls = mutableListOf<Pair<String, String?>>()
    private val logEntries = mutableListOf<LogEntry>()

    private lateinit var handler: EventHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        smsSentCalls.clear()
        smsReceivedCalls.clear()
        callStateCalls.clear()
        incomingCallCalls.clear()
        simInfoCalls.clear()
        errorCalls.clear()
        logEntries.clear()

        handler = EventHandler(
            onSmsSent = { reqId, status -> smsSentCalls.add(reqId to status) },
            onSmsReceived = { smsReceivedCalls.add(it) },
            onCallState = { state, sim, reqId -> callStateCalls.add(Triple(state, sim, reqId)) },
            onIncomingCall = { from, sim -> incomingCallCalls.add(from to sim) },
            onSimInfo = { simInfoCalls.add(it) },
            onError = { message, reqId -> errorCalls.add(message to reqId) },
            addLog = { logEntries.add(it) },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── SMS_SENT ──

    @Test
    fun `test SMS_SENT event calls onSmsSent with reqId and status`() {
        val msg = WsMessage(
            type = "event",
            event = "SMS_SENT",
            status = "ok",
            reqId = "req-1",
        )

        handler.handleEvent(msg)

        assertEquals(1, smsSentCalls.size)
        assertEquals("req-1", smsSentCalls[0].first)
        assertEquals("ok", smsSentCalls[0].second)
    }

    @Test
    fun `test SMS_SENT event with null status uses unknown`() {
        val msg = WsMessage(
            type = "event",
            event = "SMS_SENT",
            reqId = "req-2",
        )

        handler.handleEvent(msg)

        assertEquals(1, smsSentCalls.size)
        assertEquals("unknown", smsSentCalls[0].second)
    }

    // ── INCOMING_SMS ──

    @Test
    fun `test INCOMING_SMS event calls onSmsReceived with SmsEntry fields`() {
        val msg = WsMessage(
            type = "event",
            event = "INCOMING_SMS",
            from = "+15551234567",
            body = "Hello there",
            sim = 1,
        )

        handler.handleEvent(msg)

        assertEquals(1, smsReceivedCalls.size)
        val entry = smsReceivedCalls[0]
        assertEquals("received", entry.direction)
        assertEquals("+15551234567", entry.address)
        assertEquals("Hello there", entry.body)
        assertEquals(1, entry.sim)
    }

    @Test
    fun `test INCOMING_SMS with null from uses unknown`() {
        val msg = WsMessage(
            type = "event",
            event = "INCOMING_SMS",
            body = "Test",
        )

        handler.handleEvent(msg)

        assertEquals("unknown", smsReceivedCalls[0].address)
    }

    @Test
    fun `test INCOMING_SMS with null body uses empty string`() {
        val msg = WsMessage(
            type = "event",
            event = "INCOMING_SMS",
            from = "+15551234567",
        )

        handler.handleEvent(msg)

        assertEquals("", smsReceivedCalls[0].body)
    }

    // ── INCOMING_CALL ──

    @Test
    fun `test INCOMING_CALL event calls onIncomingCall`() {
        val msg = WsMessage(
            type = "event",
            event = "INCOMING_CALL",
            from = "+15559876543",
            sim = 2,
        )

        handler.handleEvent(msg)

        assertEquals(1, incomingCallCalls.size)
        assertEquals("+15559876543", incomingCallCalls[0].first)
        assertEquals(2, incomingCallCalls[0].second)
    }

    @Test
    fun `test INCOMING_CALL with null from uses unknown`() {
        val msg = WsMessage(
            type = "event",
            event = "INCOMING_CALL",
        )

        handler.handleEvent(msg)

        assertEquals("unknown", incomingCallCalls[0].first)
        assertNull(incomingCallCalls[0].second)
    }

    // ── CALL_STATE ──

    @Test
    fun `test CALL_STATE event calls onCallState with state string`() {
        val msg = WsMessage(
            type = "event",
            event = "CALL_STATE",
            state = "ringing",
            sim = 1,
            reqId = "req-3",
        )

        handler.handleEvent(msg)

        assertEquals(1, callStateCalls.size)
        assertEquals("ringing", callStateCalls[0].first)
        assertEquals(1, callStateCalls[0].second)
        assertEquals("req-3", callStateCalls[0].third)
    }

    @Test
    fun `test CALL_STATE with null state uses unknown`() {
        val msg = WsMessage(
            type = "event",
            event = "CALL_STATE",
        )

        handler.handleEvent(msg)

        assertEquals("unknown", callStateCalls[0].first)
    }

    // ── SIM_INFO ──

    @Test
    fun `test SIM_INFO event calls onSimInfo with list of SimInfo`() {
        val sims = listOf(
            SimInfo(slot = 1, carrier = "T-Mobile", number = "+15551111111"),
            SimInfo(slot = 2, carrier = "AT&T", number = null),
        )
        val msg = WsMessage(
            type = "event",
            event = "SIM_INFO",
            sims = sims,
        )

        handler.handleEvent(msg)

        assertEquals(1, simInfoCalls.size)
        assertEquals(2, simInfoCalls[0].size)
        assertEquals("T-Mobile", simInfoCalls[0][0].carrier)
        assertNull(simInfoCalls[0][1].number)
    }

    @Test
    fun `test SIM_INFO with null sims passes empty list`() {
        val msg = WsMessage(
            type = "event",
            event = "SIM_INFO",
        )

        handler.handleEvent(msg)

        assertEquals(1, simInfoCalls.size)
        assertTrue(simInfoCalls[0].isEmpty())
    }

    // ── ERROR ──

    @Test
    fun `test ERROR event calls onError`() {
        val msg = WsMessage(
            type = "event",
            event = "ERROR",
            body = "Something went wrong",
            reqId = "req-4",
        )

        handler.handleEvent(msg)

        assertEquals(1, errorCalls.size)
        assertEquals("Something went wrong", errorCalls[0].first)
        assertEquals("req-4", errorCalls[0].second)
    }

    @Test
    fun `test ERROR event with null body uses Unknown error`() {
        val msg = WsMessage(
            type = "event",
            event = "ERROR",
        )

        handler.handleEvent(msg)

        assertEquals("Unknown error", errorCalls[0].first)
    }

    // ── Error field in message ──

    @Test
    fun `test error field in message triggers onError before event dispatch`() {
        val msg = WsMessage(
            type = "event",
            event = "SMS_SENT",
            error = "target_offline",
            reqId = "req-5",
        )

        handler.handleEvent(msg)

        // Error field takes priority
        assertEquals(1, errorCalls.size)
        assertEquals("target_offline", errorCalls[0].first)
        assertEquals("req-5", errorCalls[0].second)
        // SMS_SENT should NOT be called
        assertTrue(smsSentCalls.isEmpty())
    }

    // ── Unknown Event ──

    @Test
    fun `test unknown event type does not crash`() {
        val msg = WsMessage(
            type = "event",
            event = "UNKNOWN_FUTURE_EVENT",
        )

        assertDoesNotThrow { handler.handleEvent(msg) }

        // No callbacks called except addLog
        assertTrue(smsSentCalls.isEmpty())
        assertTrue(smsReceivedCalls.isEmpty())
        assertTrue(callStateCalls.isEmpty())
        assertTrue(incomingCallCalls.isEmpty())
        assertTrue(simInfoCalls.isEmpty())
        assertTrue(errorCalls.isEmpty())
    }

    // ── Log entries ──

    @Test
    fun `test every event adds a log entry`() {
        handler.handleEvent(WsMessage(type = "event", event = "SMS_SENT", status = "ok"))
        handler.handleEvent(WsMessage(type = "event", event = "INCOMING_SMS", from = "x", body = "y"))
        handler.handleEvent(WsMessage(type = "event", event = "SIM_INFO"))

        assertEquals(3, logEntries.size)
        assertTrue(logEntries.all { it.direction == "IN" })
    }

    @Test
    fun `test log entry summary contains event name`() {
        handler.handleEvent(WsMessage(type = "event", event = "CALL_STATE", state = "active"))

        assertTrue(logEntries[0].summary.contains("CALL_STATE"))
    }
}
