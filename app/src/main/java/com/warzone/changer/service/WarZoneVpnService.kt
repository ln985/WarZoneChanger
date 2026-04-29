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
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
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
            
            // 建立VPN接口
            vpnInterface = Builder()
                .setSession("WarZoneChanger")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)  // 代理所有流量
                .addDnsServer("8.8.8.8")
                .addDnsServer("114.114.114.114")
                .setMtu(1500)
                .setBlocking(true)
                .addDisallowedApplication(packageName)  // 排除自身
                .establish()

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface is null")
                stopSelf()
                return
            }

            running.set(true)
            isRunning = true
            
            Log.i(TAG, "VPN started, adcode: ${location.adcode}, location: ${location.getFormattedAddress()}")
            
            // 启动流量转发线程
            Thread({ handleTraffic() }, "VPN-Traffic").start()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun handleTraffic() {
        try {
            val fd = vpnInterface ?: return
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val buffer = ByteArray(32768)
            val location = LocationStore.get(this)
            
            while (running.get()) {
                try {
                    val len = input.read(buffer)
                    if (len <= 0) continue
                    
                    // 解析IP包
                    val packet = buffer.copyOf(len)
                    
                    // 检查是否是需要修改的域名的DNS请求或HTTP请求
                    val modified = processPacket(packet, len, location)
                    
                    if (modified != null) {
                        output.write(modified)
                    } else {
                        output.write(packet, 0, len)
                    }
                    output.flush()
                } catch (e: java.io.IOException) {
                    if (running.get()) {
                        Log.w(TAG, "IO error: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Packet processing error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Traffic handler error", e)
        }
    }

    /**
     * 处理单个网络包
     * 检测并修改位置API的响应
     */
    private fun processPacket(packet: ByteArray, length: Int, location: com.warzone.changer.model.SelectedLocation?): ByteArray? {
        if (location == null || length < 20) return null
        
        try {
            // IP头部解析
            val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
            val protocol = packet[9].toInt() and 0xFF
            
            // 只处理TCP协议 (protocol = 6)
            if (protocol != 6 || length < ipHeaderLen + 20) return null
            
            // TCP头部解析
            val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() and 0xF0) shr 4) * 4
            val payloadOffset = ipHeaderLen + tcpHeaderLen
            
            if (length <= payloadOffset) return null
            
            val payload = String(packet, payloadOffset, length - payloadOffset, Charsets.US_ASCII)
            
            // 检查是否是HTTP请求（包含Host头）
            if (payload.startsWith("GET ") || payload.startsWith("POST ") || payload.startsWith("HTTP/")) {
                // 检查是否包含需要拦截的域名
                if (payload.contains("lbs.qq.com") || 
                    payload.contains("hulubang.qq.com") ||
                    payload.contains("api.game.qq.com") ||
                    payload.contains("adcode") ||
                    payload.contains("province") ||
                    payload.contains("city_code")) {
                    
                    Log.d(TAG, "Intercepted location API request")
                    
                    // 如果是HTTP响应，修改响应体
                    if (payload.startsWith("HTTP/")) {
                        val modifiedPayload = modifyHttpPayload(payload, location)
                        if (modifiedPayload != null) {
                            val result = ByteArray(payloadOffset + modifiedPayload.length)
                            System.arraycopy(packet, 0, result, 0, payloadOffset)
                            modifiedPayload.toByteArray(Charsets.UTF_8).copyInto(result, payloadOffset)
                            return result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败，正常转发
        }
        
        return null
    }

    private fun modifyHttpPayload(payload: String, location: com.warzone.changer.model.SelectedLocation): String? {
        return try {
            com.warzone.changer.injector.LocationModifier.modifyResponse(payload, location)
        } catch (e: Exception) {
            null
        }
    }

    private fun stopVpn() {
        running.set(false)
        isRunning = false
        
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
