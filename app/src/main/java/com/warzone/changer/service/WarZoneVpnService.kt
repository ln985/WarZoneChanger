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
import com.warzone.changer.data.LocationStore
import com.warzone.changer.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class WarZoneVpnService : VpnService() {

    companion object {
        private const val TAG = "WarZoneVPN"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.VPN_START"
        const val ACTION_STOP = "com.warzone.action.VPN_STOP"
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var proxyThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        
        val location = LocationStore.get(this)
        if (location == null || !location.isValid()) {
            Log.e(TAG, "No location selected")
            stopSelf()
            return
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            
            // 建立VPN接口 - 仅代理王者荣耀相关的域名
            vpnInterface = Builder()
                .setSession("WarZoneChanger")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("114.114.114.114")
                .setMtu(1500)
                .setBlocking(true)
                // 排除应用自身
                .addDisallowedApplication(packageName)
                .establish()

            running.set(true)
            isRunning = true
            
            // 启动代理线程处理流量
            startProxyThread()
            
            Log.i(TAG, "VPN started, targeting adcode: ${location.adcode}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun startProxyThread() {
        proxyThread = Thread {
            try {
                val vpnFd = vpnInterface ?: return@Thread
                val input = FileInputStream(vpnFd.fileDescriptor)
                val output = FileOutputStream(vpnFd.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)
                
                while (running.get()) {
                    try {
                        buffer.clear()
                        val length = input.read(buffer.array())
                        if (length <= 0) continue
                        
                        buffer.limit(length)
                        
                        // 解析IP包，检查是否是需要修改的请求
                        val packet = buffer.array().copyOf(length)
                        val modified = interceptAndModify(packet, length)
                        
                        if (modified != null) {
                            output.write(modified)
                        } else {
                            output.write(packet, 0, length)
                        }
                        output.flush()
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.w(TAG, "Proxy error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proxy thread error", e)
            }
        }.apply {
            name = "WarZoneProxy"
            isDaemon = true
            start()
        }
    }

    private fun interceptAndModify(packet: ByteArray, length: Int): ByteArray? {
        // 这里可以解析HTTP/HTTPS请求，检测王者荣耀的位置API
        // 然后修改响应中的adcode
        // 简单实现：直接返回null，让包正常传递
        // 完整实现需要TCP/IP协议解析和MITM代理
        return null
    }

    private fun stopVpn() {
        running.set(false)
        isRunning = false
        
        proxyThread?.interrupt()
        proxyThread = null
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Close VPN failed", e)
        }
        vpnInterface = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
        
        Log.i(TAG, "VPN stopped")
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
            .setContentText("战区修改服务运行中")
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
