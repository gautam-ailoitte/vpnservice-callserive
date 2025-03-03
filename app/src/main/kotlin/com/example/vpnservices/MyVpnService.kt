package com.example.vpnservices

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetProcessingThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // Check if VPN is already running
        if (vpnInterface != null) {
            Log.d("VPN", "VPN already running")
            return START_STICKY
        }

        // Check VPN permission
        if (prepare(this) != null) {
            Log.e("VPN", "VPN permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start the VPN service
        setupVpn()
        return START_STICKY
    }

    private fun setupVpn() {
        try {
            Log.d("VPN", "Setting up VPN")

            // Configure the VPN
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)  // IPv4 traffic
                .addDnsServer("8.8.8.8") // Google DNS
                .setMtu(1500)            // Standard MTU size
                .setSession("Domain Blocker VPN")
                .setBlocking(true)

            // Allow apps to bypass the VPN if needed
            // This is optional but helps with troubleshooting
            // builder.addDisallowedApplication(packageName)

            // Establish the VPN interface
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e("VPN", "Failed to establish VPN interface")
                stopSelf()
                return
            }

            Log.d("VPN", "VPN interface established")

            // Setup packet processing
            val vpnFileDescriptor = vpnInterface!!.fileDescriptor
            val outputStream = FileOutputStream(vpnFileDescriptor)
            val outputChannel = outputStream.channel

            // Start processing packets in a background thread
            val packetReader = PacketReader(vpnFileDescriptor, outputChannel)
            packetProcessingThread = Thread(packetReader, "VPN-PacketProcessor")
            packetProcessingThread?.start()

            Log.d("VPN", "VPN service started successfully")
        } catch (e: Exception) {
            Log.e("VPN", "Error setting up VPN: ${e.message}", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.d("VPN", "Stopping VPN service")

        // Stop the packet processing thread
        packetProcessingThread?.interrupt()

        try {
            // Wait for thread to terminate
            packetProcessingThread?.join(500)
        } catch (e: InterruptedException) {
            Log.w("VPN", "Interrupted while waiting for packet processing thread to stop")
        }

        // Close the VPN interface
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e("VPN", "Error closing VPN interface: ${e.message}", e)
        }

        vpnInterface = null
        packetProcessingThread = null

        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}