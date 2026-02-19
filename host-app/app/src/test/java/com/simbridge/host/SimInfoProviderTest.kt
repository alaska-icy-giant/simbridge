package com.simbridge.host

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import com.simbridge.host.service.SimInfoProvider
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimInfoProviderTest {

    private val context = mockk<Context>(relaxed = true)
    private val subscriptionManager = mockk<SubscriptionManager>(relaxed = true)

    private lateinit var simInfoProvider: SimInfoProvider

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(androidx.core.content.ContextCompat::class)
        every {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        } returns subscriptionManager

        simInfoProvider = SimInfoProvider(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── Returns Active SIMs ──

    @Test
    fun `test returns active SIMs with correct slot carrier and number`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        val sub1 = mockk<SubscriptionInfo>(relaxed = true).apply {
            every { simSlotIndex } returns 0  // 0-indexed
            every { carrierName } returns "T-Mobile"
        }
        val sub2 = mockk<SubscriptionInfo>(relaxed = true).apply {
            every { simSlotIndex } returns 1
            every { carrierName } returns "AT&T"
        }

        every { subscriptionManager.activeSubscriptionInfoList } returns listOf(sub1, sub2)

        val sims = simInfoProvider.getActiveSimCards()

        assertEquals(2, sims.size)
        assertEquals(1, sims[0].slot)  // 0-indexed + 1 = 1
        assertEquals("T-Mobile", sims[0].carrier)
        assertEquals(2, sims[1].slot)
        assertEquals("AT&T", sims[1].carrier)
    }

    @Test
    fun `test slot index is converted from 0-indexed to 1-indexed`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        val sub = mockk<SubscriptionInfo>(relaxed = true).apply {
            every { simSlotIndex } returns 0
            every { carrierName } returns "Verizon"
        }

        every { subscriptionManager.activeSubscriptionInfoList } returns listOf(sub)

        val sims = simInfoProvider.getActiveSimCards()

        assertEquals(1, sims[0].slot)
    }

    // ── Empty on No SIMs ──

    @Test
    fun `test returns empty list when no SIM cards present`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every { subscriptionManager.activeSubscriptionInfoList } returns emptyList()

        val sims = simInfoProvider.getActiveSimCards()

        assertTrue(sims.isEmpty())
    }

    @Test
    fun `test returns empty list when activeSubscriptionInfoList is null`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every { subscriptionManager.activeSubscriptionInfoList } returns null

        val sims = simInfoProvider.getActiveSimCards()

        assertTrue(sims.isEmpty())
    }

    // ── SecurityException Handling ──

    @Test
    fun `test returns empty list on SecurityException`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every {
            subscriptionManager.activeSubscriptionInfoList
        } throws SecurityException("Permission denied")

        val sims = simInfoProvider.getActiveSimCards()

        assertTrue(sims.isEmpty())
    }

    // ── Permission Denied ──

    @Test
    fun `test returns empty list when READ_PHONE_STATE permission denied`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_DENIED

        val sims = simInfoProvider.getActiveSimCards()

        assertTrue(sims.isEmpty())
        // SubscriptionManager should never be queried
        verify(exactly = 0) { subscriptionManager.activeSubscriptionInfoList }
    }

    // ── getSubscriptionForSlot ──

    @Test
    fun `test getSubscriptionForSlot returns correct subscription`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        val sub = mockk<SubscriptionInfo>(relaxed = true).apply {
            every { simSlotIndex } returns 0  // slot 1 in 1-indexed
        }
        every { subscriptionManager.activeSubscriptionInfoList } returns listOf(sub)

        val result = simInfoProvider.getSubscriptionForSlot(1) // 1-indexed

        assertNotNull(result)
        assertEquals(sub, result)
    }

    @Test
    fun `test getSubscriptionForSlot returns null for nonexistent slot`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        every { subscriptionManager.activeSubscriptionInfoList } returns emptyList()

        val result = simInfoProvider.getSubscriptionForSlot(3)

        assertNull(result)
    }

    @Test
    fun `test unknown carrier returns Unknown string`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            )
        } returns PackageManager.PERMISSION_GRANTED

        val sub = mockk<SubscriptionInfo>(relaxed = true).apply {
            every { simSlotIndex } returns 0
            every { carrierName } returns null
        }
        every { subscriptionManager.activeSubscriptionInfoList } returns listOf(sub)

        val sims = simInfoProvider.getActiveSimCards()

        assertEquals("Unknown", sims[0].carrier)
    }
}
