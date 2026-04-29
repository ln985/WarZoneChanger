package com.warzone.changer.injector

import android.content.Context
import com.warzone.changer.data.LocationStore
import com.warzone.changer.model.SelectedLocation

/**
 * Location injection helper
 * Stores and retrieves virtual location data for VPN proxy use
 */
class LocationInjector(private val context: Context) {

    fun getCurrentLocation(): SelectedLocation? = LocationStore.get(context)

    fun isActive(): Boolean = LocationStore.has(context)

    /**
     * Generate modified geocoder response JSON
     * Call this when intercepting Tencent Map API responses
     */
    fun buildModifiedResponse(originalJson: String): String {
        val location = getCurrentLocation() ?: return originalJson
        return try {
            val root = org.json.JSONObject(originalJson)
            val result = root.optJSONObject("result") ?: return originalJson

            val adInfo = result.optJSONObject("ad_info")
            adInfo?.let {
                if (location.province.isNotEmpty()) it.put("province", location.province)
                if (location.city.isNotEmpty()) it.put("city", location.city)
                if (location.district.isNotEmpty()) it.put("district", location.district)
                if (location.adcode.isNotEmpty()) it.put("adcode", location.adcode)
            }

            val addrComp = result.optJSONObject("address_component")
            addrComp?.let {
                if (location.province.isNotEmpty()) it.put("province", location.province)
                if (location.city.isNotEmpty()) it.put("city", location.city)
                if (location.district.isNotEmpty()) it.put("district", location.district)
            }

            val fmtAddr = result.optJSONObject("formatted_addresses")
            fmtAddr?.let {
                val addr = listOfNotNull(
                    location.province.takeIf { it.isNotEmpty() },
                    location.city.takeIf { it.isNotEmpty() && it != location.province },
                    location.district.takeIf { it.isNotEmpty() }
                ).joinToString("")
                it.put("recommend", addr)
                it.put("rough", addr)
            }

            root.toString()
        } catch (e: Exception) {
            originalJson
        }
    }
}
