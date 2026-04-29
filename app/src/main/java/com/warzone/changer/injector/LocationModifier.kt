package com.warzone.changer.injector

import android.util.Log
import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject

/**
 * 战区修改引擎
 * 拦截并修改位置API响应中的adcode
 */
object LocationModifier {
    
    private const val TAG = "LocationModifier"
    
    // 需要拦截的域名列表
    val INTERCEPT_DOMAINS = setOf(
        "lbs.qq.com",
        "hulubang.qq.com", 
        "api.game.qq.com",
        "tgpa.qq.com",
        "pp.myapp.com",
        "commdata.v.qq.com",
        "sdkapi.tenpay.com",
        "tlog.wegame.com",
        "upcsm.qq.com",
        "is.snssdk.com",
        "api-access.pangolin-sdk-toutiao.com",
        "open.snssdk.com",
        "api.tiktokv.com"
    )
    
    /**
     * 判断是否需要拦截此域名
     */
    fun shouldIntercept(domain: String): Boolean {
        return INTERCEPT_DOMAINS.any { domain.contains(it, ignoreCase = true) }
    }
    
    /**
     * 修改HTTP响应中的adcode相关字段
     */
    fun modifyResponse(responseBody: String, location: SelectedLocation): String {
        return try {
            // 尝试解析JSON并修改adcode
            modifyJsonAdcode(responseBody, location)
        } catch (e: Exception) {
            Log.w(TAG, "Not JSON or parse error, doing string replacement")
            // 备用方案: 直接替换字符串
            doStringReplace(responseBody, location)
        }
    }
    
    private fun modifyJsonAdcode(jsonStr: String, location: SelectedLocation): String {
        try {
            val json = JSONObject(jsonStr)
            
            // 常见的位置API响应结构
            modifyNestedAdcode(json, location)
            
            // result层
            json.optJSONObject("result")?.let { modifyNestedAdcode(it, location) }
            
            // data层
            json.optJSONObject("data")?.let {
                modifyNestedAdcode(it, location)
                it.optJSONObject("result")?.let { r -> modifyNestedAdcode(r, location) }
                it.optJSONObject("ad_info")?.let { r -> modifyAdInfo(r, location) }
            }
            
            return json.toString()
        } catch (e: Exception) {
            throw e
        }
    }
    
    private fun modifyNestedAdcode(obj: JSONObject, location: SelectedLocation) {
        // 修改adcode
        if (location.adcode.isNotEmpty()) {
            obj.opt("adcode")?.let {
                obj.put("adcode", location.adcode)
                obj.put("city_code", location.adcode)
            }
        }
        
        // 修改ad_info
        obj.optJSONObject("ad_info")?.let { modifyAdInfo(it, location) }
        obj.optJSONObject("address_component")?.let { modifyAddrComponent(it, location) }
        obj.optJSONObject("address_info")?.let { modifyAddrComponent(it, location) }
    }
    
    private fun modifyAdInfo(adInfo: JSONObject, location: SelectedLocation) {
        if (location.adcode.isNotEmpty()) adInfo.put("adcode", location.adcode)
        if (location.province.isNotEmpty()) adInfo.put("province", location.province)
        if (location.city.isNotEmpty()) adInfo.put("city", location.city)
        if (location.district.isNotEmpty()) adInfo.put("district", location.district)
    }
    
    private fun modifyAddrComponent(addr: JSONObject, location: SelectedLocation) {
        if (location.adcode.isNotEmpty()) addr.put("adcode", location.adcode)
        if (location.province.isNotEmpty()) addr.put("province", location.province)
        if (location.city.isNotEmpty()) addr.put("city", location.city)
        if (location.district.isNotEmpty()) addr.put("district", location.district)
        
        addr.optJSONObject("ad_info")?.let { modifyAdInfo(it, location) }
        addr.optJSONObject("address_component")?.let { modifyAddrComponent(it, location) }
    }
    
    private fun doStringReplace(text: String, location: SelectedLocation): String {
        var result = text
        if (location.province.isNotEmpty()) {
            result = result.replace(""province"\s*:\s*"[^"]*"" .toRegex(), 
                ""province":"${location.province}"")
        }
        if (location.city.isNotEmpty()) {
            result = result.replace(""city"\s*:\s*"[^"]*"" .toRegex(),
                ""city":"${location.city}"")
        }
        if (location.district.isNotEmpty()) {
            result = result.replace(""district"\s*:\s*"[^"]*"" .toRegex(),
                ""district":"${location.district}"")
        }
        if (location.adcode.isNotEmpty()) {
            result = result.replace(""adcode"\s*:\s*"[^"]*"" .toRegex(),
                ""adcode":"${location.adcode}"")
        }
        return result
    }
}
