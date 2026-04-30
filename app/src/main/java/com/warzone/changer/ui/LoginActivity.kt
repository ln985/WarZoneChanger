package com.warzone.changer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.warzone.changer.R
import com.warzone.changer.data.DeviceStore
import com.warzone.changer.utils.ApiClient

class LoginActivity : AppCompatActivity() {

    private lateinit var etCardKey: EditText
    private lateinit var btnVerify: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etCardKey = findViewById(R.id.et_card_key)
        btnVerify = findViewById(R.id.btn_verify)

        btnVerify.setOnClickListener {
            val cardKey = etCardKey.text.toString().trim()
            if (cardKey.isEmpty()) {
                Toast.makeText(this, "请输入卡密", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val deviceId = DeviceStore.getDeviceId(this)
            val deviceName = DeviceStore.getDeviceName()
            
            ApiClient.verify(cardKey, deviceId, deviceName, object : ApiClient.Callback {
                override fun onSuccess(message: String, data: Map<String, Any>?) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "验证成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }
}