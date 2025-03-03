package com.example.vpnservices

import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class PacketReader(
    private val vpnFileDescriptor: FileDescriptor,
    private val outputChannel: FileChannel
) : Runnable {

    override fun run() {
        val buffer = ByteBuffer.allocate(32767)
        val inputStream = FileInputStream(vpnFileDescriptor)
        val inputChannel = inputStream.channel

        try {
            while (!Thread.interrupted()) {
                // Clear the buffer before reading
                buffer.clear()

                // Read the packet
                val bytesRead = inputChannel.read(buffer)

                if (bytesRead <= 0) {
                    Thread.sleep(100) // Prevent CPU spinning
                    continue
                }

                // Prepare buffer for reading
                buffer.flip()

                // Create a copy of the buffer for domain extraction
                val bufferCopy = buffer.duplicate()

                // Extract domain without modifying buffer positions
                val domain = PacketParser.extractDomain(bufferCopy)

                // By default, allow the packet unless we know it's for a blocked domain
                var shouldBlock = false

                if (domain != null) {
                    // Log the detected domain
                    Log.d("VPN", "Detected domain: $domain")

                    // Check if this domain should be blocked
                    shouldBlock = isBlockedDomain(domain)

                    if (shouldBlock) {
                        Log.d("VPN", "Blocked packet for domain: $domain - Size: $bytesRead bytes")
                    }
                }

                if (!shouldBlock) {
                    // Important: Make sure we're at the beginning of the buffer before writing
                    buffer.rewind()
                    buffer.position(0)
                    outputChannel.write(buffer)

                    // Only log domains we've detected (avoid excessive logging)
                    if (domain != null) {
                        Log.d("VPN", "Forwarded allowed packet - Size: $bytesRead bytes")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VPN", "Error in packet processing: ${e.message}", e)
        }
    }

    private fun isBlockedDomain(domain: String): Boolean {
        val blockedSites = BlocklistManager.getBlockedSites()
        val normalizedDomain = domain.trim().lowercase()

        // Check for exact domain match
        if (blockedSites.contains(normalizedDomain)) {
            Log.d("VPN", "Blocked exact match: $domain")
            return true
        }

        // Check for subdomain matches
        for (blockedSite in blockedSites) {
            // Ensure we match complete domain parts
            if (normalizedDomain == blockedSite ||
                normalizedDomain.endsWith(".$blockedSite")) {
                Log.d("VPN", "Blocked domain: $domain (matches $blockedSite)")
                return true
            }
        }

        return false
    }
}