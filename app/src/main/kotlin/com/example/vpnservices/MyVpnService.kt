package com.example.vpnservices

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import javax.net.ssl.HttpsURLConnection


class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val blockedSites = BlocklistManager.getBlockedSites()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (VpnService.prepare(this) != null) {
            Log.e("VPN", "VPN Permission is NOT granted!")
            stopSelf()
            return START_NOT_STICKY
        }
        runVpn()
        return START_STICKY
    }

    private fun runVpn() {
        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setBlocking(true)
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .setSession("MyVPN")
        vpnInterface = builder.establish()
        Log.d("VPN", "VPN Interface established: $vpnInterface")

        vpnInterface?.fileDescriptor?.let { fd ->
            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)
            val buffer = ByteBuffer.allocate(65535)

            while (!Thread.interrupted()) {
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)

                    val domain = PacketParser.extractDomain(buffer)
                    if (domain != null) {
                        Log.d("VPN", "Accessed: $domain")

                        if (blockedSites.contains(domain)) {
                            Log.d("VPN", "Blocked: $domain")
                            outputStream.write(ByteArray(length)) // Send empty response
                            outputStream.flush()
                            continue
                        }
                    }

                    buffer.rewind()
                    outputStream.write(buffer.array(), 0, length)
                    outputStream.flush()
                }
                buffer.clear()
            }
        }
    }

    override fun onDestroy() {
        vpnInterface?.close()
    }
}

