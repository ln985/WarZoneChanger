package com.warzone.changer.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.warzone.changer.R
import com.warzone.changer.data.LocationStore
import com.warzone.changer.service.VpnProxyService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_VPN = 100
        private const val REQUEST_LOCATION = 200
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnSelectLocation: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvLocation = findViewById(R.id.tv_location)
        btnToggle = findViewById(R.id.btn_toggle)
        btnSelectLocation = findViewById(R.id.btn_select_location)

        btnSelectLocation.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java)
            startActivityForResult(intent, REQUEST_LOCATION)
        }

        btnToggle.setOnClickListener {
            if (VpnProxyService.isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val location = LocationStore.getSelectedLocation(this)
        if (location != null) {
            tvLocation.text = "当前战区: ${location.province} ${location.city} (${location.adcode})"
        } else {
            tvLocation.text = "未选择战区"
        }

        if (VpnProxyService.isRunning) {
            tvStatus.text = "● 运行中"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            btnToggle.text = "停止修改"
        } else {
            tvStatus.text = "○ 已停止"
            tvStatus.setTextColor(0xFFF44336.toInt())
            btnToggle.text = "开始修改"
        }
    }

    private fun startVpn() {
        if (!LocationStore.hasLocation(this)) {
            Toast.makeText(this, "请先选择目标战区", Toast.LENGTH_SHORT).show()
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN)
        } else {
            onVpnPermissionGranted()
        }
    }

    private fun onVpnPermissionGranted() {
        val intent = Intent(this, VpnProxyService::class.java)
        intent.action = VpnProxyService.ACTION_START
        startForegroundService(intent)
        updateUI()
        Toast.makeText(this, "战区修改已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopVpn() {
        val intent = Intent(this, VpnProxyService::class.java)
        intent.action = VpnProxyService.ACTION_STOP
        startService(intent)
        updateUI()
        Toast.makeText(this, "战区修改已停止", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_VPN -> {
                if (resultCode == Activity.RESULT_OK) {
                    onVpnPermissionGranted()
                } else {
                    Toast.makeText(this, "VPN权限被拒绝", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION -> {
                updateUI()
            }
        }
    }
}