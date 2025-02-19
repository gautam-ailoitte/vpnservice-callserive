package com.example.vpnservices



import android.util.Log
import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import java.nio.ByteBuffer

object PacketParser {
    fun extractDomain(buffer: ByteBuffer): String? {
        return when {
            isDnsPacket(buffer) -> extractDomainFromDns(buffer)
            isTlsHandshake(buffer) -> extractSniFromTls(buffer)
            else -> null
        }
    }

    private fun isDnsPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 12) return false // Minimum DNS header size is 12 bytes
        val data = buffer.array()

        val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val qr = flags shr 15 // Query (0) or Response (1)

        return qr == 0 // Only process DNS queries
    }
    private fun extractDomainFromDns(packetData: ByteBuffer): String? {
        return try {
            val dnsMessage = Message(packetData)
            val question: Record? = dnsMessage.getSectionArray(Section.QUESTION).firstOrNull()

            if (question == null) {
                Log.e("VPN", "DNS packet does not contain a valid question section")
                return null
            }
            question.name.toString(true)
        } catch (e: Exception) {
            Log.e("VPN", "Failed to parse DNS: ${e.message}")
            null
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

