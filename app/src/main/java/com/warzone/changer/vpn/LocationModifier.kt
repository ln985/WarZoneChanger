package com.warzone.changer.vpn

import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject

object LocationModifier {
    fun modifyResponse(body: String, loc: SelectedLocation): String {
        return try {
            val json = JSONObject(body)
            patch(json, loc)
            json.optJSONObject("result")?.let { patch(it, loc) }
            json.optJSONObject("data")?.let { d ->
                patch(d, loc)
                d.optJSONObject("result")?.let { patch(it, loc) }
                d.optJSONObject("ad_info")?.let { patchAdInfo(it, loc) }
                d.optJSONObject("location")?.let { patch(it, loc) }
            }
            json.optJSONObject("location")?.let { patch(it, loc) }
            json.toString()
        } catch (e: Exception) { body }
    }

    private fun patch(o: JSONObject, loc: SelectedLocation) {
        o.optJSONObject("ad_info")?.let { patchAdInfo(it, loc) }
        o.optJSONObject("address_component")?.let { patchAddr(it, loc) }
        o.optJSONObject("address_info")?.let { patchAddr(it, loc) }
        if (loc.adcode.isNotEmpty()) {
            if (o.has("adcode")) o.put("adcode", loc.adcode)
            if (o.has("city_code")) o.put("city_code", loc.adcode)
        }
    }

    private fun patchAdInfo(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
    }

    private fun patchAddr(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
    }
}
