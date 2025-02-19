package com.example.vpnservices

import android.util.Log
import java.nio.ByteBuffer

object PacketParser {
    fun extractDomain(buffer: ByteBuffer): String? {
        Log.d("VPN", "Received Packet - Size: ${buffer.remaining()} bytes")

        return when {
            isDnsPacket(buffer) ->extractDomainManually(buffer)
            isTlsHandshake(buffer) -> extractSniFromTls(buffer)
            else -> {
                Log.d("not process", "Not valid packet")
                null
            }
        }
    }


    private fun isDnsPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 20) { // Ensure it's at least big enough for an IPv4 header
            Log.d("isDNS VPN", "Packet too small to be IPv4/IPv6: Size = ${buffer.remaining()} bytes")
            return false
        }

        val pos = buffer.position()
        val firstByte = buffer.get(pos).toInt() and 0xF0

        return when (firstByte shr 4) {
            4 -> checkIpv4UdpDns(buffer, pos) // IPv4 Packet
            6 -> checkIpv6UdpDns(buffer, pos) // IPv6 Packet
            else -> {
                Log.d("isDNS VPN", "Unknown packet type")
                false
            }
        }
    }

    private fun checkIpv4UdpDns(buffer: ByteBuffer, pos: Int): Boolean {
        // IPv4 Header Length
        val ipHeaderLength = (buffer.get(pos).toInt() and 0x0F) * 4
        val protocol = buffer.get(pos + 9).toInt() and 0xFF // Protocol field (TCP=6, UDP=17)
        val srcIp = "${buffer.get(pos + 12).toInt() and 0xFF}.${buffer.get(pos + 13).toInt() and 0xFF}.${buffer.get(pos + 14).toInt() and 0xFF}.${buffer.get(pos + 15).toInt() and 0xFF}"

        if (protocol != 17) { // UDP = 17
            Log.d("isDNS VPN", "Ignoring non-UDP packet from $srcIp")
            return false
        }

        val udpStart = pos + ipHeaderLength
        val destPort = ((buffer.get(udpStart + 2).toInt() and 0xFF) shl 8) or (buffer.get(udpStart + 3).toInt() and 0xFF)

        if (destPort != 53) {
            Log.d("isDNS VPN", "Ignoring non-DNS UDP packet on port $destPort")
            return false
        }

        buffer.position(udpStart + 8) // Move to UDP payload (DNS header)
        return validateDnsHeader(buffer)
    }

    private fun checkIpv6UdpDns(buffer: ByteBuffer, pos: Int): Boolean {
        if (buffer.remaining() < 40) return false // Minimum IPv6 header size

        val protocol = buffer.get(pos + 6).toInt() and 0xFF // Next Header field
        if (protocol != 17) { // UDP = 17
            Log.d("isDNS VPN", "Ignoring non-UDP IPv6 packet")
            return false
        }

        val udpStart = pos + 40
        val destPort = ((buffer.get(udpStart + 2).toInt() and 0xFF) shl 8) or (buffer.get(udpStart + 3).toInt() and 0xFF)

        if (destPort != 53) {
            Log.d("isDNS VPN", "Ignoring non-DNS UDP packet on port $destPort")
            return false
        }

        buffer.position(udpStart + 8) // Move to UDP payload (DNS header)
        return validateDnsHeader(buffer)
    }

    private fun validateDnsHeader(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 12) return false

        val transactionId = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
        val flags = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)
        val qr = (flags shr 15) and 0x1 // QR: 0 = Query, 1 = Response
        val opcode = (flags shr 11) and 0xF // Extract 4-bit Opcode
        val qdCount = ((buffer.get().toInt() and 0xFF) shl 8) or (buffer.get().toInt() and 0xFF)

        Log.d("isDns VPN", "DNS Header: Transaction ID = $transactionId, QR = $qr, Opcode = $opcode, QDCount = $qdCount")

        return qr == 0 && qdCount > 0
    }






    private fun extractDomainManually(buffer: ByteBuffer): String? {
        try {
            buffer.position(12) // Skip DNS header (first 12 bytes)
            val domainParts = mutableListOf<String>()

            while (true) {
                val length = buffer.get().toInt() and 0xFF // Read length byte
                if (length == 0) break // End of domain name

                val labelBytes = ByteArray(length)
                buffer.get(labelBytes)
                domainParts.add(String(labelBytes, Charsets.UTF_8))
            }
            Log.d("VPN", "Extracted Domain: ${ domainParts.joinToString(".")}")
            return domainParts.joinToString(".")
        } catch (e: Exception) {
            Log.e("VPN", "Failed to manually extract domain: ${e.message}")
            return null
        }
    }




    private fun isTlsHandshake(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 6) return false
        return buffer[0] == 0x16.toByte() && buffer[5] == 0x01.toByte()
    }

    private fun extractSniFromTls(buffer: ByteBuffer): String? {
        try {
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            var index = 43 // Start at handshake payload
            while (index < data.size - 5) {
                if (data[index] == 0x00.toByte() && data[index + 1] == 0x00.toByte()) {
                    val length = ((data[index + 3].toInt() and 0xFF) shl 8) or (data[index + 4].toInt() and 0xFF)
                    if (index + 5 + length <= data.size) {
                        return String(data, index + 5, length, Charsets.UTF_8)
                    }
                }
                index++
            }
        } catch (e: Exception) {
            Log.e("VPN", "Failed to parse TLS SNI", e)
        }
        return null
    }
}
