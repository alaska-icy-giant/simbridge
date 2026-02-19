package com.simbridge.host

import com.simbridge.host.data.LogEntry
import com.simbridge.host.data.SimInfo
import com.simbridge.host.data.WsMessage
import com.simbridge.host.service.CallHandler
import com.simbridge.host.service.CommandHandler
import com.simbridge.host.service.SimInfoProvider
import com.simbridge.host.service.SmsHandler
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandHandlerTest {

    private val smsHandler = mockk<SmsHandler>(relaxed = true)
    private val callHandler = mockk<CallHandler>(relaxed = true)
    private val simInfoProvider = mockk<SimInfoProvider>(relaxed = true)
    private val sentEvents = mutableListOf<WsMessage>()
    private val logEntries = mutableListOf<LogEntry>()

    private lateinit var handler: CommandHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        sentEvents.clear()
        logEntries.clear()

        handler = CommandHandler(
            smsHandler = smsHandler,
            callHandler = callHandler,
            simInfoProvider = simInfoProvider,
            sendEvent = { sentEvents.add(it) },
            addLog = { logEntries.add(it) },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── SEND_SMS Dispatch ──

    @Test
    fun `test dispatch SEND_SMS routes to SmsHandler`() {
        val msg = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            to = "+15551234567",
            body = "Hello",
            sim = 1,
            reqId = "req-1",
        )

        handler.handleCommand(msg)

        verify(exactly = 1) {
            smsHandler.sendSms("+15551234567", "Hello", 1, "req-1")
        }
    }

    @Test
    fun `test dispatch SEND_SMS with null sim passes null`() {
        val msg = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            to = "+15551234567",
            body = "Hello",
            reqId = "req-2",
        )

        handler.handleCommand(msg)

        verify(exactly = 1) {
            smsHandler.sendSms("+15551234567", "Hello", null, "req-2")
        }
    }

    // ── MAKE_CALL Dispatch ──

    @Test
    fun `test dispatch MAKE_CALL routes to CallHandler`() {
        val msg = WsMessage(
            type = "command",
            cmd = "MAKE_CALL",
            to = "+15559876543",
            sim = 2,
            reqId = "req-3",
        )

        handler.handleCommand(msg)

        verify(exactly = 1) {
            callHandler.makeCall("+15559876543", 2, "req-3")
        }
    }

    // ── HANG_UP Dispatch ──

    @Test
    fun `test dispatch HANG_UP routes to CallHandler`() {
        val msg = WsMessage(
            type = "command",
            cmd = "HANG_UP",
            reqId = "req-4",
        )

        handler.handleCommand(msg)

        verify(exactly = 1) {
            callHandler.hangUp("req-4")
        }
    }

    // ── GET_SIMS Dispatch ──

    @Test
    fun `test dispatch GET_SIMS routes to SimInfoProvider and sends event`() {
        val sims = listOf(
            SimInfo(slot = 1, carrier = "T-Mobile", number = "+15551111111"),
            SimInfo(slot = 2, carrier = "AT&T", number = "+15552222222"),
        )
        every { simInfoProvider.getActiveSimCards() } returns sims

        val msg = WsMessage(
            type = "command",
            cmd = "GET_SIMS",
            reqId = "req-5",
        )

        handler.handleCommand(msg)

        verify(exactly = 1) { simInfoProvider.getActiveSimCards() }
        assertEquals(1, sentEvents.size)

        val event = sentEvents[0]
        assertEquals("event", event.type)
        assertEquals("SIM_INFO", event.event)
        assertEquals("req-5", event.reqId)
        assertEquals(sims, event.sims)
    }

    // ── Unknown Command ──

    @Test
    fun `test unknown command does not crash and sends error event`() {
        val msg = WsMessage(
            type = "command",
            cmd = "SELF_DESTRUCT",
            reqId = "req-6",
        )

        assertDoesNotThrow { handler.handleCommand(msg) }

        assertEquals(1, sentEvents.size)
        val event = sentEvents[0]
        assertEquals("ERROR", event.event)
        assertEquals("error", event.status)
        assertTrue(event.body!!.contains("Unknown command"))
    }

    @Test
    fun `test null cmd is ignored silently`() {
        val msg = WsMessage(type = "command", cmd = null)

        assertDoesNotThrow { handler.handleCommand(msg) }

        assertTrue(sentEvents.isEmpty())
        assertTrue(logEntries.isEmpty())
    }

    // ── Missing Required Fields ──

    @Test
    fun `test SEND_SMS missing to field returns error`() {
        val msg = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            body = "Hello",
            reqId = "req-7",
        )

        handler.handleCommand(msg)

        verify(exactly = 0) { smsHandler.sendSms(any(), any(), any(), any()) }
        assertEquals(1, sentEvents.size)
        assertEquals("ERROR", sentEvents[0].event)
        assertEquals("error", sentEvents[0].status)
        assertTrue(sentEvents[0].body!!.contains("'to'"))
    }

    @Test
    fun `test SEND_SMS missing body field returns error`() {
        val msg = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            to = "+15551234567",
            reqId = "req-8",
        )

        handler.handleCommand(msg)

        verify(exactly = 0) { smsHandler.sendSms(any(), any(), any(), any()) }
        assertEquals(1, sentEvents.size)
        assertEquals("ERROR", sentEvents[0].event)
        assertTrue(sentEvents[0].body!!.contains("'body'"))
    }

    @Test
    fun `test MAKE_CALL missing to field returns error`() {
        val msg = WsMessage(
            type = "command",
            cmd = "MAKE_CALL",
            reqId = "req-9",
        )

        handler.handleCommand(msg)

        verify(exactly = 0) { callHandler.makeCall(any(), any(), any()) }
        assertEquals(1, sentEvents.size)
        assertEquals("ERROR", sentEvents[0].event)
        assertTrue(sentEvents[0].body!!.contains("'to'"))
    }

    // ── Log Entries ──

    @Test
    fun `test command handling adds log entry`() {
        val msg = WsMessage(
            type = "command",
            cmd = "HANG_UP",
            reqId = "req-10",
        )

        handler.handleCommand(msg)

        assertEquals(1, logEntries.size)
        assertEquals("IN", logEntries[0].direction)
        assertTrue(logEntries[0].summary.contains("HANG_UP"))
    }
}
