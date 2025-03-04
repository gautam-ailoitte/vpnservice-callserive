package com.example.vpnservices


import android.media.AudioManager
import android.os.Build
import android.telecom.CallScreeningService
import android.telecom.Call.Details
import android.util.Log
import androidx.annotation.RequiresApi



@RequiresApi(Build.VERSION_CODES.N)
class MyCallManagerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Details) {
        // Check if the call is from a trusted number or based on custom logic
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        Log.d("MyCallManagerService", "Incoming call from: $incomingNumber")

        // For demonstration purposes, let's block all calls except from a specific number
        if (incomingNumber == "+917542036307") {

            // ringing
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // For Android Pie and above, check if DND mode is enabled and override it
                if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                    audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL // Force normal mode
                }
            } else {
                // For older versions, set volume directly
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_RING,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            }
            // Allow call
            val response = CallResponse.Builder()
                .setDisallowCall(false)  // Allow call
                .setRejectCall(false)    // Don't reject
                .setSkipNotification(false) // No notification
                .build()
            respondToCall(callDetails, response)
        } else {
            // Block call
            val response = CallResponse.Builder()
                .setDisallowCall(true)   // Block call
                .setRejectCall(true)     // Reject call
                .setSkipNotification(false) // Notification
                .build()
            respondToCall(callDetails, response)
        }
    }
}
