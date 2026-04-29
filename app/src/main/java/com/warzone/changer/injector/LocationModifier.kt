package com.warzone.changer.injector

import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject

object LocationModifier {
    fun modifyResponse(body: String, loc: SelectedLocation): String {
        return try { modifyJson(body, loc) } catch (e: Exception) { simpleReplace(body, loc) }
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
    }

    private fun patchAddr(o: JSONObject, loc: SelectedLocation) {
        if (loc.adcode.isNotEmpty()) o.put("adcode", loc.adcode)
        if (loc.province.isNotEmpty()) o.put("province", loc.province)
        if (loc.city.isNotEmpty()) o.put("city", loc.city)
        if (loc.district.isNotEmpty()) o.put("district", loc.district)
    }

    private fun simpleReplace(s: String, loc: SelectedLocation): String {
        var r = s
        if (loc.province.isNotEmpty()) r = simpleVal(r, "province", loc.province)
        if (loc.city.isNotEmpty()) r = simpleVal(r, "city", loc.city)
        if (loc.district.isNotEmpty()) r = simpleVal(r, "district", loc.district)
        if (loc.adcode.isNotEmpty()) r = simpleVal(r, "adcode", loc.adcode)
        return r
    }

    private fun simpleVal(json: String, key: String, newVal: String): String {
        val search = """ + key + """
        var idx = json.indexOf(search)
        if (idx < 0) return json
        val colonIdx = json.indexOf(":", idx + search.length)
        if (colonIdx < 0) return json
        var valStart = json.indexOf(""", colonIdx + 1)
        if (valStart < 0) return json
        val valEnd = json.indexOf(""", valStart + 1)
        if (valEnd < 0) return json
        return json.substring(0, valStart + 1) + newVal + json.substring(valEnd)
    }
}
