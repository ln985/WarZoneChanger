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
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN服务 - 通过本地代理拦截并修改战区信息
 * 原理: VPN -> 本地TUN接口 -> 解析IP包 -> 检测HTTP请求 -> 修改响应中的adcode
 */
class LocalVpnService : VpnService() {

    companion object {
        private const val TAG = "LocalVPN"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.warzone.action.START"
        const val ACTION_STOP = "com.warzone.action.STOP"
        var isRunning = false; private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); return START_NOT_STICKY }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        val loc = LocationStore.get(this)
        if (loc == null || !loc.isValid()) {
            android.widget.Toast.makeText(this, "请先选择虚拟位置", android.widget.Toast.LENGTH_SHORT).show()
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
            running.set(true); isRunning = true
            Log.i(TAG, "VPN started, adcode=${loc.adcode}")
            workerThread = Thread({ pumpPackets() }, "vpn-packet-pump").apply { start() }
        } catch (e: Exception) {
            Log.e(TAG, "startVpn failed", e); teardown()
        }
    }

    private fun pumpPackets() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteBuffer.allocate(65535)
        val loc = LocationStore.get(this)
        while (running.get()) {
            try {
                buf.clear()
                val n = input.read(buf.array())
                if (n <= 0) { Thread.sleep(5); continue }
                buf.limit(n)
                val pkt = buf.array().copyOf(n)
                val out = tryModifyPacket(pkt, loc)
                output.write(out ?: pkt); output.flush()
            } catch (e: Exception) {
                if (running.get()) Log.w(TAG, "pump: ${e.message}")
            }
        }
    }

    /**
     * 解析IP+TCP包，提取HTTP payload
     * 如果是腳本的位置API响应，修改adcode
     */
    private fun tryModifyPacket(pkt: ByteArray, loc: com.warzone.changer.model.SelectedLocation?): ByteArray? {
        if (loc == null || pkt.size < 40) return null
        try {
            val verIhl = pkt[0].toInt() and 0xFF
            val ipVer = verIhl shr 4
            if (ipVer != 4) return null
            val ipHdrLen = (verIhl and 0x0F) * 4
            val totalLen = ((pkt[2].toInt() and 0xFF) shl 8) or (pkt[3].toInt() and 0xFF)
            val proto = pkt[9].toInt() and 0xFF
            if (proto != 6) return null // TCP only
            if (totalLen < ipHdrLen + 20) return null
            val tcpHdrOff = ipHdrLen
            val dataOff = ((pkt[tcpHdrOff + 12].toInt() and 0xFF) shr 4) * 4
            val payloadStart = ipHdrLen + dataOff
            if (payloadStart >= totalLen) return null
            val payload = pkt.copyOfRange(payloadStart, totalLen)
            val head = String(payload, Charsets.US_ASCII).take(512)
            // 检查是否是HTTP响应且包含位置相关字段
            if (!head.startsWith("HTTP/")) return null
            if (!head.contains("adcode") && !head.contains("province") &&
                !head.contains("city_code") && !head.contains("ad_info")) return null
            Log.d(TAG, "Intercepted location API response, modifying...")
            val body = String(payload, Charsets.UTF_8)
            val modified = com.warzone.changer.injector.LocationModifier.modifyResponse(body, loc)
            val modBytes = modified.toByteArray(Charsets.UTF_8)
            // 重建IP包
            val newPkt = ByteArray(payloadStart + modBytes.size)
            System.arraycopy(pkt, 0, newPkt, 0, payloadStart)
            System.arraycopy(modBytes, 0, newPkt, payloadStart, modBytes.size)
            // 修改IP总长
            val newTotal = newPkt.size
            newPkt[2] = ((newTotal shr 8) and 0xFF).toByte()
            newPkt[3] = (newTotal and 0xFF).toByte()
            // 重算IP校验和
            newPkt[10] = 0; newPkt[11] = 0
            var sum = 0
            for (i in 0 until ipHdrLen step 2) sum += ((newPkt[i].toInt() and 0xFF) shl 8) or (newPkt[i+1].toInt() and 0xFF)
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            val cksum = sum.inv() and 0xFFFF
            newPkt[10] = (cksum shr 8).toByte(); newPkt[11] = (cksum and 0xFF).toByte()
            return newPkt
        } catch (e: Exception) { return null }
    }

    private fun teardown() {
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
        return b.setContentTitle("流年改战区工具").setContentText("战区修改运行中")
            .setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).build()
    }

    override fun onDestroy() { teardown(); super.onDestroy() }
}
