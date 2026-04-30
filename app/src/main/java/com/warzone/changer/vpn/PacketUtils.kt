package com.warzone.changer.vpn

import java.nio.ByteBuffer

object PacketUtils {
    data class IpHeader(
        val version: Int, val headerLen: Int, val totalLen: Int,
        val protocol: Int, val srcIp: String, val dstIp: String,
        val srcIpBytes: ByteArray, val dstIpBytes: ByteArray
    )
    data class TcpHeader(
        val srcPort: Int, val dstPort: Int, val seqNum: Long, val ackNum: Long,
        val headerLen: Int, val flags: Int, val window: Int
    )
    const val SYN = 0x02
    const val ACK = 0x10
    const val FIN = 0x01
    const val RST = 0x04
    const val PSH = 0x08

    fun parseIp(data: ByteArray): IpHeader? {
        if (data.size < 20) return null
        val verIhl = data[0].toInt() and 0xFF
        val version = verIhl shr 4
        if (version != 4) return null
        val ihl = (verIhl and 0x0F) * 4
        val totalLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val protocol = data[9].toInt() and 0xFF
        val src = "${data[12].toInt() and 0xFF}.${data[13].toInt() and 0xFF}.${data[14].toInt() and 0xFF}.${data[15].toInt() and 0xFF}"
        val dst = "${data[16].toInt() and 0xFF}.${data[17].toInt() and 0xFF}.${data[18].toInt() and 0xFF}.${data[19].toInt() and 0xFF}"
        return IpHeader(version, ihl, totalLen, protocol, src, dst,
            data.copyOfRange(12, 16), data.copyOfRange(16, 20))
    }

    fun parseTcp(data: ByteArray, offset: Int): TcpHeader? {
        if (data.size < offset + 20) return null
        val srcPort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        val dstPort = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
        val seq = ((data[offset + 4].toInt() and 0xFF).toLong() shl 24) or
                  ((data[offset + 5].toInt() and 0xFF).toLong() shl 16) or
                  ((data[offset + 6].toInt() and 0xFF).toLong() shl 8) or
                  (data[offset + 7].toInt() and 0xFF).toLong()
        val ack = ((data[offset + 8].toInt() and 0xFF).toLong() shl 24) or
                  ((data[offset + 9].toInt() and 0xFF).toLong() shl 16) or
                  ((data[offset + 10].toInt() and 0xFF).toLong() shl 8) or
                  (data[offset + 11].toInt() and 0xFF).toLong()
        val dataOff = ((data[offset + 12].toInt() and 0xFF) shr 4) * 4
        val flags = data[offset + 13].toInt() and 0xFF
        val window = ((data[offset + 14].toInt() and 0xFF) shl 8) or (data[offset + 15].toInt() and 0xFF)
        return TcpHeader(srcPort, dstPort, seq, ack, dataOff, flags, window)
    }

    fun ipChecksum(header: ByteArray): Int {
        var sum = 0L
        for (i in header.indices step 2) {
            sum += ((header[i].toInt() and 0xFF) shl 8) or (if (i + 1 < header.size) (header[i + 1].toInt() and 0xFF) else 0)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    fun tcpChecksum(srcIp: ByteArray, dstIp: ByteArray, tcpData: ByteArray): Int {
        var sum = 0L
        for (i in srcIp.indices step 2) sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i+1].toInt() and 0xFF)
        for (i in dstIp.indices step 2) sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i+1].toInt() and 0xFF)
        sum += 6 // TCP protocol
        sum += tcpData.size
        for (i in tcpData.indices step 2) sum += ((tcpData[i].toInt() and 0xFF) shl 8) or (if (i+1 < tcpData.size) (tcpData[i+1].toInt() and 0xFF) else 0)
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
