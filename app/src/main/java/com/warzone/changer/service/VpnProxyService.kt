package com.warzone.changer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.warzone.changer.App
import com.warzone.changer.R
import com.warzone.changer.ui.MainActivity

class VpnProxyService : VpnService() {

    companion object {
        private const val TAG = "VpnProxyService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.START"
        const val ACTION_STOP = "com.warzone.action.STOP"
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            vpnInterface = Builder()
                .setSession("WarZoneChanger")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
                .setBlocking(true)
                .establish()
            isRunning = true
            Log.i(TAG, "VPN proxy started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            isRunning = false
            Log.i(TAG, "VPN proxy stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN", e)
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
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("流年改战区工具")
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
}
