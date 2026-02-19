package com.simbridge.client

import com.simbridge.client.data.LogEntry
import com.simbridge.client.data.WsMessage
import com.simbridge.client.service.CommandSender
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandSenderTest {

    private val sentMessages = mutableListOf<WsMessage>()
    private val logEntries = mutableListOf<LogEntry>()

    private lateinit var sender: CommandSender

    @BeforeEach
    fun setUp() {
        sentMessages.clear()
        logEntries.clear()

        sender = CommandSender(
            send = { sentMessages.add(it) },
            addLog = { logEntries.add(it) },
        )
    }

    // ── sendSms ──

    @Test
    fun `test sendSms builds SEND_SMS WsMessage`() {
        sender.sendSms(sim = 1, to = "+15551234567", body = "Hello")

        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0]
        assertEquals("command", msg.type)
        assertEquals("SEND_SMS", msg.cmd)
        assertEquals(1, msg.sim)
        assertEquals("+15551234567", msg.to)
        assertEquals("Hello", msg.body)
    }

    @Test
    fun `test sendSms includes unique reqId`() {
        val reqId = sender.sendSms(sim = 1, to = "+15551234567", body = "Hello")

        assertNotNull(reqId)
        assertTrue(reqId.isNotBlank())
        assertEquals(reqId, sentMessages[0].reqId)
    }

    @Test
    fun `test sendSms creates a log entry`() {
        sender.sendSms(sim = 1, to = "+15551234567", body = "Hello")

        assertEquals(1, logEntries.size)
        assertEquals("OUT", logEntries[0].direction)
        assertTrue(logEntries[0].summary.contains("SEND_SMS"))
        assertTrue(logEntries[0].summary.contains("+15551234567"))
    }

    // ── makeCall ──

    @Test
    fun `test makeCall builds MAKE_CALL WsMessage`() {
        sender.makeCall(sim = 2, to = "+15559876543")

        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0]
        assertEquals("command", msg.type)
        assertEquals("MAKE_CALL", msg.cmd)
        assertEquals(2, msg.sim)
        assertEquals("+15559876543", msg.to)
    }

    @Test
    fun `test makeCall includes unique reqId`() {
        val reqId = sender.makeCall(sim = 2, to = "+15559876543")

        assertNotNull(reqId)
        assertTrue(reqId.isNotBlank())
        assertEquals(reqId, sentMessages[0].reqId)
    }

    @Test
    fun `test makeCall creates a log entry`() {
        sender.makeCall(sim = 1, to = "+15559876543")

        assertEquals(1, logEntries.size)
        assertEquals("OUT", logEntries[0].direction)
        assertTrue(logEntries[0].summary.contains("MAKE_CALL"))
        assertTrue(logEntries[0].summary.contains("+15559876543"))
    }

    // ── hangUp ──

    @Test
    fun `test hangUp builds HANG_UP WsMessage`() {
        sender.hangUp()

        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0]
        assertEquals("command", msg.type)
        assertEquals("HANG_UP", msg.cmd)
    }

    @Test
    fun `test hangUp includes unique reqId`() {
        val reqId = sender.hangUp()

        assertNotNull(reqId)
        assertTrue(reqId.isNotBlank())
        assertEquals(reqId, sentMessages[0].reqId)
    }

    @Test
    fun `test hangUp creates a log entry`() {
        sender.hangUp()

        assertEquals(1, logEntries.size)
        assertEquals("OUT", logEntries[0].direction)
        assertTrue(logEntries[0].summary.contains("HANG_UP"))
    }

    // ── getSims ──

    @Test
    fun `test getSims builds GET_SIMS WsMessage`() {
        sender.getSims()

        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0]
        assertEquals("command", msg.type)
        assertEquals("GET_SIMS", msg.cmd)
    }

    @Test
    fun `test getSims includes unique reqId`() {
        val reqId = sender.getSims()

        assertNotNull(reqId)
        assertTrue(reqId.isNotBlank())
        assertEquals(reqId, sentMessages[0].reqId)
    }

    @Test
    fun `test getSims creates a log entry`() {
        sender.getSims()

        assertEquals(1, logEntries.size)
        assertEquals("OUT", logEntries[0].direction)
        assertTrue(logEntries[0].summary.contains("GET_SIMS"))
    }

    // ── Unique reqId per call ──

    @Test
    fun `test each command generates unique UUID reqId`() {
        val ids = mutableSetOf<String>()

        ids.add(sender.sendSms(sim = 1, to = "1", body = "a"))
        ids.add(sender.makeCall(sim = 1, to = "2"))
        ids.add(sender.hangUp())
        ids.add(sender.getSims())

        assertEquals(4, ids.size, "All reqIds should be unique")
    }

    @Test
    fun `test multiple sendSms calls produce different reqIds`() {
        val id1 = sender.sendSms(sim = 1, to = "1", body = "a")
        val id2 = sender.sendSms(sim = 1, to = "2", body = "b")

        assertNotEquals(id1, id2)
    }
}
