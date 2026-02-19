package com.simbridge.host.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.simbridge.host.data.WsMessage

class SmsHandler(
    private val context: Context,
    private val simInfoProvider: SimInfoProvider,
    private val sendEvent: (WsMessage) -> Unit,
) {

    companion object {
        private const val TAG = "SmsHandler"
        const val ACTION_SMS_SENT = "com.simbridge.host.SMS_SENT"
    }

    /**
     * Sends an SMS to the specified number using the given SIM slot.
     * Reports success/failure as a WS event.
     */
    fun sendSms(to: String, body: String, simSlot: Int?, reqId: String?) {
        try {
            // H-10: Error if requested SIM slot doesn't exist instead of silent fallback
            if (simSlot != null && simInfoProvider.getSubscriptionForSlot(simSlot) == null) {
                sendEvent(
                    WsMessage(
                        type = "event",
                        event = "SMS_SENT",
                        status = "error",
                        body = "SIM slot $simSlot not available",
                        reqId = reqId,
                    )
                )
                return
            }
            val smsManager = getSmsManagerForSlot(simSlot)
            val sentIntent = PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_SMS_SENT).apply { putExtra("req_id", reqId) },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Split long messages
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, body, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) { sentIntents.add(sentIntent) }
                smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, null)
            }

            Log.i(TAG, "SMS sent to $to via SIM slot ${simSlot ?: "default"}")
            sendEvent(
                WsMessage(
                    type = "event",
                    event = "SMS_SENT",
                    status = "ok",
                    reqId = reqId,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $to", e)
            sendEvent(
                WsMessage(
                    type = "event",
                    event = "SMS_SENT",
                    status = "error",
                    body = e.message,
                    reqId = reqId,
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManagerForSlot(simSlot: Int?): SmsManager {
        if (simSlot == null) {
            return SmsManager.getDefault()
        }
        val subscription = simInfoProvider.getSubscriptionForSlot(simSlot)
        return if (subscription != null) {
            SmsManager.getSmsManagerForSubscriptionId(subscription.subscriptionId)
        } else {
            Log.w(TAG, "SIM slot $simSlot not found, using default")
            SmsManager.getDefault()
        }
    }
}
