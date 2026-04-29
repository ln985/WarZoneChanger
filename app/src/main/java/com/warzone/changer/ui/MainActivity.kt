package com.warzone.changer.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.warzone.changer.R
import com.warzone.changer.data.DeviceStore
import com.warzone.changer.data.LocationStore
import com.warzone.changer.model.Announcement
import com.warzone.changer.service.LocationSpoofService
import com.warzone.changer.utils.ApiClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPickLocation: Button
    private lateinit var btnAnnouncement: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DeviceStore.isAuthorized(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        initViews()
        requestNotifPermission()
        loadAndShowAnnouncements()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvLocation = findViewById(R.id.tv_location)
        tvExpiry = findViewById(R.id.tv_expiry)
        btnToggle = findViewById(R.id.btn_toggle)
        btnPickLocation = findViewById(R.id.btn_pick_location)
        btnAnnouncement = findViewById(R.id.btn_announcement)

        tvExpiry.text = "Expires: ${DeviceStore.getExpiresAt(this) ?: "Unknown"}"

        btnToggle.setOnClickListener {
            if (LocationSpoofService.isRunning) stopSpoof() else startSpoof()
        }

        btnPickLocation.setOnClickListener {
            startActivity(Intent(this, LocationPickerActivity::class.java))
        }

        btnAnnouncement.setOnClickListener {
            loadAndShowAnnouncements()
        }

        updateUI()
    }

    private fun startSpoof() {
        if (!LocationStore.has(this)) {
            Toast.makeText(this, "Please select location first", Toast.LENGTH_SHORT).show()
            return
        }

        // Check mock location permission
        val allowedApps = Settings.Secure.getString(contentResolver, "mock_location")
        if (allowedApps == null || !allowedApps.contains(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Mock Location Permission")
                .setMessage("Please set this app as mock location app:\n\nDeveloper Options > Select mock location app > LiuNian WarZone Tool")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val intent = Intent(this, LocationSpoofService::class.java).apply {
            action = LocationSpoofService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        btnToggle.postDelayed({ updateUI() }, 1000)
    }

    private fun stopSpoof() {
        startService(Intent(this, LocationSpoofService::class.java).apply {
            action = LocationSpoofService.ACTION_STOP
        })
        btnToggle.postDelayed({ updateUI() }, 500)
    }

    private fun updateUI() {
        val running = LocationSpoofService.isRunning
        tvStatus.text = if (running) "GPS Mock Running" else "GPS Mock Stopped"
        tvStatus.setTextColor(ContextCompat.getColor(this,
            if (running) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btnToggle.text = if (running) "Stop Mock" else "Start Mock"

        val loc = LocationStore.get(this)
        tvLocation.text = if (loc != null && loc.isValid()) {
            "Location: ${loc.getFormattedAddress()}"
        } else {
            "No location selected"
        }
    }

    private fun loadAndShowAnnouncements() {
        lifecycleScope.launch {
            try {
                val list = ApiClient.getAnnouncements()
                if (list.isNotEmpty()) {
                    showAnnouncementDialog(list)
                }
            } catch (_: Exception) { }
        }
    }

    private fun showAnnouncementDialog(announcements: List<Announcement>) {
        val container = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        for ((index, ann) in announcements.withIndex()) {
            val titleView = TextView(this@MainActivity).apply {
                text = ann.title
                setTextColor(0xFF60A5FA.toInt())
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                if (index > 0) {
                    val p = (resources.displayMetrics.density * 16).toInt()
                    setPadding(0, p, 0, 0)
                }
            }
            container.addView(titleView)

            if (ann.content.isNotEmpty()) {
                val contentView = TextView(this@MainActivity).apply {
                    text = ann.content
                    setTextColor(0xFFE2E8F0.toInt())
                    textSize = 14f
                    setPadding(0, 8, 0, 0)
                }
                container.addView(contentView)
            }

            if (ann.time.isNotEmpty()) {
                val timeView = TextView(this@MainActivity).apply {
                    text = ann.time
                    setTextColor(0xFF64748B.toInt())
                    textSize = 11f
                    setPadding(0, 4, 0, 0)
                }
                container.addView(timeView)
            }

            if (index < announcements.size - 1) {
                val divider = View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.density * 1).toInt()
                    ).apply {
                        topMargin = (resources.displayMetrics.density * 12).toInt()
                    }
                    setBackgroundColor(0xFF334155.toInt())
                }
                container.addView(divider)
            }
        }

        val scroll = ScrollView(this@MainActivity).apply {
            addView(container)
            val maxHeight = (resources.displayMetrics.heightPixels * 0.6).toInt()
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
        }

        AlertDialog.Builder(this)
            .setTitle("Announcement")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        lifecycleScope.launch {
            val deviceId = DeviceStore.getDeviceId(this@MainActivity)
            val (auth, _) = ApiClient.heartbeat(deviceId)
            if (!auth) {
                DeviceStore.setUnauthorized(this@MainActivity)
                Toast.makeText(this@MainActivity, "Authorization expired", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}
