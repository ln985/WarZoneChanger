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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class WarZoneVpnService : VpnService() {
    companion object {
        private const val TAG = "WarZoneVPN"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.START"
        const val ACTION_STOP = "com.warzone.action.STOP"
        var isRunning = false; private set
        
        // 只代理这些IP（apis.map.qq.com）
        private val PROXY_IPS = setOf(
            "101.91.33.219",
            "180.153.202.73"
        )
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            else -> startVpn()
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        if (isRunning) return
        val loc = LocationStore.get(this)
        if (loc == null || !loc.isValid()) {
            Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show()
            stopSelf(); return
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            vpnInterface = Builder()
                .setSession("WarZoneChanger")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("114.114.114.114")
                .setMtu(1500)
                .setBlocking(true)
                .addDisallowedApplication(packageName)
                .establish() ?: throw Exception("VPN establish returned null")
            
            running.set(true)
            isRunning = true
            Log.i(TAG, "VPN started, adcode=${loc.adcode}")
            
            workerThread = Thread({ handleTraffic() }, "vpn-packet-pump").apply { start() }
        } catch (e: Exception) {
            Log.e(TAG, "startVpn failed", e); stopVpn()
        }
    }
    
    private fun handleTraffic() {
        try {
            val fd = vpnInterface ?: return
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val buf = ByteBuffer.allocate(65535)
            
            while (running.get()) {
                try {
                    buf.clear()
                    val n = input.read(buf.array())
                    if (n <= 0) { Thread.sleep(5); continue }
                    buf.limit(n)
                    val pkt = buf.array().copyOf(n)
                    
                    // 解析IP包
                    val ipVersion = (pkt[0].toInt() and 0xF0) shr 4
                    if (ipVersion != 4) { output.write(pkt); output.flush(); continue }
                    
                    val ipHeaderLen = (pkt[0].toInt() and 0x0F) * 4
                    val protocol = pkt[9].toInt() and 0xFF
                    
                    // 获取目标IP
                    val destIp = "${pkt[16].toInt() and 0xFF}.${pkt[17].toInt() and 0xFF}.${pkt[18].toInt() and 0xFF}.${pkt[19].toInt() and 0xFF}"
                    
                    // 只代理目标IP在PROXY_IPS中的TCP流量
                    if (protocol == 6 && destIp in PROXY_IPS) {
                        // TCP协议，需要代理
                        Log.d(TAG, "Proxy TCP to $destIp")
                        // TODO: 实现TCP代理和HTTP拦截
                        // 暂时直接转发
                        output.write(pkt)
                    } else {
                        // 其他流量直接转发
                        output.write(pkt)
                    }
                    output.flush()
                } catch (e: Exception) {
                    if (running.get()) Log.w(TAG, "Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Traffic handler error", e)
        }
    }
    
    private fun stopVpn() {
        running.set(false); isRunning = false
        workerThread?.interrupt(); workerThread = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }
    
    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, App.CHANNEL_ID)
                else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("流年改战区").setContentText("战区修改服务运行中")
            .setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).build()
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
