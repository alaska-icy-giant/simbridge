package com.simbridge.host.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.simbridge.host.data.SimInfo

class SimInfoProvider(private val context: Context) {

    companion object {
        private const val TAG = "SimInfoProvider"
    }

    /**
     * Returns a list of active SIM cards with slot, carrier name, and phone number.
     */
    fun getActiveSimCards(): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted")
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()

        val activeSubscriptions: List<SubscriptionInfo> = try {
            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading subscriptions", e)
            emptyList()
        }

        return activeSubscriptions.map { info ->
            SimInfo(
                slot = info.simSlotIndex + 1, // 0-indexed → 1-indexed
                carrier = info.carrierName?.toString() ?: "Unknown",
                number = getPhoneNumber(info),
            )
        }
    }

    /**
     * Returns the SubscriptionInfo for a given 1-indexed SIM slot, or null.
     */
    fun getSubscriptionForSlot(simSlot: Int): SubscriptionInfo? {
        val sims = getActiveSubscriptions()
        return sims.find { it.simSlotIndex == simSlot - 1 }
    }

    fun getActiveSubscriptions(): List<SubscriptionInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        return try {
            sm.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun getPhoneNumber(info: SubscriptionInfo): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
                sm.getPhoneNumber(info.subscriptionId)
            } else {
                info.number
            }
        } catch (e: Exception) {
            null
        }
    }
}
