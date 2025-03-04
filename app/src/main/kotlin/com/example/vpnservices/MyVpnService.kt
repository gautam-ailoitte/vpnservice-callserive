package com.example.vpnservices

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log

class MyVpnService : VpnService() {
    companion object {
        private const val TAG = "ContentBlockerVPN"
        var isActive = false

        // IP protocol constants
        private const val IP_PROTOCOL_TCP = 6
        private const val IP_PROTOCOL_UDP = 17
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var executorService: ExecutorService? = null
    private var blockedDomains: Set<String> = setOf()

    // Connection tracking with domain info
    private val blockedConnections = ConcurrentHashMap<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        // Load blocked domains from SharedPreferences
        val prefs = getSharedPreferences("ContentBlockerPrefs", Context.MODE_PRIVATE)
        blockedDomains = prefs.getStringSet("blockedDomains", setOf()) ?: setOf()
        Log.i(TAG, "Loaded blocked domains: $blockedDomains")


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            stopSelf()
            isActive = false
            Log.i(TAG, "VPN service stopped")
            return START_NOT_STICKY
        }


        // Start VPN
        startVpn()
        isActive = true
        Log.i(TAG, "VPN service started")
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
        Log.i(TAG, "VPN has been revoked")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        isActive = false
        Log.i(TAG, "VPN service destroyed")
    }

    private fun startVpn() {
        try {
            // Configure the VPN
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setSession("Content Blocker VPN")
                .setBlocking(true)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN connection")
                return
            }

            running.set(true)
            executorService = Executors.newFixedThreadPool(2)

            // Start packet processing
            executorService?.execute {
                processPackets()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
        }
    }

    private fun stopVpn() {
        running.set(false)
        executorService?.shutdown()
        executorService = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        blockedConnections.clear()
    }

    private fun processPackets() {
        val buffer = ByteBuffer.allocate(32767)

        try {
            val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)

            while (running.get()) {
                // Reset buffer before each read
                buffer.clear()

                // Read packet from VPN interface
                val length = inputStream.read(buffer.array())
                val duplicate = length
                if (length <= 0) {
                    Thread.sleep(10)
                    Log.v(TAG, "No data to read")
                    continue
                }

                buffer.limit(length)

                // Process the packet
                try {
                    // Get IP header info
                    val ipVersion = buffer.get(0).toInt() shr 4 and 0xF

                    // Only process IPv4 packets
                    if (ipVersion != 4) {
                        outputStream.write(buffer.array(), 0, length)
                        continue
                    }

                    val ipHeaderLength = (buffer.get(0).toInt() and 0xF) * 4

                    // Get source and destination IP
                    val sourceIp = ByteArray(4)
                    val destIp = ByteArray(4)
                    buffer.position(12)
                    buffer.get(sourceIp)
                    buffer.get(destIp)

                    // Get protocol
                    buffer.position(9)
                    val protocol = buffer.get().toInt() and 0xFF

                    // Check if this is a DNS packet (UDP protocol and destination port 53)
                    if (protocol == IP_PROTOCOL_UDP) {
                        val sourcePort = ((buffer.get(ipHeaderLength).toInt() and 0xFF) shl 8) or
                                (buffer.get(ipHeaderLength + 1).toInt() and 0xFF)
                        val destPort = ((buffer.get(ipHeaderLength + 2).toInt() and 0xFF) shl 8) or
                                (buffer.get(ipHeaderLength + 3).toInt() and 0xFF)

                        // DNS query (UDP port 53)
                        if (destPort == 53) {
                            val domain = extractDomainFromDnsPacket(buffer, ipHeaderLength + 8)

                            if (domain != null && isBlocked(domain)) {
                                Log.i(TAG, "BLOCKED DNS: $domain")
                                // Add to blocked connections
                                val key = getConnectionKey(sourceIp, destIp)
                                blockedConnections[key] = true
                                continue // Drop the packet
                            }
                        }
                    }
                    // Check if this is a TCP packet going to port 80 or 443 (HTTP/HTTPS)
                    else if (protocol == IP_PROTOCOL_TCP) {
                        val sourcePort = ((buffer.get(ipHeaderLength).toInt() and 0xFF) shl 8) or
                                (buffer.get(ipHeaderLength + 1).toInt() and 0xFF)
                        val destPort = ((buffer.get(ipHeaderLength + 2).toInt() and 0xFF) shl 8) or
                                (buffer.get(ipHeaderLength + 3).toInt() and 0xFF)

                        // TCP header length
                        val tcpHeaderLength = ((buffer.get(ipHeaderLength + 12).toInt() and 0xF0) shr 4) * 4

                        // TCP flags
                        val tcpFlags = buffer.get(ipHeaderLength + 13).toInt() and 0xFF
                        val isSyn = (tcpFlags and 0x02) != 0

                        // For new connections, check if domain is blocked
                        if (isSyn) {
                            val key = getConnectionKey(sourceIp, destIp)
                            if (blockedConnections[key] == true) {
                                Log.v(TAG, "Dropping TCP packet for blocked connection: $key")
                                continue // Drop the packet
                            }
                        }

                        // If HTTP traffic, check Host header
                        if (destPort == 80) {
                            val payloadOffset = ipHeaderLength + tcpHeaderLength
                            val domain = extractHostFromHttpHeader(buffer, payloadOffset)

                            if (domain != null && isBlocked(domain)) {
                                Log.i(TAG, "BLOCKED HTTP: $domain")
                                val key = getConnectionKey(sourceIp, destIp)
                                blockedConnections[key] = true
                                continue // Drop the packet
                            }
                        }
                    }

                    // Forward the packet if not blocked
                    outputStream.write(buffer.array(), 0, length)

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet", e)
                        e.printStackTrace()
                    // Forward the packet on error
                    outputStream.write(buffer.array(), 0, length)
                }
            }

        } catch (e: Exception) {
            if (running.get()) {
                Log.e(TAG, "Error in packet processing", e)
            }
        }
    }

    // Extract domain from DNS packet
    private fun extractDomainFromDnsPacket(buffer: ByteBuffer, dnsStart: Int): String? {
        try {
            // Position at the start of the DNS query section (after header)
            buffer.position(dnsStart + 12)

            val domainParts = mutableListOf<String>()
            var length = buffer.get().toInt() and 0xFF

            // Maximum number of parts to prevent infinite loops on malformed packets
            var partsCount = 0
            val maxParts = 50

            // Parse domain parts
            while (length > 0 && partsCount < maxParts) {
                val bytes = ByteArray(length)
                buffer.get(bytes)
                domainParts.add(String(bytes))
                length = buffer.get().toInt() and 0xFF
                partsCount++
            }

            return if (domainParts.isNotEmpty()) {
                val domain = domainParts.joinToString(".")
                Log.v(TAG, "Extracted domain: $domain")
                domain
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from DNS packet", e)
            return null
        }
    }

    // Extract Host header from HTTP request
    private fun extractHostFromHttpHeader(buffer: ByteBuffer, offset: Int): String? {
        try {
            // Check if there's enough data to read
            if (buffer.limit() <= offset) {
                return null
            }

            // Position buffer after TCP header at start of HTTP request
            buffer.position(offset)

            // Read up to 1024 bytes of the HTTP header
            val maxHeaderSize = Math.min(buffer.remaining(), 1024)
            val headerBytes = ByteArray(maxHeaderSize)
            buffer.get(headerBytes)

            // Convert to string
            val headers = String(headerBytes)

            // Look for Host header using regex
            val hostRegex = "Host:\\s*([^\\r\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = hostRegex.find(headers)

            return matchResult?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting HTTP Host header", e)
            return null
        }
    }

    // Check if domain is in our block list
    private fun isBlocked(domain: String): Boolean {
        return blockedDomains.any { blockedDomain ->
            domain.equals(blockedDomain, ignoreCase = true) ||
                    domain.endsWith(".$blockedDomain", ignoreCase = true)
        }
    }

    // Helper function to get connection key
    private fun getConnectionKey(srcIp: ByteArray, dstIp: ByteArray): String {
        return "${InetAddress.getByAddress(srcIp).hostAddress}-${InetAddress.getByAddress(dstIp).hostAddress}"
    }
}