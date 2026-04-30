package com.warzone.changer.utils

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 服务器API客户端
 */
object ApiClient {

    private const val TAG = "ApiClient"
    private const val BASE_URL = "https://lnzdy.xf79.cn/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    interface Callback {
        fun onSuccess(message: String, data: Map<String, Any>? = null)
        fun onError(message: String)
    }

    fun verify(cardKey: String, deviceId: String, deviceName: String, callback: Callback) {
        val json = JSONObject().apply {
            put("cardKey", cardKey)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/verify.php")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "请求失败", e)
                callback.onError("网络请求失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyStr = response.body?.string() ?: "{}"
                    val result = JSONObject(bodyStr)
                    val success = result.optBoolean("success", false)
                    val message = result.optString("message", "未知错误")

                    if (success) {
                        val data = mutableMapOf<String, Any>()
                        result.optJSONObject("data")?.let { dataObj ->
                            dataObj.keys().forEach { key ->
                                data[key] = dataObj.get(key)
                            }
                        }
                        callback.onSuccess(message, data)
                    } else {
                        callback.onError(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析响应失败", e)
                    callback.onError("解析响应失败")
                }
            }
        })
    }
}