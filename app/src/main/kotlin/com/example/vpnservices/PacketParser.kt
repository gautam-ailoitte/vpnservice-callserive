package com.example.vpnservices

import android.util.Log
import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import java.nio.ByteBuffer
import kotlin.math.log

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
        if (buffer.remaining() < 12) {
            Log.d("isDNS VPN", "Packet too small to be DNS: Size = ${buffer.remaining()} bytes")
            return false // Minimum DNS header size
        }

        val pos = buffer.position()
        val transactionId = ((buffer.get(pos).toInt() and 0xFF) shl 8) or (buffer.get(pos + 1).toInt() and 0xFF)
        val flags = ((buffer.get(pos + 2).toInt() and 0xFF) shl 8) or (buffer.get(pos + 3).toInt() and 0xFF)
        val qr = (flags shr 15) and 0x1 // QR: 0 = Query, 1 = Response
        val opcode = (flags shr 11) and 0xF // Extract 4-bit Opcode
        val qdCount = ((buffer.get(pos + 4).toInt() and 0xFF) shl 8) or (buffer.get(pos + 5).toInt() and 0xFF)

        // ✅ Log all 12 header bytes in hex for debugging
        val headerBytes = ByteArray(12)
        buffer.position(pos)
        buffer.get(headerBytes)
        Log.d("isDns VPN", "Raw DNS Header: ${headerBytes.joinToString(" ") { String.format("%02X", it) }}")

        // ✅ Log extracted values
        Log.d("isDns VPN", "DNS Header: Transaction ID = $transactionId, QR = $qr, Opcode = $opcode, QDCount = $qdCount")

        if (qdCount > 10) { // Unusual count, likely corruption
            Log.e("isDns VPN", "Suspicious QDCount: $qdCount - DNS packet might be malformed!")
            return false
        }
        val isDns = qr == 0 && qdCount > 0
        Log.d("isDns VPN", "Packet is DNS Query? -> $isDns")
        return isDns  // Ensure it's a valid DNS query
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
