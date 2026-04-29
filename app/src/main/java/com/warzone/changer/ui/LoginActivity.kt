package com.warzone.changer.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.warzone.changer.R
import com.warzone.changer.data.DeviceStore
import com.warzone.changer.model.Announcement
import com.warzone.changer.utils.ApiClient
import kotlinx.coroutines.launch
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var btnAnnouncement: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (DeviceStore.isAuthorized(this)) {
            goMain()
            return
        }

        setContentView(R.layout.activity_login)

        etKey = findViewById(R.id.et_card_key)
        btnLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progress)
        tvError = findViewById(R.id.tv_error)
        btnAnnouncement = findViewById(R.id.btn_announcement)

        btnLogin.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                showError("请输入卡密")
                return@setOnClickListener
            }
            doLogin(key)
        }

        btnAnnouncement.setOnClickListener {
            loadAndShowAnnouncements()
        }

        loadAndShowAnnouncements()
    }

    private fun doLogin(cardKey: String) {
        btnLogin.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE

        val deviceId = DeviceStore.getDeviceId(this)

        lifecycleScope.launch {
            val (success, result) = ApiClient.verifyCard(cardKey, deviceId)
            progressBar.visibility = View.GONE
            btnLogin.isEnabled = true

            if (success) {
                DeviceStore.setAuthorized(this@LoginActivity, cardKey, result)
                Toast.makeText(this@LoginActivity, "激活成功！", Toast.LENGTH_SHORT).show()
                goMain()
            } else {
                showError(result)
            }
        }
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
        val container = LinearLayout(this@LoginActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        for ((index, ann) in announcements.withIndex()) {
            val titleView = TextView(this@LoginActivity).apply {
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
                val contentView = TextView(this@LoginActivity).apply {
                    text = ann.content
                    setTextColor(0xFFE2E8F0.toInt())
                    textSize = 14f
                    setPadding(0, 8, 0, 0)
                }
                container.addView(contentView)
            }

            if (ann.time.isNotEmpty()) {
                val timeView = TextView(this@LoginActivity).apply {
                    text = ann.time
                    setTextColor(0xFF64748B.toInt())
                    textSize = 11f
                    setPadding(0, 4, 0, 0)
                }
                container.addView(timeView)
            }

            if (index < announcements.size - 1) {
                val divider = View(this@LoginActivity).apply {
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

        val scroll = ScrollView(this@LoginActivity).apply {
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
}
