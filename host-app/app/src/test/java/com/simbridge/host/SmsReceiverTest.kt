package com.simbridge.host

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import com.simbridge.host.data.WsMessage
import com.simbridge.host.service.SmsReceiver
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val receivedEvents = mutableListOf<WsMessage>()
    private lateinit var smsReceiver: SmsReceiver

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        receivedEvents.clear()
        smsReceiver = SmsReceiver()
        smsReceiver.onSmsReceived = { receivedEvents.add(it) }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── Incoming SMS Parsing ──

    @Test
    fun `test incoming SMS parsed from intent`() {
        val smsMessage = mockk<SmsMessage>(relaxed = true)
        every { smsMessage.displayOriginatingAddress } returns "+15551234567"
        every { smsMessage.displayMessageBody } returns "Hello from test"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION

        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.getInt("slot", -1) } returns -1
        every { bundle.getInt("subscription", -1) } returns -1
        every { intent.extras } returns bundle

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(smsMessage)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, receivedEvents.size)
        val event = receivedEvents[0]
        assertEquals("event", event.type)
        assertEquals("INCOMING_SMS", event.event)
        assertEquals("+15551234567", event.from)
        assertEquals("Hello from test", event.body)
    }

    @Test
    fun `test multi-part SMS bodies are concatenated`() {
        val part1 = mockk<SmsMessage>(relaxed = true)
        every { part1.displayOriginatingAddress } returns "+15559876543"
        every { part1.displayMessageBody } returns "Part one "

        val part2 = mockk<SmsMessage>(relaxed = true)
        every { part2.displayOriginatingAddress } returns "+15559876543"
        every { part2.displayMessageBody } returns "part two"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        every { intent.extras } returns mockk<Bundle>(relaxed = true).apply {
            every { getInt("slot", -1) } returns -1
            every { getInt("subscription", -1) } returns -1
        }

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(part1, part2)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, receivedEvents.size)
        assertEquals("Part one part two", receivedEvents[0].body)
    }

    // ── Event Callback ──

    @Test
    fun `test INCOMING_SMS event sent via callback`() {
        val smsMessage = mockk<SmsMessage>(relaxed = true)
        every { smsMessage.displayOriginatingAddress } returns "+15551111111"
        every { smsMessage.displayMessageBody } returns "Test"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        every { intent.extras } returns mockk<Bundle>(relaxed = true).apply {
            every { getInt("slot", -1) } returns -1
            every { getInt("subscription", -1) } returns -1
        }

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(smsMessage)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, receivedEvents.size)
        assertEquals("INCOMING_SMS", receivedEvents[0].event)
        assertEquals("event", receivedEvents[0].type)
    }

    @Test
    fun `test no callback when onSmsReceived is null`() {
        smsReceiver.onSmsReceived = null

        val smsMessage = mockk<SmsMessage>(relaxed = true)
        every { smsMessage.displayOriginatingAddress } returns "+15552222222"
        every { smsMessage.displayMessageBody } returns "No callback"

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        every { intent.extras } returns mockk<Bundle>(relaxed = true).apply {
            every { getInt("slot", -1) } returns -1
            every { getInt("subscription", -1) } returns -1
        }

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(smsMessage)

        // Should not throw even without callback
        assertDoesNotThrow { smsReceiver.onReceive(context, intent) }
    }

    // ── SIM Slot Extraction ──

    @Test
    fun `test SIM slot extracted from intent extras`() {
        val smsMessage = mockk<SmsMessage>(relaxed = true)
        every { smsMessage.displayOriginatingAddress } returns "+15553333333"
        every { smsMessage.displayMessageBody } returns "SIM test"

        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.getInt("slot", -1) } returns 1  // 0-indexed slot 1
        every { bundle.getInt("subscription", -1) } returns -1

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        every { intent.extras } returns bundle

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(smsMessage)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, receivedEvents.size)
        // slot 1 (0-indexed) + 1 = 2 (1-indexed)
        assertEquals(2, receivedEvents[0].sim)
    }

    @Test
    fun `test SIM slot defaults to 1 when not in extras`() {
        val smsMessage = mockk<SmsMessage>(relaxed = true)
        every { smsMessage.displayOriginatingAddress } returns "+15554444444"
        every { smsMessage.displayMessageBody } returns "Default slot"

        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.getInt("slot", -1) } returns -1  // no slot info
        every { bundle.getInt("subscription", -1) } returns -1

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        every { intent.extras } returns bundle

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns arrayOf(smsMessage)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, receivedEvents.size)
        assertEquals(1, receivedEvents[0].sim)  // default is 1
    }

    // ── Wrong Action Ignored ──

    @Test
    fun `test wrong intent action is ignored`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns "com.example.WRONG_ACTION"

        smsReceiver.onReceive(context, intent)

        assertTrue(receivedEvents.isEmpty())
    }

    // ── Empty Messages Ignored ──

    @Test
    fun `test empty messages array is ignored`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns emptyArray()

        smsReceiver.onReceive(context, intent)

        assertTrue(receivedEvents.isEmpty())
    }

    @Test
    fun `test null messages array is ignored`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION

        mockkStatic(Telephony.Sms.Intents::class)
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } returns null

        smsReceiver.onReceive(context, intent)

        assertTrue(receivedEvents.isEmpty())
    }
}
