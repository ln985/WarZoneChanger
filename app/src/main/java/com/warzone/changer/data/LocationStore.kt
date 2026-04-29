package com.warzone.changer.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.warzone.changer.model.SelectedLocation

/**
 * 位置存储 - 使用SharedPreferences保存用户选择的虚拟位置
 */
object LocationStore {
    private const val PREFS = "location_prefs"
    private const val KEY = "selected_location"
    private val gson = Gson()

    fun get(ctx: Context): SelectedLocation? {
        val json = prefs(ctx).getString(KEY, null) ?: return null
        return try { gson.fromJson(json, SelectedLocation::class.java) } catch (e: Exception) { null }
    }

    fun save(ctx: Context, loc: SelectedLocation) {
        prefs(ctx).edit().putString(KEY, gson.toJson(loc)).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }

    fun has(ctx: Context): Boolean = get(ctx)?.isValid() == true

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}