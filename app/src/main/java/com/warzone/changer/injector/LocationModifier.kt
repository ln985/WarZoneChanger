package com.warzone.changer.injector

import android.util.Log
import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject

object LocationModifier {
    private const val TAG = "LocationMod"

    fun modifyResponse(body: String, loc: SelectedLocation): String {
        return try { modifyJson(body, loc) } catch (e: Exception) { replaceString(body, loc) }
    }

    private fun modifyJson(s: String, loc: SelectedLocation): String {
        val json = JSONObject(s)
        patch(json, loc)
        json.optJSONObject("result")?.let { patch(it, loc) }
        json.optJSONObject("data")?.let { d ->
            patch(d, loc)
            d.optJSONObject("result")?.let { patch(it, loc) }
            d.optJSONObject("ad_info")?.let { patchAdInfo(it, loc) }
            d.optJSONObject("location")?.let { patch(it, loc) }
        }
        json.optJSONObject("location")?.let { patch(it, loc) }
        return json.toString()
    }

    private fun patch(o: JSONObject, loc: SelectedLocation) {
        o.optJSONObject("ad_info")?.let { patchAdInfo(it, loc) }
        o.optJSONObject("address_component")?.let { patchAddr(it, loc) }
        o.optJSONObject("address_info")?.let { patchAddr(it, loc) }
        if (loc.adcode.isNotEmpty()) {
            o.opt("adcode")?.let { o.put("adcode", loc.adcode) }
            o.opt("city_code")?.let { o.put("city_code", loc.adcode) }
        }
    }

    private fun patchAdInfo(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
        o.optJSONObject("ad_info")?.let { patchAdInfo(it, loc) }
        o.optJSONObject("address_component")?.let { patchAddr(it, loc) }
    }

    private fun patchAddr(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
    }

    private fun replaceString(s: String, loc: SelectedLocation): String {
        var r = s
        if (loc.province.isNotEmpty()) r = r.replace(Regex("\"province\"\s*:\s*\"[^\"]*\""), "\"province\":\"" + loc.province + "\"")
        if (loc.city.isNotEmpty()) r = r.replace(Regex("\"city\"\s*:\s*\"[^\"]*\""), "\"city\":\"" + loc.city + "\"")
        if (loc.district.isNotEmpty()) r = r.replace(Regex("\"district\"\s*:\s*\"[^\"]*\""), "\"district\":\"" + loc.district + "\"")
        if (loc.adcode.isNotEmpty()) r = r.replace(Regex("\"adcode\"\s*:\s*\"[^\"]*\""), "\"adcode\":\"" + loc.adcode + "\"")
        return r
    }
}
