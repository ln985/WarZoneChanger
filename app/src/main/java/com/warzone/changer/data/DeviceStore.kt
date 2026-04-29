package com.warzone.changer.data

import android.content.Context
import java.util.UUID

/**
 * 设备标识管理
 */
object DeviceStore {
    private const val PREFS = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_AUTHORIZED = "authorized"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_CARD_KEY = "card_key"

    fun getDeviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun setAuthorized(ctx: Context, cardKey: String, expiresAt: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTHORIZED, true)
            .putString(KEY_EXPIRES, expiresAt)
            .putString(KEY_CARD_KEY, cardKey)
            .apply()
    }

    fun setUnauthorized(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTHORIZED, false)
            .remove(KEY_EXPIRES)
            .remove(KEY_CARD_KEY)
            .apply()
    }

    fun isAuthorized(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTHORIZED, false)
    }

    fun getExpiresAt(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EXPIRES, null)
    }

    fun getCardKey(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CARD_KEY, null)
    }
}