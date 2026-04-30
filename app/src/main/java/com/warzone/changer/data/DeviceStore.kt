package com.warzone.changer.data

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 设备标识管理
 * 使用Android ID作为设备唯一标识，用于卡密绑定
 */
object DeviceStore {

    /**
     * 获取设备唯一标识
     */
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: generateFallbackId()
        } catch (e: Exception) {
            generateFallbackId()
        }
    }

    /**
     * 获取设备名称
     */
    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun generateFallbackId(): String {
        return "unknown_${System.currentTimeMillis()}"
    }
}