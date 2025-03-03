package com.example.vpnservices

import android.util.Log
import java.nio.ByteBuffer

object PacketParser {
    fun extractDomain(buffer: ByteBuffer): String? {
        try {
            // Make sure we don't modify the buffer position
            val bufferPosition = buffer.position()

            val domain = when {
                isDnsPacket(buffer) -> extractDomainFromDns(buffer)
                isTlsPacket(buffer) -> extractSniFromTls(buffer)
                else -> null
            }

            // Reset buffer position
            buffer.position(bufferPosition)
            return domain
        } catch (e: Exception) {
            Log.e("VPN", "Error extracting domain: ${e.message}", e)
            return null
        }
    }

    private fun isDnsPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 20) {
            return false // Too small for IPv4 header
        }

        // Save original position
        val originalPos = buffer.position()

        try {
            // Get first byte to check IP version
            val firstByte = buffer.get(originalPos).toInt() and 0xFF
            val version = firstByte shr 4

            val isDns = when (version) {
                4 -> isIpv4DnsPacket(buffer, originalPos)
                6 -> isIpv6DnsPacket(buffer, originalPos)
                else -> false
            }

            return isDns
        } finally {
            // Restore original position
            buffer.position(originalPos)
        }
    }

    private fun isIpv4DnsPacket(buffer: ByteBuffer, startPos: Int): Boolean {
        // IPv4 header can be variable length (IHL field)
        val ihl = buffer.get(startPos).toInt() and 0x0F
        if (ihl < 5) return false // Invalid IPv4 header

        val ipHeaderLength = ihl * 4

        // Check if UDP protocol (17)
        val protocol = buffer.get(startPos + 9).toInt() and 0xFF
        if (protocol != 17) return false

        // Check if packet is long enough to contain UDP header
        if (buffer.remaining() < ipHeaderLength + 8) return false

        // Get destination port from UDP header
        val destPort = ((buffer.get(startPos + ipHeaderLength + 2).toInt() and 0xFF) shl 8) or
                (buffer.get(startPos + ipHeaderLength + 3).toInt() and 0xFF)

        // Check if it's a DNS query (port 53)
        return destPort == 53
    }

    private fun isIpv6DnsPacket(buffer: ByteBuffer, startPos: Int): Boolean {
        // IPv6 header is 40 bytes
        if (buffer.remaining() < 48) return false // 40 + 8 (UDP header)

        // Check if next header is UDP (17)
        val nextHeader = buffer.get(startPos + 6).toInt() and 0xFF
        if (nextHeader != 17) return false

        // Get destination port from UDP header
        val destPort = ((buffer.get(startPos + 40 + 2).toInt() and 0xFF) shl 8) or
                (buffer.get(startPos + 40 + 3).toInt() and 0xFF)

        // Check if it's a DNS query (port 53)
        return destPort == 53
    }

    private fun extractDomainFromDns(buffer: ByteBuffer): String? {
        // Save position
        val originalPos = buffer.position()

        try {
            // Find DNS payload (after IP and UDP headers)
            var pos = originalPos

            // Check IP version
            val version = buffer.get(pos).toInt() shr 4

            // Skip IP header
            pos += if (version == 4) {
                // IPv4: variable header length
                (buffer.get(pos).toInt() and 0x0F) * 4
            } else {
                // IPv6: fixed 40-byte header
                40
            }

            // Skip UDP header (8 bytes)
            pos += 8

            // Now at DNS header
            // Skip transaction ID, flags, counts (12 bytes)
            pos += 12

            // Now at DNS question section
            buffer.position(pos)

            // Read domain name parts
            val domainParts = mutableListOf<String>()
            while (true) {
                val length = buffer.get().toInt() and 0xFF
                if (length == 0) break // End of domain name

                // Read label
                val labelBytes = ByteArray(length)
                buffer.get(labelBytes)
                domainParts.add(String(labelBytes))
            }

            return if (domainParts.isNotEmpty()) {
                domainParts.joinToString(".")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VPN", "Error parsing DNS packet: ${e.message}", e)
            return null
        } finally {
            // Restore position
            buffer.position(originalPos)
        }
    }

    private fun isTlsPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 20) return false

        // Save position
        val originalPos = buffer.position()

        try {
            // Check IP version
            val version = buffer.get(originalPos).toInt() shr 4

            // Skip IP header
            var pos = originalPos
            pos += if (version == 4) {
                // IPv4: variable header length
                (buffer.get(pos).toInt() and 0x0F) * 4
            } else if (version == 6) {
                // IPv6: fixed 40-byte header
                40
            } else {
                return false
            }

            // Check protocol (TCP = 6)
            val protocol = if (version == 4) {
                buffer.get(originalPos + 9).toInt() and 0xFF
            } else {
                buffer.get(originalPos + 6).toInt() and 0xFF
            }

            if (protocol != 6) return false // Not TCP

            // Skip TCP header (variable length)
            // TCP header length is in the 12th byte of TCP header, upper 4 bits
            val tcpHeaderLength = ((buffer.get(pos + 12).toInt() and 0xF0) shr 4) * 4
            pos += tcpHeaderLength

            // Check TLS handshake record
            // TLS Record Type 0x16 = Handshake
            if (buffer.remaining() < pos - originalPos + 5) return false

            return buffer.get(pos).toInt() == 0x16 &&
                    buffer.get(pos + 1).toInt() == 0x03 && // TLS version major
                    buffer.get(pos + 5).toInt() == 0x01    // Handshake type 1 = Client Hello
        } catch (e: Exception) {
            return false
        } finally {
            // Restore position
            buffer.position(originalPos)
        }
    }

    private fun extractSniFromTls(buffer: ByteBuffer): String? {
        // Save position
        val originalPos = buffer.position()

        try {
            // Find TLS payload (after IP and TCP headers)
            var pos = originalPos

            // Check IP version
            val version = buffer.get(pos).toInt() shr 4

            // Skip IP header
            pos += if (version == 4) {
                // IPv4: variable header length
                (buffer.get(pos).toInt() and 0x0F) * 4
            } else if (version == 6) {
                // IPv6: fixed 40-byte header
                40
            } else {
                return null
            }

            // Skip TCP header
            val tcpHeaderLength = ((buffer.get(pos + 12).toInt() and 0xF0) shr 4) * 4
            pos += tcpHeaderLength

            // Now at TLS record
            buffer.position(pos)

            // Check TLS record type (0x16 = Handshake)
            if (buffer.get().toInt() != 0x16) return null

            // Skip version (2 bytes) and length (2 bytes)
            buffer.position(buffer.position() + 4)

            // Check handshake type (1 = Client Hello)
            if (buffer.get().toInt() != 0x01) return null

            // Skip handshake length (3 bytes), TLS version (2 bytes), and random (32 bytes)
            buffer.position(buffer.position() + 37)

            // Skip session ID
            val sessionIdLength = buffer.get().toInt() and 0xFF
            buffer.position(buffer.position() + sessionIdLength)

            // Skip cipher suites
            val cipherSuitesLength = (buffer.get().toInt() and 0xFF) shl 8
            buffer.position(buffer.position() + cipherSuitesLength)

            // Skip compression methods
            val compressionMethodsLength = buffer.get().toInt() and 0xFF
            buffer.position(buffer.position() + compressionMethodsLength)

            // Check if we have extensions
            if (buffer.remaining() < 2) return null

            // Get extensions length
            val extensionsLength = (buffer.get().toInt() and 0xFF) shl 8

            // Read extensions
            var remaining = extensionsLength
            while (remaining > 4) {
                val extensionType = (buffer.get().toInt() and 0xFF) shl 8
                val extensionLength = (buffer.get().toInt() and 0xFF) shl 8

                // SNI extension type is 0x0000
                if (extensionType == 0) {
                    // Skip list length (2 bytes) and name type (1 byte)
                    buffer.position(buffer.position() + 3)

                    // Read hostname length
                    val hostnameLength = (buffer.get().toInt() and 0xFF) shl 8

                    // Read hostname
                    val hostnameBytes = ByteArray(hostnameLength)
                    buffer.get(hostnameBytes)

                    return String(hostnameBytes)
                } else {
                    // Skip this extension
                    buffer.position(buffer.position() + extensionLength)
                }

                remaining -= (extensionLength + 4)
            }

            return null
        } catch (e: Exception) {
            Log.e("VPN", "Error parsing TLS packet: ${e.message}", e)
            return null
        } finally {
            // Restore position
            buffer.position(originalPos)
        }
    }
}