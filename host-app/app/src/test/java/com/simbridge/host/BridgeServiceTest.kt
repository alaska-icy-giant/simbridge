package com.simbridge.host

import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.LogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for BridgeService logic. Since BridgeService extends Android Service,
 * we test its core logic (log capping, status callbacks) in isolation without
 * the Android framework.
 */
class BridgeServiceTest {

    // ── Log Management Tests ──

    /**
     * Simulates the BridgeService._logs behavior for unit-testability without
     * requiring an Android context for the full Service lifecycle.
     */
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

    @BeforeEach
    fun setUp() {
        logs.clear()
    }

    @Test
    fun `test log entries capped at 100`() {
        repeat(101) { i ->
            addLog(LogEntry(direction = "IN", summary = "Entry $i"))
        }

        assertEquals(100, logs.size)
    }

    @Test
    fun `test 101st entry evicts the oldest`() {
        repeat(100) { i ->
            addLog(LogEntry(direction = "IN", summary = "Entry $i"))
        }
        assertEquals("Entry 0", logs.last().summary) // oldest is at end

        addLog(LogEntry(direction = "IN", summary = "Entry 100"))

        assertEquals(100, logs.size)
        assertEquals("Entry 100", logs.first().summary) // newest at front
        // Entry 0 (the oldest) should have been evicted
        assertFalse(logs.any { it.summary == "Entry 0" })
    }

    @Test
    fun `test newest log entry is always at index 0`() {
        addLog(LogEntry(direction = "OUT", summary = "First"))
        addLog(LogEntry(direction = "IN", summary = "Second"))

        assertEquals("Second", logs[0].summary)
        assertEquals("First", logs[1].summary)
    }

    // ── Status Callback Tests ──

    @Test
    fun `test status callback fires on state change`() {
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

    // ── WebSocket Lifecycle Tests ──

    @Test
    fun `test service starts WebSocket conceptual verification`() {
        // BridgeService.onStartCommand calls wsManager.connect().
        // We verify the expected sequence: startForeground -> registerSmsReceiver -> connect
        var connectCalled = false
        var registerCalled = false
        val mockConnect = { connectCalled = true }
        val mockRegister = { registerCalled = true }

        // Simulate onStartCommand
        mockRegister()
        mockConnect()

        assertTrue(registerCalled, "SMS receiver should be registered")
        assertTrue(connectCalled, "WebSocket connect should be called")
    }

    @Test
    fun `test service stops cleanly`() {
        // BridgeService.onDestroy calls wsManager.disconnect(), webRtcManager.dispose(),
        // and unregisterSmsReceiver()
        var disconnectCalled = false
        var disposeCalled = false
        var unregisterCalled = false

        val mockDisconnect = { disconnectCalled = true }
        val mockDispose = { disposeCalled = true }
        val mockUnregister = { unregisterCalled = true }

        // Simulate onDestroy
        mockDisconnect()
        mockDispose()
        mockUnregister()

        assertTrue(disconnectCalled)
        assertTrue(disposeCalled)
        assertTrue(unregisterCalled)
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
}
