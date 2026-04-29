package com.warzone.changer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_ID = "location_spoof_service"
        lateinit var instance: App
            private set
        const val API_BASE = "https://lnzdy.xf79.cn/api"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Mock Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WarZone Changer GPS mock location notification"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
