package com.simbridge.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SimBridgeClientApp : Application() {

    companion object {
        const val CHANNEL_ID = "simbridge_client"
        const val CHANNEL_NAME = "SimBridge Client"
        const val CHANNEL_CALL_ID = "simbridge_call"
        const val CHANNEL_CALL_NAME = "Incoming Calls"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SimBridge Client connection status"
                setShowBadge(false)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID, CHANNEL_CALL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call and SMS notifications"
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(callChannel)
        }
    }
}
