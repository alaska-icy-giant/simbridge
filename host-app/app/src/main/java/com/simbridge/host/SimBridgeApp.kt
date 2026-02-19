package com.simbridge.host

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SimBridgeApp : Application() {

    companion object {
        const val CHANNEL_ID = "simbridge_service"
        const val CHANNEL_NAME = "SimBridge Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SimBridge connected to the relay server"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
