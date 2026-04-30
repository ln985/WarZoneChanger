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
                while (running) {
                    val client = serverSocket?.accept() ?: continue
                    Thread({ handleClient(client) }).start()
                }
            } catch (e: Exception) { Log.e(TAG, "Server error", e) }
        }, "http-proxy").start()
    }

    private fun handleClient(client: Socket) {
        try {
            val inp = client.getInputStream().buffered()
            val out = client.getOutputStream()
            val reqLine = readLine(inp) ?: return
            val hdrs = mutableMapOf<String, String>()
            var ln: String?
            while (true) {
                ln = readLine(inp) ?: break
                if (ln.isEmpty()) break
                val ci = ln.indexOf(":")
                if (ci > 0) hdrs[ln.substring(0, ci).trim().lowercase()] = ln.substring(ci + 1).trim()
            }
            val host = hdrs["host"] ?: ""
            if (host.isEmpty() || (!host.contains("apis.map.qq.com") && !host.contains("lbs.qq.com"))) { client.close(); return }
            val rHost = host.split(":")[0]
            val rPort = if (host.contains(":")) host.split(":")[1].toInt() else 80
            val remote = Socket(rHost, rPort)
            val rOut = remote.getOutputStream()
            val rIn = remote.getInputStream()
            val req = StringBuilder()
            req.append(reqLine.replace("http://" + host, "")).append("\r\n")
            for ((k, v) in hdrs) req.append(k).append(": ").append(v).append("\r\n")
            req.append("\r\n")
            rOut.write(req.toString().toByteArray())
            rOut.flush()
            val respLine = readLine(rIn) ?: ""
            val rh = StringBuilder(); rh.append(respLine).append("\r\n")
            var cl = 0; var ch = false
            val rhm = mutableMapOf<String, String>()
            while (true) {
                ln = readLine(rIn) ?: break
                if (ln.isEmpty()) break
                rh.append(ln).append("\r\n")
                val ci2 = ln.indexOf(":")
                if (ci2 > 0) {
                    val k2 = ln.substring(0, ci2).trim().lowercase()
                    val v2 = ln.substring(ci2 + 1).trim()
                    rhm[k2] = v2
                    if (k2 == "content-length") cl = v2.toIntOrNull() ?: 0
                    if (k2 == "transfer-encoding" && v2.contains("chunked")) ch = true
                }
            }
            var body = ""
            if (cl > 0) {
                val buf = ByteArray(cl); var rd = 0
                while (rd < cl) { val n = rIn.read(buf, rd, cl - rd); if (n < 0) break; rd += n }
                body = String(buf, 0, rd, Charsets.UTF_8)
            } else if (ch) { body = readChunked(rIn) }
            remote.close()
            val loc = targetLocation
            if (loc != null && body.isNotEmpty()) {
                try { body = LocationModifier.modifyResponse(body, loc) }
                catch (e: Exception) { Log.w(TAG, "Modify failed", e) }
            }
            val bb = body.toByteArray(Charsets.UTF_8)
            val rsp = StringBuilder()
            rsp.append(respLine).append("\r\n")
            rsp.append("content-length: " + bb.size + "\r\n")
            for ((k3, v3) in rhm) { if (k3 != "content-length" && k3 != "transfer-encoding") rsp.append(k3).append(": ").append(v3).append("\r\n") }
            rsp.append("\r\n")
            out.write(rsp.toString().toByteArray())
            out.write(bb)
            out.flush()
            client.close()
        } catch (e: Exception) { try { client.close() } catch (_: Exception) {} }
    }

    private fun readLine(i: InputStream): String? {
        val s = StringBuilder(); var p = 0
        while (true) {
            val b = i.read(); if (b < 0) return if (s.isEmpty()) null else s.toString()
            if (b == 0x0A && p == 0x0D) return s.dropLast(1).toString()
            s.append(b.toChar()); p = b
        }
    }

    private fun readChunked(i: InputStream): String {
        val s = StringBuilder()
        while (true) {
            val sz = readLine(i) ?: break
            val szI = sz.trim().toIntOrNull(16) ?: break
            if (szI == 0) { readLine(i); break }
            val buf = ByteArray(szI); var rd = 0
            while (rd < szI) { val n = i.read(buf, rd, szI - rd); if (n < 0) break; rd += n }
            s.append(String(buf, 0, rd, Charsets.UTF_8))
            readLine(i)
        }
        return s.toString()
    }

    fun stop() { running = false; try { serverSocket?.close() } catch (_: Exception) {}; serverSocket = null }
}