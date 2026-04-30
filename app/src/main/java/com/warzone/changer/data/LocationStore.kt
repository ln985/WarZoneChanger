package com.warzone.changer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 用户选择的目标位置存储
 * 存储用户选择的目标战区编码，供拦截器使用
 */
object LocationStore {

    private const val PREF_NAME = "warzone_location"
    private const val KEY_ADCODE = "target_adcode"
    private const val KEY_NAME = "target_name"
    private const val KEY_PROVINCE = "target_province"
    private const val KEY_CITY = "target_city"

    data class SelectedLocation(
        val adcode: String,
        val name: String,
        val province: String,
        val city: String
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存用户选择的目标位置
     */
    fun saveLocation(context: Context, adcode: String, name: String, province: String, city: String) {
        getPrefs(context).edit().apply {
            putString(KEY_ADCODE, adcode)
            putString(KEY_NAME, name)
            putString(KEY_PROVINCE, province)
            putString(KEY_CITY, city)
            apply()
        }
    }

    /**
     * 获取用户选择的目标位置
     * @return SelectedLocation 或 null（如果未选择）
     */
    fun getSelectedLocation(context: Context): SelectedLocation? {
        val prefs = getPrefs(context)
        val adcode = prefs.getString(KEY_ADCODE, null) ?: return null
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val province = prefs.getString(KEY_PROVINCE, "") ?: ""
        val city = prefs.getString(KEY_CITY, "") ?: ""
        return SelectedLocation(adcode, name, province, city)
    }

    /**
     * 检查是否已选择位置
     */
    fun hasLocation(context: Context): Boolean {
        return getPrefs(context).contains(KEY_ADCODE)
    }

    /**
     * 清除选择
     */
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}