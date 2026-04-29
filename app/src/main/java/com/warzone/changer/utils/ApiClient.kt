package com.warzone.changer.utils

import com.warzone.changer.App
import com.warzone.changer.data.DeviceStore
import com.warzone.changer.model.Announcement
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 服务端API通信
 */
object ApiClient {

    private val gson = Gson()

    /**
     * 验证卡密
     */
    suspend fun verifyCard(cardKey: String, deviceId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val sign = md5(cardKey + deviceId + "ln0707yyds")
            val body = "action=verify&card_key=${enc(cardKey)}&device_id=${enc(deviceId)}&sign=$sign"
            val resp = post("${App.API_BASE}/index.php", body)
            val json = gson.fromJson(resp, JsonObject::class.java)
            if (json.get("ok")?.asBoolean == true) {
                val expiresAt = json.get("expires_at")?.asString ?: ""
                Pair(true, expiresAt)
            } else {
                Pair(false, json.get("msg")?.asString ?: "验证失败")
            }
        } catch (e: Exception) {
            Pair(false, "网络错误: ${e.message}")
        }
    }

    /**
     * 心跳保活
     */
    suspend fun heartbeat(deviceId: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val body = "action=heartbeat&device_id=${enc(deviceId)}"
            val resp = post("${App.API_BASE}/index.php", body)
            val json = gson.fromJson(resp, JsonObject::class.java)
            val auth = json.get("authorized")?.asBoolean ?: false
            val expires = json.get("expires_at")?.asString
            Pair(auth, expires)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    /**
     * 获取公告
     */
    suspend fun getAnnouncements(): List<Announcement> = withContext(Dispatchers.IO) {
        try {
            val resp = get("${App.API_BASE}/index.php?action=announcements")
            val json = gson.fromJson(resp, JsonObject::class.java)
            val data = json.getAsJsonArray("data") ?: return@withContext emptyList()
            data.map { gson.fromJson(it, Announcement::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 检查设备授权
     */
    suspend fun checkAuth(deviceId: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val resp = get("${App.API_BASE}/index.php?action=check&device_id=${enc(deviceId)}")
            val json = gson.fromJson(resp, JsonObject::class.java)
            val auth = json.get("authorized")?.asBoolean ?: false
            val expires = json.get("expires_at")?.asString
            Pair(auth, expires)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    // ===== HTTP工具方法 =====

    private fun get(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun post(urlStr: String, body: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}