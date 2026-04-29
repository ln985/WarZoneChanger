package com.warzone.changer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.github.megatronking.netbare.NetBare
import com.github.megatronking.netbare.NetBareConfig
import com.github.megatronking.netbare.NetBareListener
import com.github.megatronking.netbare.http.HttpInterceptorFactory
import com.warzone.changer.App
import com.warzone.changer.R
import com.warzone.changer.injector.LocationInjector
import com.warzone.changer.ui.MainActivity
import java.util.Collections

class VpnProxyService : VpnService(), NetBareListener {

    companion object {
        private const val TAG = "VpnProxyService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.START"
        const val ACTION_STOP = "com.warzone.action.STOP"
        var isRunning = false
            private set
    }

    private var netBare: NetBare? = null

    override fun onCreate() {
        super.onCreate()
        netBare = NetBare.get()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            val config = NetBareConfig.Builder()
                .addInterceptorFactory(HttpInterceptorFactory { LocationInjector(applicationContext) })
                .build()
            netBare?.start(config)
            isRunning = true
            Log.i(TAG, "VPN代理已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动VPN失败", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            netBare?.stop()
            isRunning = false
            Log.i(TAG, "VPN代理已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止VPN失败", e)
        }
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, App.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("战区修改器")
            .setContentText("VPN代理运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onServiceStarted() {
        Log.i(TAG, "NetBare service started")
    }

    override fun onServiceStopped() {
        isRunning = false
        Log.i(TAG, "NetBare service stopped")
    }
}