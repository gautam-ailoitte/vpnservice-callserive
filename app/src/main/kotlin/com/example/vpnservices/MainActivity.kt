package com.example.vpnservices

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.net.VpnService
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class MainActivity: FlutterActivity() {
    private val TAG = "ContentBlocker"
    private val CHANNEL = "com.example.content_blocker/vpn"
    private val VPN_REQUEST_CODE = 100
    private var pendingStartVpn = false
    private var blockedDomains: ArrayList<String> = ArrayList()

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            when (call.method) {
                "startVpn" -> {
                    @Suppress("UNCHECKED_CAST")
                    blockedDomains = (call.argument<List<String>>("blockedDomains") ?: ArrayList()) as ArrayList<String>

                    Log.i(TAG, "Starting VPN with blocked domains: $blockedDomains")

                    // Save blocked domains to shared preferences
                    val prefs = getSharedPreferences("ContentBlockerPrefs", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    editor.putStringSet("blockedDomains", blockedDomains.toSet())
                    editor.apply()

                    val vpnIntent = VpnService.prepare(this)
                    if (vpnIntent != null) {
                        pendingStartVpn = true
                        startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
                        result.success(null)
                    } else {
                        startVpnService()
                        result.success(null)
                    }
                }
                "stopVpn" -> {
                    Log.i(TAG, "Stopping VPN service")
                    val intent = Intent(this,  MyVpnService::class.java)
                    intent.action = "STOP"
                    startService(intent)
                    result.success(null)
                }
//                "isVpnActive" -> {
//                    result. MyVpnService.isActive)
//                }
                "getBlockedDomainLogs" -> {
                    // This would require implementation of a method to retrieve logs
                    // For now, we'll return an empty list
                    result.success(ArrayList<String>())
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (pendingStartVpn) {
                Log.i(TAG, "VPN permission granted, starting service")
                startVpnService()
                pendingStartVpn = false
            }
        } else if (requestCode == VPN_REQUEST_CODE) {
            Log.w(TAG, "VPN permission denied")
            pendingStartVpn = false
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        intent.action = "START"
        startService(intent)
    }
}