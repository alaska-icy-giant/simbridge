package com.simbridge.host

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.simbridge.host.data.WsMessage
import com.simbridge.host.service.CallHandler
import com.simbridge.host.service.SimInfoProvider
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CallHandlerTest {

    private val context = mockk<Context>(relaxed = true)
    private val simInfoProvider = mockk<SimInfoProvider>(relaxed = true)
    private val telecomManager = mockk<TelecomManager>(relaxed = true)
    private val sentEvents = mutableListOf<WsMessage>()

    private lateinit var callHandler: CallHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        mockkStatic(androidx.core.content.ContextCompat::class)
        mockkStatic(Uri::class)
        every { Uri.fromParts(any(), any(), any()) } returns mockk(relaxed = true)

        every {
            context.getSystemService(Context.TELECOM_SERVICE)
        } returns telecomManager

        sentEvents.clear()

        callHandler = CallHandler(
            context = context,
            simInfoProvider = simInfoProvider,
            sendEvent = { sentEvents.add(it) },
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── Make Call Success ──

    @Test
    fun `test makeCall places call via TelecomManager`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every { simInfoProvider.getSubscriptionForSlot(any()) } returns null

        callHandler.makeCall("+15551234567", null, "req-1")

        verify(exactly = 1) {
            telecomManager.placeCall(any(), any())
        }

        assertEquals(1, sentEvents.size)
        val event = sentEvents[0]
        assertEquals("event", event.type)
        assertEquals("CALL_STATE", event.event)
        assertEquals("dialing", event.state)
        assertEquals("req-1", event.reqId)
    }

    @Test
    fun `test makeCall sends dialing state event`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_GRANTED

        callHandler.makeCall("+15559876543", 1, "req-2")

        assertTrue(sentEvents.any { it.state == "dialing" })
    }

    // ── Hang Up ──

    @Test
    fun `test hangUp ends call and sends ended state`() {
        // In JVM unit tests, Build.VERSION.SDK_INT is 0 (< 28/P), so endCall()
        // is skipped due to the API level guard. The ended event is still sent.
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            )
        } returns PackageManager.PERMISSION_GRANTED

        callHandler.hangUp("req-3")

        assertEquals(1, sentEvents.size)
        assertEquals("CALL_STATE", sentEvents[0].event)
        assertEquals("ended", sentEvents[0].state)
        assertEquals("req-3", sentEvents[0].reqId)
    }

    @Test
    fun `test hangUp without ANSWER_PHONE_CALLS permission still sends ended event`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            )
        } returns PackageManager.PERMISSION_DENIED

        callHandler.hangUp("req-4")

        // endCall should not be called without permission
        @Suppress("DEPRECATION")
        verify(exactly = 0) { telecomManager.endCall() }

        // But ended event should still be sent
        assertEquals(1, sentEvents.size)
        assertEquals("ended", sentEvents[0].state)
    }

    // ── CALL_STATE Events ──

    @Test
    fun `test makeCall sends dialing CALL_STATE event`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_GRANTED

        callHandler.makeCall("+15551111111", null, "req-5")

        val event = sentEvents.first()
        assertEquals("CALL_STATE", event.event)
        assertEquals("dialing", event.state)
    }

    @Test
    fun `test hangUp sends ended CALL_STATE event`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            )
        } returns PackageManager.PERMISSION_GRANTED

        callHandler.hangUp("req-6")

        val event = sentEvents.first()
        assertEquals("CALL_STATE", event.event)
        assertEquals("ended", event.state)
    }

    // ── Permission Error ──

    @Test
    fun `test makeCall sends error on missing CALL_PHONE permission`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_DENIED

        callHandler.makeCall("+15552222222", null, "req-7")

        verify(exactly = 0) { telecomManager.placeCall(any(), any()) }

        assertEquals(1, sentEvents.size)
        val event = sentEvents[0]
        assertEquals("CALL_STATE", event.event)
        assertEquals("error", event.state)
        assertTrue(event.body!!.contains("permission"))
        assertEquals("req-7", event.reqId)
    }

    @Test
    fun `test makeCall error event preserves reqId`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_DENIED

        callHandler.makeCall("+15553333333", null, "custom-req-id")

        assertEquals("custom-req-id", sentEvents[0].reqId)
    }

    // ── Exception Handling ──

    @Test
    fun `test makeCall sends error event on TelecomManager exception`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every { telecomManager.placeCall(any(), any()) } throws RuntimeException("Telecom failure")

        callHandler.makeCall("+15554444444", null, "req-8")

        assertEquals(1, sentEvents.size)
        assertEquals("error", sentEvents[0].state)
        assertEquals("Telecom failure", sentEvents[0].body)
    }
}
