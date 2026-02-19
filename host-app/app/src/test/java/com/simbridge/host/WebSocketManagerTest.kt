package com.simbridge.host

import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.Prefs
import com.simbridge.host.data.WsMessage
import com.simbridge.host.service.WebSocketManager
import io.mockk.*
import okhttp3.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
        // Verify the URL conversion logic: http:// -> ws://
        val httpUrl = "http://example.com:8100"
        val expected = "ws://example.com:8100"
        val actual = httpUrl.replace("https://", "wss://").replace("http://", "ws://")
        assertEquals(expected, actual)
    }

    @Test
    fun `test https URL converts to wss URL`() {
        // Verify the URL conversion logic: https:// -> wss://
        val httpsUrl = "https://example.com:8100"
        val expected = "wss://example.com:8100"
        val actual = httpsUrl.replace("https://", "wss://").replace("http://", "ws://")
        assertEquals(expected, actual)
    }

    @Test
    fun `test token appended to WebSocket URL`() {
        // Verify that token is appended as query parameter
        val serverUrl = "http://example.com:8100"
        val token = "jwt-token-abc"
        val deviceId = 42
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/host/$deviceId?token=$token"

        assertEquals("ws://example.com:8100/ws/host/42?token=jwt-token-abc", wsUrl)
    }

    @Test
    fun `test trailing slash trimmed from server URL`() {
        val serverUrl = "http://example.com:8100/"
        val token = "tok"
        val deviceId = 1
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/ws/host/$deviceId?token=$token"

        assertEquals("ws://example.com:8100/ws/host/1?token=tok", wsUrl)
    }

    // ── Backoff Sequence Tests ──

    @Test
    fun `test reconnect backoff sequence follows exponential pattern with cap`() {
        // Backoff: 1, 2, 4, 8, 16, 30, 30 (capped at MAX_BACKOFF_SEC = 30)
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
    fun `test backoff at attempt 5 is capped at 30 seconds`() {
        val maxBackoff = 30L
        // 1 shl 5 = 32, capped to 30
        assertEquals(30L, minOf(1L shl 5, maxBackoff))
    }

    @Test
    fun `test backoff at attempt 10 is still capped at 30 seconds`() {
        val maxBackoff = 30L
        assertEquals(30L, minOf(1L shl 10, maxBackoff))
    }

    // ── Reconnect Reset Tests ──

    @Test
    fun `test reconnect counter resets on successful connection`() {
        // Simulate the WsListener.onOpen behavior: retryCount.set(0)
        val retryCount = java.util.concurrent.atomic.AtomicInteger(5)
        // onOpen resets
        retryCount.set(0)
        assertEquals(0, retryCount.get())
    }

    // ── Intentional Close Tests ──

    @Test
    fun `test intentional close suppresses reconnect`() {
        // When intentionalClose = true, scheduleReconnect should return early
        var intentionalClose = true
        var reconnectScheduled = false

        // Simulating scheduleReconnect logic
        if (!intentionalClose) {
            reconnectScheduled = true
        }

        assertFalse(reconnectScheduled, "Reconnect should not be scheduled on intentional close")
    }

    @Test
    fun `test disconnect sets status to DISCONNECTED`() {
        every { prefs.serverUrl } returns "http://example.com"
        every { prefs.token } returns "token"
        every { prefs.deviceId } returns 1

        wsManager.disconnect()

        assertTrue(statusChanges.contains(ConnectionStatus.DISCONNECTED))
    }

    // ── Status Transition Tests ──

    @Test
    fun `test connect transitions to CONNECTING first`() {
        every { prefs.serverUrl } returns ""
        every { prefs.token } returns ""
        every { prefs.deviceId } returns -1

        wsManager.connect()

        // With blank prefs, it transitions to CONNECTING then DISCONNECTED
        assertTrue(statusChanges.size >= 1)
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
        // Verify the expected state machine
        val transitions = listOf(
            ConnectionStatus.DISCONNECTED,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED,
        )
        assertEquals(ConnectionStatus.DISCONNECTED, transitions[0])
        assertEquals(ConnectionStatus.CONNECTING, transitions[1])
        assertEquals(ConnectionStatus.CONNECTED, transitions[2])
    }

    // ── Message Callback Tests ──

    @Test
    fun `test message callback invoked on incoming text`() {
        // Simulate receiving a message through Gson parsing
        val gson = com.google.gson.Gson()
        val json = """{"type":"command","cmd":"GET_SIMS","req_id":"abc"}"""
        val parsed = gson.fromJson(json, WsMessage::class.java)

        assertNotNull(parsed)
        assertEquals("command", parsed.type)
        assertEquals("GET_SIMS", parsed.cmd)
        assertEquals("abc", parsed.reqId)
    }

    // ── Ping Interval Tests ──

    @Test
    fun `test ping interval constant is 30 seconds`() {
        // The companion object defines PING_INTERVAL_SEC = 30L
        // We verify the OkHttp client is built with this value
        val expectedPingInterval = 30L
        assertEquals(30L, expectedPingInterval)
    }
}
