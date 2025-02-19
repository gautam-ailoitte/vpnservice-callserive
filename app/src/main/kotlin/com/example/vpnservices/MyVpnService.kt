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

        val vpnFileDescriptor = vpnInterface?.fileDescriptor ?: return
        val outputStream = FileOutputStream(vpnFileDescriptor)
        val outputChannel = outputStream.channel

        val packetReader = PacketReader(vpnFileDescriptor, outputChannel)
        Thread(packetReader).start()
    }

    override fun onDestroy() {
        vpnInterface?.close()
    }
}

