package com.warzone.changer.vpn

import android.util.Log
import com.warzone.changer.model.SelectedLocation
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

class LocalHttpProxy(private val port: Int = 18080) {
    companion object {
        private const val TAG = "LocalHttpProxy"
    }
    
    private var serverSocket: ServerSocket? = null
    @Volatile var running = false; private set
    @Volatile var targetLocation: SelectedLocation? = null
    
    fun start() {
        running = true
        Thread({
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "Proxy started on port " + port)
                while (running) {
                    val client = serverSocket?.accept() ?: continue
                    Thread({ handleClient(client) }).start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }, "http-proxy").start()
    }
    
    private fun handleClient(client: Socket) {
        try {
            val input = client.getInputStream().buffered()
            val output = client.getOutputStream()
            
            val requestLine = readLine(input) ?: return
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (true) {
                line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] = line.substring(colonIdx + 1).trim()
                }
            }
            
            val host = headers["host"] ?: ""
            if (host.isEmpty() || (!host.contains("apis.map.qq.com") && !host.contains("lbs.qq.com"))) {
                client.close(); return
            }
            
            Log.d(TAG, "Intercepting request to " + host)
            
            val realHost = host.split(":")[0]
            val realPort = if (host.contains(":")) host.split(":")[1].toInt() else 80
            val remote = Socket(realHost, realPort)
            val remoteOut = remote.getOutputStream()
            val remoteIn = remote.getInputStream()
            
            val rebuilt = StringBuilder()
            rebuilt.append(requestLine.replace("http://" + host, "")).append("
")
            for ((k, v) in headers) {
                rebuilt.append(k).append(": ").append(v).append("
")
            }
            rebuilt.append("
")
            remoteOut.write(rebuilt.toString().toByteArray())
            remoteOut.flush()
            
            val responseLine = readLine(remoteIn) ?: ""
            val respHeaders = StringBuilder()
            respHeaders.append(responseLine).append("
")
            
            var contentLen = 0
            var chunked = false
            val respHeaderMap = mutableMapOf<String, String>()
            while (true) {
                line = readLine(remoteIn) ?: break
                if (line.isEmpty()) break
                respHeaders.append(line).append("
")
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    respHeaderMap[key] = value
                    if (key == "content-length") contentLen = value.toIntOrNull() ?: 0
                    if (key == "transfer-encoding" && value.contains("chunked")) chunked = true
                }
            }
            
            var body = ""
            if (contentLen > 0) {
                val buf = ByteArray(contentLen)
                var read = 0
                while (read < contentLen) {
                    val n = remoteIn.read(buf, read, contentLen - read)
                    if (n < 0) break
                    read += n
                }
                body = String(buf, 0, read, Charsets.UTF_8)
            } else if (chunked) {
                body = readChunked(remoteIn)
            }
            
            remote.close()
            
            val loc = targetLocation
            if (loc != null && body.isNotEmpty()) {
                try {
                    body = LocationModifier.modifyResponse(body, loc)
                    Log.d(TAG, "Modified response adcode to " + loc.adcode)
                } catch (e: Exception) {
                    Log.w(TAG, "Modify failed", e)
                }
            }
            
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val resp = StringBuilder()
            resp.append(responseLine).append("
")
            resp.append("content-length: ").append(bodyBytes.size.toString()).append("
")
            for ((k, v) in respHeaderMap) {
                if (k != "content-length" && k != "transfer-encoding") {
                    resp.append(k).append(": ").append(v).append("
")
                }
            }
            resp.append("
")
            output.write(resp.toString().toByteArray())
            output.write(bodyBytes)
            output.flush()
            client.close()
        } catch (e: Exception) {
            Log.w(TAG, "Client error", e)
            try { client.close() } catch (_: Exception) {}
        }
    }
    
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = 0
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == 0x0A && prev == 0x0D) {
                return sb.dropLast(1).toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }
    
    private fun readChunked(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.trim().toIntOrNull(16) ?: break
            if (size == 0) { readLine(input); break }
            val buf = ByteArray(size)
            var read = 0
            while (read < size) {
                val n = input.read(buf, read, size - read)
                if (n < 0) break
                read += n
            }
            sb.append(String(buf, 0, read, Charsets.UTF_8))
            readLine(input)
        }
        return sb.toString()
    }
    
    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
