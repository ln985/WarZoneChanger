package com.warzone.changer.injector

import android.content.Context
import android.net.Uri
import com.github.megatronking.netbare.http.HttpBody
import com.github.megatronking.netbare.http.HttpMethod
import com.github.megatronking.netbare.http.HttpRequest
import com.github.megatronking.netbare.http.HttpRequestHeaderPart
import com.github.megatronking.netbare.http.HttpResponse
import com.github.megatronking.netbare.http.HttpResponseHeaderPart
import com.github.megatronking.netbare.injector.InjectorCallback
import com.github.megatronking.netbare.injector.SimpleHttpInjector
import com.github.megatronking.netbare.stream.ByteStream
import com.github.megatronking.netbare.stream.Stream
import com.warzone.changer.data.LocationStore
import com.warzone.changer.model.SelectedLocation
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream

/**
 * 腾讯地图API响应注入器
 * 拦截 https://apis.map.qq.com/ws/geocoder/v1 请求
 * 修改返回的地理位置数据
 */
class LocationInjector(private val context: Context) : SimpleHttpInjector() {

    companion object {
        private const val TAG = "LocationInterceptor"
        private val PREFIXES = listOf(
            "https://apis.map.qq.com/ws/geocoder/v1",
            "http://apis.map.qq.com/ws/geocoder/v1"
        )
    }

    private var mHoldResponseHeader: HttpResponseHeaderPart? = null

    // ===== 请求拦截：修改URL中的location参数 =====
    override fun sniffRequest(request: HttpRequest): Boolean {
        val url = request.url()
        val method = request.method().toString().uppercase(Locale.ROOT)
        return PREFIXES.any { url.startsWith(it) } && method == "GET"
    }

    override fun sniffResponse(response: HttpResponse): Boolean {
        val url = response.url()
        val method = response.method().toString().uppercase(Locale.ROOT)
        return PREFIXES.any { url.startsWith(it) } && method == "GET"
    }

    override fun onRequestInject(header: HttpRequestHeaderPart, callback: InjectorCallback) {
        val location = getCurrentLocation()
        if (location != null) {
            val path = header.path()
            val modifiedPath = modifyLocationInUrl(path!!, location.locationParam())
            val newHeader = header.newBuilder()
                .uri(Uri.parse(modifiedPath))
                .build()
            callback.onFinished(newHeader as Stream)
        } else {
            callback.onFinished(header as Stream)
        }
    }

    // ===== 响应拦截：修改返回的JSON数据 =====
    override fun onResponseInject(header: HttpResponseHeaderPart, callback: InjectorCallback) {
        mHoldResponseHeader = header
    }

    override fun onResponseInject(response: HttpResponse, body: HttpBody, callback: InjectorCallback) {
        if (mHoldResponseHeader == null) {
            callback.onFinished(body as Stream)
            return
        }

        try {
            val rawBytes = readBodyBytes(body)
            if (rawBytes.isEmpty()) {
                sendOriginDataAndClear(rawBytes, callback)
                return
            }

            // 解码响应体
            val originalJson = decodeResponseBody(rawBytes, response)
            // 修改地理位置数据
            val modifiedJson = modifyGeocoderResponse(originalJson)

            // 如果内容没有变化，直接返回原始数据
            if (modifiedJson == originalJson) {
                sendOriginDataAndClear(rawBytes, callback)
                return
            }

            // 重新编码
            val modifiedBytes = encodeResponseBody(modifiedJson, response)

            // 修改Content-Length
            val newHeader = mHoldResponseHeader!!.newBuilder()
                .removeHeader("Transfer-Encoding")
                .replaceHeader("Content-Length", modifiedBytes.size.toString())
                .build()

            callback.onFinished(newHeader as Stream)
            callback.onFinished(ByteStream(modifiedBytes) as Stream)
        } catch (e: Exception) {
            sendOriginDataAndClear(null, callback, body)
        } finally {
            mHoldResponseHeader = null
        }
    }

    // ===== 核心：修改腾讯地图API响应 =====
    private fun modifyGeocoderResponse(originalJsonText: String): String {
        try {
            val location = getCurrentLocation() ?: return originalJsonText

            val root = JSONObject(originalJsonText)
            val result = root.optJSONObject("result") ?: return originalJsonText

            // 修改 ad_info（行政区划信息）
            val adInfo = result.optJSONObject("ad_info")
            if (adInfo != null) {
                if (location.province.isNotEmpty()) {
                    adInfo.put("province", location.province)
                }
                if (location.city.isNotEmpty()) {
                    adInfo.put("city", location.city)
                }
                if (location.district.isNotEmpty()) {
                    adInfo.put("district", location.district)
                }
                if (location.adcode.isNotEmpty()) {
                    adInfo.put("adcode", location.adcode)
                }
            }

            // 修改 address_component（地址组件）
            val addressComponent = result.optJSONObject("address_component")
            if (addressComponent != null) {
                if (location.province.isNotEmpty()) {
                    addressComponent.put("province", location.province)
                }
                if (location.city.isNotEmpty()) {
                    addressComponent.put("city", location.city)
                }
                if (location.district.isNotEmpty()) {
                    addressComponent.put("district", location.district)
                }
            }

            // 修改 formatted_addresses
            val formattedAddrs = result.optJSONObject("formatted_addresses")
            if (formattedAddrs != null) {
                val addr = formatAddress(
                    location.province,
                    location.city,
                    location.district
                )
                formattedAddrs.put("recommend", addr)
                formattedAddrs.put("rough", addr)
            }

            // 修改 address（完整地址）
            if (location.province.isNotEmpty() || location.city.isNotEmpty()) {
                val addr = formatAddress(location.province, location.city, location.district)
                result.put("address", addr)
            }

            // 修改 location（坐标）
            val resultLocation = result.optJSONObject("location")
            if (resultLocation != null && location.latitude != 0.0) {
                resultLocation.put("lat", location.latitude)
                resultLocation.put("lng", location.longitude)
            }

            // 修改 title（广告信息中的名称）
            val adInfo2 = result.optJSONObject("ad_info")
            if (adInfo2 != null) {
                val nameParts = listOfNotNull(
                    location.province.takeIf { it.isNotEmpty() },
                    location.city.takeIf { it.isNotEmpty() },
                    location.district.takeIf { it.isNotEmpty() }
                )
                if (nameParts.isNotEmpty()) {
                    adInfo2.put("name", nameParts.joinToString(","))
                }
            }

            return root.toString()
        } catch (e: Exception) {
            return originalJsonText
        }
    }

    // ===== 从LocationStore获取当前虚拟位置 =====
    private fun getCurrentLocation(): SelectedLocation? = LocationStore.get(context)

    // ===== 修改URL中的location参数 =====
    private fun modifyLocationInUrl(originalUrl: String, newLocation: String): String {
        try {
            val uri = Uri.parse(originalUrl)
            val builder = uri.buildUpon().clearQuery()
            val paramNames = uri.queryParameterNames

            if (paramNames.isNotEmpty()) {
                for (name in paramNames) {
                    if (name == "location") {
                        builder.appendQueryParameter("location", newLocation)
                    } else {
                        for (value in uri.getQueryParameters(name)) {
                            builder.appendQueryParameter(name, value)
                        }
                    }
                }
                if (!paramNames.contains("location")) {
                    builder.appendQueryParameter("location", newLocation)
                }
            } else {
                builder.appendQueryParameter("location", newLocation)
            }
            return builder.build().toString()
        } catch (e: Exception) {
            // 正则回退方案
            val regex = Regex("location=[^&]+")
            val replacement = "location=$newLocation"
            return if (regex.containsMatchIn(originalUrl)) {
                regex.replace(originalUrl, replacement)
            } else if (originalUrl.contains("?")) {
                "$originalUrl&location=$newLocation"
            } else {
                "$originalUrl?location=$newLocation"
            }
        }
    }

    // ===== 地址格式化 =====
    private fun formatAddress(province: String, city: String, district: String): String {
        val normProvince = normalizeSpecialRegionName(province)
        val normCity = normalizeSpecialRegionName(city)
        val normDistrict = district.trim()

        val parts = if ((normProvince == "香港特别行政区" || normProvince == "澳门特别行政区")
            && normProvince == normCity) {
            listOf(normProvince, normDistrict)
        } else {
            listOf(normProvince, normCity, normDistrict)
        }
        return parts.filter { it.isNotEmpty() }.joinToString("")
    }

    private fun normalizeSpecialRegionName(name: String): String {
        val trimmed = name.trim()
        return when (trimmed) {
            "香港" -> "香港特别行政区"
            "澳门" -> "澳门特别行政区"
            else -> trimmed
        }
    }

    // ===== 编解码工具 =====

    private fun readBodyBytes(body: HttpBody): ByteArray {
        val input = com.github.megatronking.netbare.io.HttpBodyInputStream(body)
        return input.use { it.readBytes() }
    }

    private fun decodeResponseBody(data: ByteArray, response: HttpResponse): String {
        if (data.isEmpty()) return "<empty body>"

        val encoding = response.responseHeader("Content-Encoding")
            ?.firstOrNull()?.lowercase(Locale.ROOT)

        val decoded = when {
            encoding?.contains("gzip") == true -> decompressGzip(data)
            encoding?.contains("deflate") == true || encoding?.contains("zlib") == true -> decompressDeflate(data)
            else -> data
        }

        val charset = extractCharset(response) ?: Charsets.UTF_8
        return String(decoded, charset)
    }

    private fun encodeResponseBody(text: String, response: HttpResponse): ByteArray {
        val charset = extractCharset(response) ?: Charsets.UTF_8
        var bytes = text.toByteArray(charset)

        val encoding = response.responseHeader("Content-Encoding")
            ?.firstOrNull()?.lowercase(Locale.ROOT)

        when {
            encoding?.contains("gzip") == true -> bytes = compressGzip(bytes)
            encoding?.contains("deflate") == true || encoding?.contains("zlib") == true -> bytes = compressDeflate(bytes)
        }
        return bytes
    }

    private fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun compressDeflate(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompressGzip(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    private fun decompressDeflate(data: ByteArray): ByteArray {
        return InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    private fun extractCharset(response: HttpResponse): Charset? {
        val contentType = response.responseHeader("Content-Type")?.firstOrNull() ?: return null
        val charsetStr = contentType.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim('"', '\'')
            ?.takeIf { it.isNotEmpty() }
        return try { charsetStr?.let { Charset.forName(it) } } catch (e: Exception) { null }
    }

    private fun sendOriginDataAndClear(
        rawBytes: ByteArray?,
        callback: InjectorCallback,
        body: HttpBody? = null
    ) {
        mHoldResponseHeader?.let { callback.onFinished(it as Stream) }
        if (rawBytes != null) {
            callback.onFinished(ByteStream(rawBytes) as Stream)
        } else if (body != null) {
            callback.onFinished(body as Stream)
        }
        mHoldResponseHeader = null
    }
}