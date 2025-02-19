package com.example.vpnservices

import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class PacketReader(
    private val vpnFileDescriptor: FileDescriptor,
    private val outputChannel: FileChannel // For forwarding packets
) : Runnable {

    override fun run() {
        val inputStream = FileInputStream(vpnFileDescriptor)
        val channel = inputStream.channel
        val buffer = ByteBuffer.allocate(32767)


        try {
            while (!Thread.interrupted()) { // Graceful exit when stopped
                buffer.clear()
                val bytesRead = channel.read(buffer)
                if (bytesRead <= 0) {
                    Log.d("VPN", "Ignoring empty packet")
                    continue // Skip processing
                }
                if (bytesRead > 0) {
                    buffer.flip()
                    Log.d("packet readerVPN", "Received Packet - Size: $bytesRead bytes")
                    if (!processPacket(buffer)) {
//                        Log.d("packet reader","domain allowed")
                        buffer.rewind()
                        outputChannel.write(buffer) // Forward allowed packets
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("VPN packet reader ", "Exception in PacketReader: ${e.message}")
            e.printStackTrace()
        }


    }

    private fun processPacket(buffer: ByteBuffer): Boolean {
        Log.d("processPacket before", "Received Packet - Size: ${buffer.remaining()} bytes")

        val duplicateBuffer = buffer.duplicate() // Create a copy before reading
        val rawData = ByteArray(duplicateBuffer.remaining())
        duplicateBuffer.get(rawData) // Read from duplicate, leaving the original intact

        Log.d("processPacket after", "Received Packet - Size: ${buffer.remaining()} bytes") // Should remain the same
        // ✅ Reset duplicateBuffer position before passing it to extractDomain
        duplicateBuffer.rewind()
        Log.d("duplicatePacket after", "Received Packet - Size: ${duplicateBuffer.remaining()} bytes")
        val domain = PacketParser.extractDomain(duplicateBuffer) // Use duplicate to avoid empty buffer issue

        if (domain==null){
            Log.d("VPN process packet", "Domain is null")

        }
        if (domain != null) {
            Log.d("VPN", "Detected Domain: $domain")

            if (isBlockedDomain(domain)) {
                Log.d("VPN", "Blocked: $domain")
                return true // Return true to indicate packet should be dropped
            }
        }
        return false // Return false to forward packet
    }

    private fun isBlockedDomain(domain: String): Boolean {
        return BlocklistManager.getBlockedSites().contains(domain)
    }
}
