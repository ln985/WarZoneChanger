package com.warzone.changer.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Geocoder
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
import java.util.Locale

class LocationSpoofService : Service() {

    companion object {
        private const val TAG = "LocationSpoof"
        private const val NOTIFICATION_ID = 2
        const val ACTION_START = "com.warzone.action.SPOOF_START"
        const val ACTION_STOP = "com.warzone.action.SPOOF_STOP"
        var isRunning = false
            private set

        private val CITY_COORDS = mapOf(
            "Beijing" to Pair(39.9042, 116.4074),
            "Shanghai" to Pair(31.2304, 121.4737),
            "Tianjin" to Pair(39.3434, 117.3616),
            "Chongqing" to Pair(29.5630, 106.5516),
            "Guangzhou" to Pair(23.1291, 113.2644),
            "Shenzhen" to Pair(22.5431, 114.0579),
            "Hangzhou" to Pair(30.2741, 120.1551),
            "Nanjing" to Pair(32.0603, 118.7969),
            "Chengdu" to Pair(30.5728, 104.0668),
            "Wuhan" to Pair(30.5928, 114.3055),
            "Xian" to Pair(34.3416, 108.9398),
            "Changsha" to Pair(28.2282, 112.9388),
            "Henan" to Pair(34.7657, 113.7536),
            "Hebei" to Pair(38.0428, 114.5149),
            "Shandong" to Pair(36.6683, 116.9972),
            "Shanxi" to Pair(37.8706, 112.5489),
            "Liaoning" to Pair(41.8057, 123.4315),
            "Jilin" to Pair(43.8868, 125.3245),
            "Heilongjiang" to Pair(45.7420, 126.6610),
            "Jiangsu" to Pair(32.0617, 118.7778),
            "Zhejiang" to Pair(30.2741, 120.1551),
            "Anhui" to Pair(31.8612, 117.2830),
            "Fujian" to Pair(26.0745, 119.2965),
            "Jiangxi" to Pair(28.6820, 115.8579),
            "Hubei" to Pair(30.5928, 114.3055),
            "Hunan" to Pair(28.2282, 112.9388),
            "Guangdong" to Pair(23.1317, 113.2664),
            "Guangxi" to Pair(22.8170, 108.3665),
            "Hainan" to Pair(20.0174, 110.3492),
            "Sichuan" to Pair(30.5728, 104.0668),
            "Guizhou" to Pair(26.6470, 106.6302),
            "Yunnan" to Pair(25.0389, 102.7183),
            "Tibet" to Pair(29.6525, 91.1721),
            "Shaanxi" to Pair(34.2658, 108.9541),
            "Gansu" to Pair(36.0611, 103.8343),
            "Qinghai" to Pair(36.6171, 101.7782),
            "Ningxia" to Pair(38.4872, 106.2309),
            "Xinjiang" to Pair(43.7930, 87.6271),
            "InnerMongolia" to Pair(40.8183, 111.7655),
            "Macau" to Pair(22.1987, 113.5439),
            "HongKong" to Pair(22.3193, 114.1694),
            "Taiwan" to Pair(25.0330, 121.5654)
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var updateRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSpoofing(); return START_NOT_STICKY }
            else -> startSpoofing()
        }
        return START_STICKY
    }

    private fun startSpoofing() {
        val location = LocationStore.get(this)
        if (location == null || !location.isValid()) {
            Toast.makeText(this, "Please select location first", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val coords = resolveCoordinates(location.getFormattedAddress())
        if (coords == null) {
            Toast.makeText(this, "Cannot resolve coordinates", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        targetLat = coords.first
        targetLng = coords.second

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            setupMockProvider()
            isRunning = true
            Log.i(TAG, "Mock location started: $targetLat, $targetLng")
        } catch (e: SecurityException) {
            Toast.makeText(this, "Set this app as mock location app in developer options", Toast.LENGTH_LONG).show()
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start", e)
            Toast.makeText(this, "Start failed: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun resolveCoordinates(address: String): Pair<Double, Double>? {
        try {
            val geocoder = Geocoder(this, Locale.CHINA)
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) {
                return Pair(results[0].latitude, results[0].longitude)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed: ${e.message}")
        }

        try {
            val geocoder = Geocoder(this, Locale.CHINA)
            val parts = address.split("省", "市", "区", "县")
            for (part in parts.reversed()) {
                if (part.length >= 2) {
                    val results = geocoder.getFromLocationName(part, 1)
                    if (!results.isNullOrEmpty()) {
                        return Pair(results[0].latitude, results[0].longitude)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback geocoding failed: ${e.message}")
        }

        return null
    }

    private fun setupMockProvider() {
        try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}

        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        updateRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                try {
                    val loc = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = targetLat + (Math.random() - 0.5) * 0.0002
                        longitude = targetLng + (Math.random() - 0.5) * 0.0002
                        altitude = 50.0
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                        accuracy = 8f
                        speed = 0f
                        bearing = 0f
                    }
                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
                } catch (e: Exception) {
                    Log.e(TAG, "Set mock location failed", e)
                }
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopSpoofing() {
        isRunning = false
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
        try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, App.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("LiuNian WarZone Tool")
            .setContentText("GPS Mock Location Running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopSpoofing()
        super.onDestroy()
    }
}
