package com.warzone.changer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_ID = "vpn_service"
        lateinit var instance: App
            private set
        // 服务端地址 - 修改为你的域名
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
                "VPN代理服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "战区修改器VPN代理运行通知"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}