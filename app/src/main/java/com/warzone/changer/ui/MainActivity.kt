package com.warzone.changer.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.warzone.changer.R
import com.warzone.changer.data.DeviceStore
import com.warzone.changer.data.LocationStore
import com.warzone.changer.model.Announcement
import com.warzone.changer.service.WarZoneVpnService
import com.warzone.changer.utils.ApiClient
import kotlinx.coroutines.launch
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {
    companion object { private const val VPN_REQ = 1001 }
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPickLocation: Button
    private lateinit var btnAnnouncement: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceStore.isAuthorized(this)) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        initViews()
        loadAndShowAnnouncements()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvLocation = findViewById(R.id.tv_location)
        tvExpiry = findViewById(R.id.tv_expiry)
        btnToggle = findViewById(R.id.btn_toggle)
        btnPickLocation = findViewById(R.id.btn_pick_location)
        btnAnnouncement = findViewById(R.id.btn_announcement)
        tvExpiry.text = String.format("到期: %s", DeviceStore.getExpiresAt(this) ?: "未知")
        btnToggle.setOnClickListener { if (WarZoneVpnService.isRunning) stopVpn() else startVpn() }
        btnPickLocation.setOnClickListener { startActivity(Intent(this, LocationPickerActivity::class.java)) }
        btnAnnouncement.setOnClickListener { loadAndShowAnnouncements() }
        updateUI()
    }

    private fun startVpn() {
        if (!LocationStore.has(this)) { Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show(); return }
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, VPN_REQ)
        else launchVpn()
    }

    private fun launchVpn() {
        val intent = Intent(this, WarZoneVpnService::class.java).apply { action = WarZoneVpnService.ACTION_START }
        startService(intent)
        btnToggle.postDelayed({ updateUI() }, 1000)
    }

    private fun stopVpn() {
        startService(Intent(this, WarZoneVpnService::class.java).apply { action = WarZoneVpnService.ACTION_STOP })
        btnToggle.postDelayed({ updateUI() }, 500)
    }

    private fun updateUI() {
        val running = WarZoneVpnService.isRunning
        tvStatus.text = if (running) "● 战区修改运行中" else "○ 战区修改已停止"
        tvStatus.setTextColor(ContextCompat.getColor(this, if (running) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btnToggle.text = if (running) "停止修改" else "启动修改"
        val loc = LocationStore.get(this)
        tvLocation.text = if (loc != null && loc.isValid()) loc.getFormattedAddress() else "未选择位置"
    }

    private fun loadAndShowAnnouncements() {
        lifecycleScope.launch {
            try { val list = ApiClient.getAnnouncements(); if (list.isNotEmpty()) showAnnouncementDialog(list) } catch (_: Exception) {}
        }
    }

    private fun showAnnouncementDialog(announcements: List<Announcement>) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        for ((i, ann) in announcements.withIndex()) {
            container.addView(TextView(this).apply {
                text = ann.title; setTextColor(0xFF60A5FA.toInt()); textSize = 17f; typeface = Typeface.DEFAULT_BOLD
                if (i > 0) setPadding(0, (resources.displayMetrics.density * 16).toInt(), 0, 0)
            })
            if (ann.content.isNotEmpty()) container.addView(TextView(this).apply {
                text = ann.content; setTextColor(0xFFE2E8F0.toInt()); textSize = 14f; setPadding(0, 8, 0, 0)
            })
            if (ann.time.isNotEmpty()) container.addView(TextView(this).apply {
                text = ann.time; setTextColor(0xFF64748B.toInt()); textSize = 11f; setPadding(0, 4, 0, 0)
            })
            if (i < announcements.size - 1) container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 1).toInt()).apply { topMargin = (resources.displayMetrics.density * 12).toInt() }
                setBackgroundColor(0xFF334155.toInt())
            })
        }
        val scroll = ScrollView(this).apply { addView(container); layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.6).toInt()) }
        AlertDialog.Builder(this).setTitle("系统公告").setView(scroll).setPositiveButton("我知道了", null).setCancelable(true).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQ && resultCode == RESULT_OK) launchVpn()
    }

    override fun onResume() {
        super.onResume(); updateUI()
        lifecycleScope.launch {
            val deviceId = DeviceStore.getDeviceId(this@MainActivity)
            val (auth, _) = ApiClient.heartbeat(deviceId)
            if (!auth) {
                DeviceStore.setUnauthorized(this@MainActivity)
                Toast.makeText(this@MainActivity, "授权已失效", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish()
            }
        }
    }
}
