package com.simbridge.client.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simbridge.client.MainActivity
import com.simbridge.client.R
import com.simbridge.client.SimBridgeClientApp

/**
 * Builds notifications for incoming SMS and calls.
 */
class NotificationHelper(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var nextId = 1000

    fun notifyIncomingSms(from: String, body: String) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "sms")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, SimBridgeClientApp.CHANNEL_CALL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SMS from $from")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        manager.notify(nextId++, notification)
    }

    fun notifyIncomingCall(from: String) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "call")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, SimBridgeClientApp.CHANNEL_CALL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming Call")
            .setContentText(from)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()

        manager.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification() {
        manager.cancel(CALL_NOTIFICATION_ID)
    }

    companion object {
        private const val CALL_NOTIFICATION_ID = 2000
    }
}
