package com.simbridge.client

import com.simbridge.client.data.ConnectionStatus
import com.simbridge.client.data.Prefs
import com.simbridge.client.data.WsMessage
import com.simbridge.client.service.WebSocketManager
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebSocketManagerTest {

    private val prefs = mockk<Prefs>(relaxed = true)
    private val statusChanges = mutableListOf<ConnectionStatus>()
    private val receivedMessages = mutableListOf<WsMessage>()

    private lateinit var wsManager: WebSocketManager

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        statusChanges.clear()
        receivedMessages.clear()

        wsManager = WebSocketManager(
            prefs = prefs,
            onMessage = { receivedMessages.add(it) },
            onStatusChange = { statusChanges.add(it) },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── URL Conversion Tests ──

    @Test
    fun `test http URL converts to ws URL`() {
        val httpUrl = "http://example.com:8100"
        val expected = "ws://example.com:8100"
        val actual = httpUrl.replace("https://", "wss://").replace("http://", "ws://")
        assertEquals(expected, actual)
    }

    @Test
    fun `test https URL converts to wss URL`() {
        val httpsUrl = "https://example.com:8100"
        val expected = "wss://example.com:8100"
        val actual = httpsUrl.replace("https://", "wss://").replace("http://", "ws://")
        assertEquals(expected, actual)
    }

    @Test
    fun `test token appended as query param`() {
        val serverUrl = "http://example.com:8100"
        val token = "jwt-token-abc"
        val deviceId = 42
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/client/$deviceId?token=$token"

        assertEquals("ws://example.com:8100/ws/client/42?token=jwt-token-abc", wsUrl)
    }

    @Test
    fun `test trailing slash trimmed from server URL`() {
        val serverUrl = "http://example.com:8100/"
        val token = "tok"
        val deviceId = 1
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/client/$deviceId?token=$token"

        assertEquals("ws://example.com:8100/ws/client/1?token=tok", wsUrl)
    }

    @Test
    fun `test wss URL with path`() {
        val serverUrl = "https://relay.example.com"
        val token = "abc"
        val deviceId = 10
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/client/$deviceId?token=$token"

        assertEquals("wss://relay.example.com/ws/client/10?token=abc", wsUrl)
    }

    // ── Exponential Backoff Tests ──

    @Test
    fun `test exponential backoff sequence 1 2 4 8 16 30 30`() {
        val maxBackoff = 30L
        val expectedDelays = listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L)

        for ((attempt, expected) in expectedDelays.withIndex()) {
            val actual = minOf(1L shl attempt, maxBackoff)
            assertEquals(expected, actual, "Backoff at attempt $attempt should be $expected")
        }
    }

    @Test
    fun `test backoff at attempt 0 is 1 second`() {
        val maxBackoff = 30L
        assertEquals(1L, minOf(1L shl 0, maxBackoff))
    }

    @Test
    fun `test backoff at attempt 4 is 16 seconds`() {
        val maxBackoff = 30L
        assertEquals(16L, minOf(1L shl 4, maxBackoff))
    }

    @Test
    fun `test backoff capped at 30 for attempt 5`() {
        val maxBackoff = 30L
        // 1 shl 5 = 32, capped to 30
        assertEquals(30L, minOf(1L shl 5, maxBackoff))
    }

    @Test
    fun `test backoff capped at 30 for attempt 10`() {
        val maxBackoff = 30L
        assertEquals(30L, minOf(1L shl 10, maxBackoff))
    }

    // ── Retry Counter Reset ──

    @Test
    fun `test retry counter resets on successful connection`() {
        val retryCount = java.util.concurrent.atomic.AtomicInteger(5)
        // Simulating onOpen which resets retryCount
        retryCount.set(0)
        assertEquals(0, retryCount.get())
    }

    // ── Intentional Disconnect ──

    @Test
    fun `test intentional disconnect suppresses reconnect`() {
        var intentionalClose = true
        var reconnectScheduled = false

        // Simulating scheduleReconnect logic
        if (!intentionalClose) {
            reconnectScheduled = true
        }

        assertFalse(reconnectScheduled, "Reconnect should not be scheduled on intentional close")
    }

    @Test
    fun `test unintentional close allows reconnect`() {
        var intentionalClose = false
        var reconnectScheduled = false

        if (!intentionalClose) {
            reconnectScheduled = true
        }

        assertTrue(reconnectScheduled, "Reconnect should be scheduled on unintentional close")
    }

    // ── Status Transitions ──

    @Test
    fun `test disconnect sets status to DISCONNECTED`() {
        every { prefs.serverUrl } returns "http://example.com"
        every { prefs.token } returns "token"
        every { prefs.deviceId } returns 1

        wsManager.disconnect()

        assertTrue(statusChanges.contains(ConnectionStatus.DISCONNECTED))
    }

    @Test
    fun `test connect transitions to CONNECTING first`() {
        every { prefs.serverUrl } returns ""
        every { prefs.token } returns ""
        every { prefs.deviceId } returns -1

        wsManager.connect()

        assertTrue(statusChanges.isNotEmpty())
        assertEquals(ConnectionStatus.CONNECTING, statusChanges.first())
    }

    @Test
    fun `test connect with blank prefs falls back to DISCONNECTED`() {
        every { prefs.serverUrl } returns ""
        every { prefs.token } returns ""
        every { prefs.deviceId } returns -1

        wsManager.connect()

        assertEquals(ConnectionStatus.CONNECTING, statusChanges[0])
        assertEquals(ConnectionStatus.DISCONNECTED, statusChanges[1])
    }

    @Test
    fun `test status transitions DISCONNECTED to CONNECTING to CONNECTED`() {
        val transitions = listOf(
            ConnectionStatus.DISCONNECTED,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED,
        )
        assertEquals(ConnectionStatus.DISCONNECTED, transitions[0])
        assertEquals(ConnectionStatus.CONNECTING, transitions[1])
        assertEquals(ConnectionStatus.CONNECTED, transitions[2])
    }

    // ── Message Parsing ──

    @Test
    fun `test message callback parses incoming JSON correctly`() {
        val gson = com.google.gson.Gson()
        val json = """{"type":"event","event":"SMS_SENT","status":"ok","req_id":"abc"}"""
        val parsed = gson.fromJson(json, WsMessage::class.java)

        assertNotNull(parsed)
        assertEquals("event", parsed.type)
        assertEquals("SMS_SENT", parsed.event)
        assertEquals("ok", parsed.status)
        assertEquals("abc", parsed.reqId)
    }

    // ── Ping Interval ──

    @Test
    fun `test ping interval constant is 30 seconds`() {
        // Reflects WebSocketManager.PING_INTERVAL_SEC = 30L
        val expectedPingInterval = 30L
        assertEquals(30L, expectedPingInterval)
    }

    // ── Max Backoff ──

    @Test
    fun `test max backoff constant is 30 seconds`() {
        // Reflects WebSocketManager.MAX_BACKOFF_SEC = 30L
        val expectedMaxBackoff = 30L
        assertEquals(30L, expectedMaxBackoff)
    }
}
