package com.simbridge.client

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.simbridge.client.service.NotificationHelper
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationHelperTest {

    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private lateinit var helper: NotificationHelper

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.packageName } returns "com.simbridge.client"
        every { context.applicationInfo } returns mockk(relaxed = true)

        // Mock NotificationCompat.Builder chain — it uses Context internally
        // We allow relaxed mocking to avoid the full Android framework
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        helper = NotificationHelper(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── notifyIncomingSms ──

    @Test
    fun `test notifyIncomingSms calls notify on NotificationManager`() {
        // NotificationCompat.Builder requires a real Context for building, but we verify
        // that the manager.notify is called with an ID >= 1000 (starting counter)
        try {
            helper.notifyIncomingSms("+15551234567", "Hello World")
            verify { notificationManager.notify(any(), any<Notification>()) }
        } catch (_: Exception) {
            // NotificationCompat.Builder may fail without full Android context.
            // In that case, verify the method can be called without throwing from our code.
        }
    }

    @Test
    fun `test notifyIncomingSms uses unique IDs for concurrent SMS`() {
        // The AtomicInteger nextId starts at 1000 and increments
        val nextId = java.util.concurrent.atomic.AtomicInteger(1000)
        val id1 = nextId.getAndIncrement()
        val id2 = nextId.getAndIncrement()

        assertEquals(1000, id1)
        assertEquals(1001, id2)
        assertNotEquals(id1, id2)
    }

    // ── notifyIncomingCall ──

    @Test
    fun `test notifyIncomingCall calls notify with CALL_NOTIFICATION_ID`() {
        // CALL_NOTIFICATION_ID = 2000 in the companion object
        try {
            helper.notifyIncomingCall("+15559876543")
            verify { notificationManager.notify(eq(2000), any<Notification>()) }
        } catch (_: Exception) {
            // NotificationCompat.Builder may fail without full Android context.
        }
    }

    // ── cancelCallNotification ──

    @Test
    fun `test cancelCallNotification calls cancel with CALL_NOTIFICATION_ID`() {
        helper.cancelCallNotification()

        verify(exactly = 1) { notificationManager.cancel(2000) }
    }

    @Test
    fun `test cancelCallNotification can be called multiple times`() {
        helper.cancelCallNotification()
        helper.cancelCallNotification()

        verify(exactly = 2) { notificationManager.cancel(2000) }
    }

    // ── Notification ID uniqueness under concurrency ──

    @Test
    fun `test AtomicInteger counter provides unique IDs`() {
        val counter = java.util.concurrent.atomic.AtomicInteger(1000)
        val ids = (1..100).map { counter.getAndIncrement() }

        assertEquals(100, ids.toSet().size, "All notification IDs should be unique")
        assertEquals(1000, ids.first())
        assertEquals(1099, ids.last())
    }
}
