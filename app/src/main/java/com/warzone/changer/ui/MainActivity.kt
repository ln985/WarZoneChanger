package com.warzone.changer.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
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
import com.warzone.changer.service.VpnProxyService
import com.warzone.changer.utils.ApiClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val VPN_REQ = 1001
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPickLocation: Button
    private lateinit var progressBar: ProgressBar

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
        progressBar = findViewById(R.id.progress)

        tvExpiry.text = "到期时间: ${DeviceStore.getExpiresAt(this) ?: "未知"}"

        btnToggle.setOnClickListener {
            if (VpnProxyService.isRunning) stopVpn() else startVpn()
        }

        btnPickLocation.setOnClickListener {
            startActivity(Intent(this, LocationPickerActivity::class.java))
        }

        updateUI()
    }

    private fun startVpn() {
        if (!LocationStore.has(this)) {
            Toast.makeText(this, "请先选择虚拟位置", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQ)
        } else {
            launchVpnService()
        }
    }

    private fun stopVpn() {
        startService(Intent(this, VpnProxyService::class.java).apply {
            action = VpnProxyService.ACTION_STOP
        })
        btnToggle.postDelayed({ updateUI() }, 500)
    }

    private fun launchVpnService() {
        val intent = Intent(this, VpnProxyService::class.java).apply {
            action = VpnProxyService.ACTION_START
        }
        startService(intent)
        btnToggle.postDelayed({ updateUI() }, 1000)
    }

    private fun updateUI() {
        val running = VpnProxyService.isRunning
        tvStatus.text = if (running) "● VPN运行中" else "○ VPN已停止"
        tvStatus.setTextColor(ContextCompat.getColor(this,
            if (running) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btnToggle.text = if (running) "停止代理" else "启动代理"

        val loc = LocationStore.get(this)
        tvLocation.text = if (loc != null && loc.isValid()) {
            "📍 ${loc.getFormattedAddress()}"
        } else {
            "⚠️ 未选择位置"
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
                text = "📢 ${ann.title}"
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
            .setTitle("系统公告")
            .setView(scroll)
            .setPositiveButton("我知道了", null)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQ && resultCode == RESULT_OK) launchVpnService()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        lifecycleScope.launch {
            val deviceId = DeviceStore.getDeviceId(this@MainActivity)
            val (auth, _) = ApiClient.heartbeat(deviceId)
            if (!auth) {
                DeviceStore.setUnauthorized(this@MainActivity)
                Toast.makeText(this@MainActivity, "授权已失效，请重新激活", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}