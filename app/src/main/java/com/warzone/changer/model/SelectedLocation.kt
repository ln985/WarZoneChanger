package com.warzone.changer.model

/**
 * 用户选择的虚拟位置
 */
data class SelectedLocation(
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val adcode: String = ""
) {
    fun isValid(): Boolean = province.isNotEmpty() || city.isNotEmpty()

    /**
     * 生成腾讯地图API需要的location参数 (lat,lng)
     */
    fun locationParam(): String = "$latitude,$longitude"

    fun getFormattedAddress(): String {
        return buildString {
            if (province.isNotEmpty()) append(province)
            if (city.isNotEmpty() && city != province) append(city)
            if (district.isNotEmpty()) append(district)
        }
    }
}