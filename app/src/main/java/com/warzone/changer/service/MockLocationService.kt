package com.warzone.changer.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.warzone.changer.App
import com.warzone.changer.R
import com.warzone.changer.data.LocationStore
import com.warzone.changer.ui.MainActivity

class MockLocationService : Service() {
    companion object {
        private const val TAG = "MockLocation"
        private const val NOTIFICATION_ID = 2
        const val ACTION_START = "com.warzone.action.MOCK_START"
        const val ACTION_STOP = "com.warzone.action.MOCK_STOP"
        var isRunning = false; private set
        
        private val CITY_COORDS = mapOf(
            "北京" to doubleArrayOf(39.9042, 116.4074),
            "上海" to doubleArrayOf(31.2304, 121.4737),
            "广州" to doubleArrayOf(23.1291, 113.2644),
            "深圳" to doubleArrayOf(22.5431, 114.0579),
            "成都" to doubleArrayOf(30.5728, 104.0668),
            "武汉" to doubleArrayOf(30.5928, 114.3055),
            "杰州" to doubleArrayOf(34.7657, 113.7536),
            "河北" to doubleArrayOf(38.0428, 114.5149),
            "山东" to doubleArrayOf(36.6683, 116.9972),
            "江苏" to doubleArrayOf(32.0617, 118.7778),
            "浙江" to doubleArrayOf(30.2741, 120.1551),
            "四川" to doubleArrayOf(30.5728, 104.0668),
            "湖北" to doubleArrayOf(30.5928, 114.3055),
            "湖南" to doubleArrayOf(28.2282, 112.9388),
            "福建" to doubleArrayOf(26.0745, 119.2965),
            "重庆" to doubleArrayOf(29.5630, 106.5516),
            "天津" to doubleArrayOf(39.3434, 117.3616),
            "西安" to doubleArrayOf(34.3416, 108.9398),
            "长沙" to doubleArrayOf(28.2282, 112.9388),
            "南京" to doubleArrayOf(32.0603, 118.7969),
            "杍州" to doubleArrayOf(30.2741, 120.1551),
            "安徽" to doubleArrayOf(31.8612, 117.2830),
            "江西" to doubleArrayOf(28.6820, 115.8579),
            "辽宁" to doubleArrayOf(41.8057, 123.4315),
            "吉林" to doubleArrayOf(43.8868, 125.3245),
            "黑龙江" to doubleArrayOf(45.7420, 126.6610),
            "广西" to doubleArrayOf(22.8170, 108.3665),
            "海南" to doubleArrayOf(20.0174, 110.3492),
            "贵州" to doubleArrayOf(26.6470, 106.6302),
            "云南" to doubleArrayOf(25.0389, 102.7183),
            "西藏" to doubleArrayOf(29.6525, 91.1721),
            "陕西" to doubleArrayOf(34.2658, 108.9541),
            "甘肃" to doubleArrayOf(36.0611, 103.8343),
            "青海" to doubleArrayOf(36.6171, 101.7782),
            "宁夏" to doubleArrayOf(38.4872, 106.2309),
            "新疆" to doubleArrayOf(43.7930, 87.6271),
            "内蒙古" to doubleArrayOf(40.8183, 111.7655),
            "香港" to doubleArrayOf(22.3193, 114.1694),
            "澳门" to doubleArrayOf(22.1987, 113.5439),
            "台湾" to doubleArrayOf(25.0330, 121.5654)
        )
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lm: LocationManager
    private var lat = 0.0; private var lng = 0.0
    private var runner: Runnable? = null
    override fun onCreate() { super.onCreate(); lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_STOP -> { stop(); return START_NOT_STICKY } else -> start() }
        return START_STICKY
    }
    private fun start() {
        val loc = LocationStore.get(this)
        if (loc == null || !loc.isValid()) { Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show(); stopSelf(); return }
        val coords = resolveCoords(loc.getFormattedAddress())
        if (coords == null) { Toast.makeText(this, "无法解析坐标", Toast.LENGTH_SHORT).show(); stopSelf(); return }
        lat = coords[0]; lng = coords[1]
        try {
            startForeground(NOTIFICATION_ID, buildNotif())
            try { lm.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            isRunning = true
            runner = object : Runnable { override fun run() {
                if (!isRunning) return
                try {
                    val l = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = lat + (Math.random() - 0.5) * 0.0003
                        longitude = lng + (Math.random() - 0.5) * 0.0003
                        altitude = 50.0; time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                        accuracy = 10f; speed = 0f; bearing = 0f
                    }
                    lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, l)
                } catch (e: Exception) { Log.e(TAG, "set mock failed", e) }
                handler.postDelayed(this, 1500)
            }}
            handler.post(runner!!)
        } catch (e: SecurityException) {
            Toast.makeText(this, "请在开发者选项中设置本应用为模拟位置应用", Toast.LENGTH_LONG).show()
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "start failed", e); stopSelf() }
    }
    private fun resolveCoords(addr: String): DoubleArray? {
        for ((k, v) in CITY_COORDS) { if (addr.contains(k)) return v }
        val geocoder = android.location.Geocoder(this, java.util.Locale.CHINA)
        try { val r = geocoder.getFromLocationName(addr, 1); if (!r.isNullOrEmpty()) return doubleArrayOf(r[0].latitude, r[0].longitude) } catch (_: Exception) {}
        return null
    }
    private fun stop() {
        isRunning = false; runner?.let { handler.removeCallbacks(it) }; runner = null
        try { lm.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }
    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, App.CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("流年改战区").setContentText("模拟定位运行中").setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).build()
    }
    override fun onDestroy() { stop(); super.onDestroy() }
}
