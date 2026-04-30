package com.warzone.changer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_ID = "warzone_vpn_service"
        lateinit var instance: App; private set
        const val API_BASE = "https://lnzdy.xf79.cn/api"
    }
    override fun onCreate() { super.onCreate(); instance = this; createNotificationChannel() }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "战区修改服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "VPN代理服务通知"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
