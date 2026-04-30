package com.warzone.changer.injector

import android.content.Context
import android.util.Log
import com.github.megatronking.netbare.http.HttpRequest
import com.github.megatronking.netbare.http.HttpResponse
import com.github.megatronking.netbare.http.HttpResponseBodyInterceptor
import com.warzone.changer.data.LocationStore
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * 腾讯地图API响应拦截器
 * 
 * 核心原理：
 * 1. 王者荣耀请求 http://apis.map.qq.com/ws/geocoder/v1 走HTTP明文
 * 2. VPN拦截后，替换响应中的 adcode 为目标区域编码
 * 3. 游戏读到修改后的 adcode → 设置战区
 */
class LocationInjector(private val context: Context) : HttpResponseBodyInterceptor() {

    companion object {
        private const val TAG = "LocationInjector"
        private const val TARGET_HOST = "apis.map.qq.com"
        private const val TARGET_PATH = "/ws/geocoder/v1"
    }

    private val bodyAccumulator = ByteArrayOutputStream()
    private var isTarget = false

    override fun intercept(
        request: HttpRequest,
        response: HttpResponse,
        buffer: ByteBuffer,
        chain: Chain
    ) {
        val host = request.host()
        val url = request.url()
        
        if (!isTarget && isTargetRequest(host, url)) {
            isTarget = true
            Log.i(TAG, "✓ 识别到腾讯地图API请求: $host$url")
        }
        
        if (isTarget) {
            // 累积真实响应体（但不使用）
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bodyAccumulator.write(bytes)
            
            // 当收到完整响应时，替换为假数据
            val contentLen = response.getHeader("Content-Length")?.toIntOrNull() ?: 0
            if (contentLen > 0 && bodyAccumulator.size() >= contentLen) {
                val location = LocationStore.getSelectedLocation(context)
                if (location != null) {
                    val fakeBody = buildFakeResponse(location.adcode, location.name)
                    val fakeBytes = fakeBody.toByteArray(Charset.forName("UTF-8"))
                    Log.i(TAG, "→ 替换响应: adcode=${location.adcode}, 区域=${location.name}")
                    
                    response.setHeader("Content-Length", fakeBytes.size.toString())
                    chain.process(request, response, ByteBuffer.wrap(fakeBytes))
                    bodyAccumulator.reset()
                    isTarget = false
                    return
                }
            }
            
            // 还没接收完，继续累积
            // 如果没有Content-Length，用chunk模式的结束标记
            if (contentLen == 0 && bodyAccumulator.size() > 0) {
                val accumulated = bodyAccumulator.toString("UTF-8")
                if (accumulated.trimEnd().endsWith("}")) {
                    val location = LocationStore.getSelectedLocation(context)
                    if (location != null) {
                        val fakeBody = buildFakeResponse(location.adcode, location.name)
                        val fakeBytes = fakeBody.toByteArray(Charset.forName("UTF-8"))
                        Log.i(TAG, "→ 替换响应(chunked): adcode=${location.adcode}")
                        
                        response.setHeader("Content-Length", fakeBytes.size.toString())
                        chain.process(request, response, ByteBuffer.wrap(fakeBytes))
                        bodyAccumulator.reset()
                        isTarget = false
                        return
                    }
                }
            }
            
            // 不传递原始数据（吞掉真实响应）
            chain.process(request, response, ByteBuffer.allocate(0))
        } else {
            // 非目标请求，正常转发
            chain.process(request, response, buffer)
        }
    }

    private fun isTargetRequest(host: String?, url: String?): Boolean {
        if (host == null || url == null) return false
        return host.contains(TARGET_HOST) && url.contains(TARGET_PATH)
    }

    /**
     * 构造假的腾讯地图API响应
     * 格式与真实API一致，只修改 adcode 字段
     */
    private fun buildFakeResponse(adcode: String, regionName: String): String {
        val location = getLocationByAdcode(adcode)
        val province = getProvinceByAdcode(adcode)
        val city = getCityByAdcode(adcode)
        val district = regionName
        
        val response = JSONObject().apply {
            put("status", 0)
            put("message", "Success")
            put("request_id", generateRequestId())
            
            put("result", JSONObject().apply {
                put("address_components", JSONObject().apply {
                    put("nation", "中国")
                    put("province", province)
                    put("city", city)
                    put("district", district)
                    put("street", "")
                    put("street_number", "")
                })
                
                put("ad_info", JSONObject().apply {
                    put("nation_code", "156")
                    put("adcode", adcode)       // ★ 核心字段
                    put("city_code", adcode.take(4) + "00")
                    put("name", regionName)
                    put("location", location)
                    put("nation", "中国")
                    put("province", province)
                    put("city", city)
                    put("district", district)
                })
                
                put("location", location)
                put("formatted_addresses", JSONObject().apply {
                    put("recommend", "")
                    put("rough", "")
                })
                put("address_reference", JSONObject())
            })
        }
        
        return response.toString()
    }

    private fun getLocationByAdcode(adcode: String): JSONObject {
        val latLng = when {
            adcode.startsWith("11") -> Pair(39.9042, 116.4074)
            adcode.startsWith("31") -> Pair(31.2304, 121.4737)
            adcode.startsWith("44") -> Pair(23.1291, 113.2644)
            adcode.startsWith("33") -> Pair(30.2741, 120.1551)
            adcode.startsWith("32") -> Pair(32.0617, 118.7778)
            adcode.startsWith("51") -> Pair(30.5728, 104.0668)
            adcode.startsWith("50") -> Pair(29.5630, 106.5516)
            adcode.startsWith("42") -> Pair(30.5928, 114.3055)
            adcode.startsWith("43") -> Pair(28.2282, 112.9388)
            adcode.startsWith("35") -> Pair(26.0745, 119.2965)
            adcode.startsWith("36") -> Pair(28.6820, 115.8579)
            adcode.startsWith("34") -> Pair(31.8612, 117.2830)
            adcode.startsWith("37") -> Pair(36.6683, 116.9972)
            adcode.startsWith("41") -> Pair(34.7472, 113.6254)
            adcode.startsWith("13") -> Pair(38.0428, 114.5149)
            adcode.startsWith("14") -> Pair(37.8706, 112.5489)
            adcode.startsWith("21") -> Pair(41.8057, 123.4315)
            adcode.startsWith("22") -> Pair(43.8868, 125.3245)
            adcode.startsWith("23") -> Pair(45.8038, 126.5350)
            adcode.startsWith("15") -> Pair(40.8183, 111.7656)
            adcode.startsWith("61") -> Pair(34.2658, 108.9541)
            adcode.startsWith("62") -> Pair(36.0594, 103.8343)
            adcode.startsWith("63") -> Pair(36.6171, 101.7782)
            adcode.startsWith("64") -> Pair(38.4872, 106.2309)
            adcode.startsWith("65") -> Pair(43.7930, 87.6271)
            adcode.startsWith("53") -> Pair(25.0389, 102.7183)
            adcode.startsWith("52") -> Pair(26.6470, 106.6302)
            adcode.startsWith("45") -> Pair(22.8170, 108.3665)
            adcode.startsWith("46") -> Pair(20.0174, 110.3492)
            adcode.startsWith("54") -> Pair(29.6500, 91.1000)
            else -> Pair(39.9042, 116.4074)
        }
        return JSONObject().apply {
            put("lat", latLng.first)
            put("lng", latLng.second)
        }
    }

    private fun getProvinceByAdcode(adcode: String): String {
        return when {
            adcode.startsWith("11") -> "北京市"
            adcode.startsWith("31") -> "上海市"
            adcode.startsWith("44") -> "广东省"
            adcode.startsWith("33") -> "浙江省"
            adcode.startsWith("32") -> "江苏省"
            adcode.startsWith("51") -> "四川省"
            adcode.startsWith("50") -> "重庆市"
            adcode.startsWith("42") -> "湖北省"
            adcode.startsWith("43") -> "湖南省"
            adcode.startsWith("35") -> "福建省"
            adcode.startsWith("36") -> "江西省"
            adcode.startsWith("34") -> "安徽省"
            adcode.startsWith("37") -> "山东省"
            adcode.startsWith("41") -> "河南省"
            adcode.startsWith("13") -> "河北省"
            adcode.startsWith("14") -> "山西省"
            adcode.startsWith("21") -> "辽宁省"
            adcode.startsWith("22") -> "吉林省"
            adcode.startsWith("23") -> "黑龙江省"
            adcode.startsWith("15") -> "内蒙古自治区"
            adcode.startsWith("61") -> "陕西省"
            adcode.startsWith("62") -> "甘肃省"
            adcode.startsWith("63") -> "青海省"
            adcode.startsWith("64") -> "宁夏回族自治区"
            adcode.startsWith("65") -> "新疆维吾尔自治区"
            adcode.startsWith("53") -> "云南省"
            adcode.startsWith("52") -> "贵州省"
            adcode.startsWith("45") -> "广西壮族自治区"
            adcode.startsWith("46") -> "海南省"
            adcode.startsWith("54") -> "西藏自治区"
            else -> "未知"
        }
    }

    private fun getCityByAdcode(adcode: String): String {
        // 根据adcode前6位可以精确到市
        return "城市"
    }

    private fun generateRequestId(): String {
        val chars = "abcdef0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}