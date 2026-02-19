package com.simbridge.host.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.simbridge.host.data.WsMessage

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    var onSmsReceived: ((WsMessage) -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group message parts by sender (multi-part SMS)
        val grouped = mutableMapOf<String, StringBuilder>()
        var simSlot = 1

        for (sms: SmsMessage in messages) {
            val sender = sms.displayOriginatingAddress ?: "unknown"
            grouped.getOrPut(sender) { StringBuilder() }.append(sms.displayMessageBody)
        }

        // Try to determine SIM slot from intent extras
        val extras = intent.extras
        if (extras != null) {
            val slot = extras.getInt("slot", -1)
            if (slot >= 0) simSlot = slot + 1 // 0-indexed → 1-indexed
            val subId = extras.getInt("subscription", -1)
            if (subId >= 0) {
                // Could map subscriptionId → slot, but slot extra is simpler
            }
        }

        for ((sender, body) in grouped) {
            Log.i(TAG, "Incoming SMS from $sender on SIM $simSlot")
            val event = WsMessage(
                type = "event",
                event = "INCOMING_SMS",
                sim = simSlot,
                from = sender,
                body = body.toString(),
            )
            onSmsReceived?.invoke(event)
        }
    }
}
