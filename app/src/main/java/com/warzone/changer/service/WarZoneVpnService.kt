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
import com.warzone.changer.vpn.LocalHttpProxy
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer

class WarZoneVpnService : VpnService() {
    companion object {
        private const val TAG = "WarZoneVPN"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.START"
        const val ACTION_STOP = "com.warzone.action.STOP"
        var isRunning = false; private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var httpProxy: LocalHttpProxy? = null
    @Volatile private var running = false

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
            android.widget.Toast.makeText(this, "请先选择位置", android.widget.Toast.LENGTH_SHORT).show()
            stopSelf(); return
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            
            // Start local HTTP proxy on a free port
            val proxy = LocalHttpProxy(18080)
            proxy.targetLocation = loc
            proxy.start()
            httpProxy = proxy
            
            // Create VPN that routes target domain traffic through our proxy
            vpnInterface = Builder()
                .setSession("WarZoneChanger")
                .addAddress("10.0.0.2", 32)
                .addRoute("101.91.33.219", 32)  // apis.map.qq.com
                .addRoute("180.153.202.73", 32) // apis.map.qq.com
                .addDnsServer("8.8.8.8")
                .addDnsServer("114.114.114.114")
                .setMtu(1500)
                .setBlocking(true)
                .addDisallowedApplication(packageName)
                .establish() ?: throw Exception("VPN establish returned null")
            
            running = true
            isRunning = true
            Log.i(TAG, "VPN started, adcode=${loc.adcode}")
            
            // Forward VPN traffic
            Thread({ forwardTraffic() }, "vpn-forward").start()
        } catch (e: Exception) {
            Log.e(TAG, "startVpn failed", e); stopVpn()
        }
    }

    private fun forwardTraffic() {
        try {
            val input = java.io.FileInputStream(vpnInterface!!.fileDescriptor)
            val output = java.io.FileOutputStream(vpnInterface!!.fileDescriptor)
            val buf = ByteBuffer.allocate(65535)
            
            while (running) {
                try {
                    buf.clear()
                    val n = input.read(buf.array())
                    if (n <= 0) { Thread.sleep(1); continue }
                    buf.limit(n)
                    val pkt = buf.array().copyOf(n)
                    output.write(pkt)
                    output.flush()
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "Forward error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Forward thread error", e)
        }
    }

    private fun stopVpn() {
        running = false; isRunning = false
        httpProxy?.stop(); httpProxy = null
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
