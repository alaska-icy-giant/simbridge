package com.simbridge.host.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.simbridge.host.data.WsMessage

class CallHandler(
    private val context: Context,
    private val simInfoProvider: SimInfoProvider,
    private val sendEvent: (WsMessage) -> Unit,
) {

    companion object {
        private const val TAG = "CallHandler"
    }

    /**
     * Places an outgoing call to the specified number using the given SIM slot.
     */
    fun makeCall(to: String, simSlot: Int?, reqId: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "CALL_PHONE permission not granted")
            sendEvent(
                WsMessage(
                    type = "event",
                    event = "CALL_STATE",
                    state = "error",
                    body = "CALL_PHONE permission not granted",
                    reqId = reqId,
                )
            )
            return
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", to, null)
            val extras = Bundle()

            // Set the phone account for SIM selection
            val phoneAccount = getPhoneAccountForSlot(simSlot)
            if (phoneAccount != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
            }

            telecomManager.placeCall(uri, extras)
            Log.i(TAG, "Call placed to $to via SIM slot ${simSlot ?: "default"}")

            sendEvent(
                WsMessage(
                    type = "event",
                    event = "CALL_STATE",
                    state = "dialing",
                    sim = simSlot,
                    reqId = reqId,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place call to $to", e)
            sendEvent(
                WsMessage(
                    type = "event",
                    event = "CALL_STATE",
                    state = "error",
                    body = e.message,
                    reqId = reqId,
                )
            )
        }
    }

    /**
     * Ends the current active call.
     */
    @Suppress("DEPRECATION")
    fun hangUp(reqId: String?) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                telecomManager.endCall()
            }
            sendEvent(
                WsMessage(
                    type = "event",
                    event = "CALL_STATE",
                    state = "ended",
                    reqId = reqId,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hang up", e)
        }
    }

    private fun getPhoneAccountForSlot(simSlot: Int?): PhoneAccountHandle? {
        if (simSlot == null) return null

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) return null

            val subscriptionInfo = simInfoProvider.getSubscriptionForSlot(simSlot) ?: return null
            val subId = subscriptionInfo.subscriptionId.toString()
            val accounts = telecomManager.callCapablePhoneAccounts

            // Match phone account by subscription ID embedded in the account handle ID
            for (account in accounts) {
                if (account.id.contains(subId)) {
                    return account
                }
            }

            // Fallback: try index-based if subscription ID matching fails
            Log.w(TAG, "No PhoneAccount matched subId $subId, falling back to index")
            return accounts.getOrNull(simSlot - 1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone account for slot $simSlot", e)
            return null
        }
    }
}
