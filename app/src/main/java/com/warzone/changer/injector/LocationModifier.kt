package com.warzone.changer.injector

import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject

object LocationModifier {
    fun modifyResponse(body: String, loc: SelectedLocation): String {
        return try {
            val json = JSONObject(body)
            patchAll(json, loc)
            json.toString()
        } catch (e: Exception) {
            body
        }
    }

    private fun patchAll(json: JSONObject, loc: SelectedLocation) {
        doReplace(json, loc)
        json.optJSONObject("result")?.let { doReplace(it, loc) }
        json.optJSONObject("data")?.let { d ->
            doReplace(d, loc)
            d.optJSONObject("result")?.let { doReplace(it, loc) }
            d.optJSONObject("ad_info")?.let { doReplace(it, loc) }
            d.optJSONObject("location")?.let { doReplace(it, loc) }
        }
        json.optJSONObject("location")?.let { doReplace(it, loc) }
    }

    private fun doReplace(o: JSONObject, loc: SelectedLocation) {
        o.optJSONObject("ad_info")?.let { setLoc(it, loc) }
        o.optJSONObject("address_component")?.let { setLoc(it, loc) }
        o.optJSONObject("address_info")?.let { setLoc(it, loc) }
        if (loc.adcode.isNotEmpty()) {
            if (o.has("adcode")) o.put("adcode", loc.adcode)
            if (o.has("city_code")) o.put("city_code", loc.adcode)
        }
    }

    private fun setLoc(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
    }
}
