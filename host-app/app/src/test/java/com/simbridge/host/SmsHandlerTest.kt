package com.simbridge.host

import android.app.PendingIntent
import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import com.simbridge.host.data.WsMessage
import com.simbridge.host.service.SimInfoProvider
import com.simbridge.host.service.SmsHandler
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsHandlerTest {

    private val context = mockk<Context>(relaxed = true)
    private val simInfoProvider = mockk<SimInfoProvider>(relaxed = true)
    private val sentEvents = mutableListOf<WsMessage>()
    private val smsManager = mockk<SmsManager>(relaxed = true)

    private lateinit var smsHandler: SmsHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        mockkStatic(PendingIntent::class)
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        mockkStatic(SmsManager::class)
        every { SmsManager.getDefault() } returns smsManager

        sentEvents.clear()

        smsHandler = SmsHandler(
            context = context,
            simInfoProvider = simInfoProvider,
            sendEvent = { sentEvents.add(it) },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── Send SMS Success ──

    @Test
    fun `test sendSms calls SmsManager with correct arguments`() {
        every { smsManager.divideMessage("Hello") } returns arrayListOf("Hello")

        smsHandler.sendSms("+15551234567", "Hello", null, "req-1")

        verify(exactly = 1) {
            smsManager.sendTextMessage("+15551234567", null, "Hello", any(), null)
        }
        assertEquals(1, sentEvents.size)
        assertEquals("SMS_SENT", sentEvents[0].event)
        assertEquals("ok", sentEvents[0].status)
        assertEquals("req-1", sentEvents[0].reqId)
    }

    // ── SIM Selection ──

    @Test
    fun `test sendSms uses correct subscriptionId for SIM slot`() {
        val subInfo = mockk<SubscriptionInfo>(relaxed = true)
        every { subInfo.subscriptionId } returns 5
        every { simInfoProvider.getSubscriptionForSlot(2) } returns subInfo

        val simSpecificSmsManager = mockk<SmsManager>(relaxed = true)
        every { simSpecificSmsManager.divideMessage("Hi") } returns arrayListOf("Hi")

        @Suppress("DEPRECATION")
        mockkStatic(SmsManager::class)
        every { SmsManager.getSmsManagerForSubscriptionId(5) } returns simSpecificSmsManager

        smsHandler.sendSms("+15559876543", "Hi", 2, "req-2")

        verify(exactly = 1) {
            simSpecificSmsManager.sendTextMessage("+15559876543", null, "Hi", any(), null)
        }
    }

    @Test
    fun `test sendSms falls back to default when SIM slot not found`() {
        every { simInfoProvider.getSubscriptionForSlot(99) } returns null
        every { smsManager.divideMessage("Test") } returns arrayListOf("Test")

        smsHandler.sendSms("+15551111111", "Test", 99, "req-3")

        verify(exactly = 1) {
            smsManager.sendTextMessage("+15551111111", null, "Test", any(), null)
        }
    }

    // ── Multipart Messages ──

    @Test
    fun `test sendSms uses sendMultipartTextMessage for long messages`() {
        val longBody = "A".repeat(200) // Exceeds single SMS limit
        val parts = arrayListOf("Part1", "Part2")
        every { smsManager.divideMessage(longBody) } returns parts

        smsHandler.sendSms("+15552222222", longBody, null, "req-4")

        verify(exactly = 1) {
            smsManager.sendMultipartTextMessage(
                "+15552222222", null, parts, any(), null
            )
        }
        // Should NOT call single-part sendTextMessage
        verify(exactly = 0) {
            smsManager.sendTextMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `test sendSms multipart creates matching sentIntents list`() {
        val parts = arrayListOf("Part1", "Part2", "Part3")
        every { smsManager.divideMessage(any()) } returns parts

        smsHandler.sendSms("+15553333333", "long message", null, "req-5")

        verify {
            smsManager.sendMultipartTextMessage(
                "+15553333333", null, parts,
                match<ArrayList<PendingIntent>> { it.size == 3 },
                null
            )
        }
    }

    // ── Failure Event ──

    @Test
    fun `test sendSms sends error event on SmsManager failure`() {
        every { smsManager.divideMessage(any()) } throws RuntimeException("Radio not available")

        smsHandler.sendSms("+15554444444", "Hello", null, "req-6")

        assertEquals(1, sentEvents.size)
        val event = sentEvents[0]
        assertEquals("event", event.type)
        assertEquals("SMS_SENT", event.event)
        assertEquals("error", event.status)
        assertEquals("Radio not available", event.body)
        assertEquals("req-6", event.reqId)
    }

    @Test
    fun `test sendSms sends error event with reqId preserved`() {
        every { smsManager.divideMessage(any()) } throws IllegalStateException("No service")

        smsHandler.sendSms("+15555555555", "Fail", null, "req-preserve")

        assertEquals(1, sentEvents.size)
        assertEquals("req-preserve", sentEvents[0].reqId)
        assertEquals("error", sentEvents[0].status)
    }
}
