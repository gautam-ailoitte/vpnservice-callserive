package com.example.vpnservices

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val blockedSites = listOf(
        "example.com", "blockedwebsite.com", "facebook.com"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VPN", "onStartCommand: Starting VPN service.")
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        Log.d("VPN", "startVpn: Configuring VPN.")
        val builder = Builder()
        builder.setSession("MyVPN")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)

        val intent = Intent(this, MyVpnService::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        builder.setConfigureIntent(pendingIntent)
        vpnInterface = builder.establish()

        vpnInterface?.fileDescriptor?.let { fd ->
            val intFd = getFdFromFileDescriptor(fd)
            protect(intFd)
        }

        Log.d("VPN", "VPN Interface established.")
        startPacketProcessing()
    }

    private fun getFdFromFileDescriptor(fd: FileDescriptor): Int {
        try {
            val field = FileDescriptor::class.java.getDeclaredField("descriptor")
            field.isAccessible = true
            return field.get(fd) as Int
        } catch (e: Exception) {
            Log.e("VPN", "Error getting file descriptor: ${e.message}")
            e.printStackTrace()
        }
        return -1
    }

    private fun startPacketProcessing() {
        Log.d("VPN", "startPacketProcessing: Listening to packets.")
        vpnInterface?.fileDescriptor?.let { fd ->
            val input = FileInputStream(fd).channel
            val output = FileOutputStream(fd).channel
            val buffer = ByteBuffer.allocate(32767)

            while (true) {
                buffer.clear()
                val length = input.read(buffer)
                if (length > 0) {
                    buffer.flip()
                    val siteAccessed = parseDomain(buffer)
                    Log.d("VPN", "Accessed site: $siteAccessed")

                    if (blockedSites.contains(siteAccessed)) {
                        Log.d("VPN", "Blocked site accessed: $siteAccessed")
                        sendLogToServer(siteAccessed)
                        continue // Drop packet
                    }
                    output.write(buffer) // Forward traffic if not blocked
                }
            }
        }
    }

    private fun parseDnsQuery(buffer: ByteBuffer): String {
        val dnsQuery = ByteArray(buffer.remaining())
        buffer.get(dnsQuery)

        if (dnsQuery.size >= 12) {
            var domain = ""
            var index = 12

            while (index < dnsQuery.size) {
                val length = dnsQuery[index].toInt() and 0xFF
                if (length == 0) break
                index++

                val domainPart = String(dnsQuery, index, length)
                domain += if (domain.isEmpty()) domainPart else ".$domainPart"
                index += length
            }

            return domain
        }
        return ""
    }

    private fun parseHttpHostHeader(buffer: ByteBuffer): String {
        val httpRequest = ByteArray(buffer.remaining())
        buffer.get(httpRequest)

        val requestString = String(httpRequest)
        val hostHeader = "Host: "

        if (requestString.contains(hostHeader)) {
            val startIndex = requestString.indexOf(hostHeader) + hostHeader.length
            val endIndex = requestString.indexOf("\r\n", startIndex)

            if (startIndex != -1 && endIndex != -1) {
                return requestString.substring(startIndex, endIndex)
            }
        }
        return ""
    }

    private fun parseDomain(buffer: ByteBuffer): String {
        val domainFromDns = parseDnsQuery(buffer)
        if (domainFromDns.isNotEmpty()) return domainFromDns

        val domainFromHttp = parseHttpHostHeader(buffer)
        if (domainFromHttp.isNotEmpty()) return domainFromHttp

        return "Unknown"
    }

    private fun sendLogToServer(site: String) {
        Log.d("VPN", "sendLogToServer: Sending log for blocked site: $site")
        // Implement backend logging
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        Log.d("VPN", "onDestroy: VPN service destroyed and interface closed.")
    }
}
