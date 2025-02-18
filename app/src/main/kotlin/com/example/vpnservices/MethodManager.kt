package com.example.vpnservices

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MethodChannelManager(private val context: Context, private val activity: Activity, flutterEngine: FlutterEngine) {
    private val CHANNEL = "web_blocker"
    private lateinit var dnsServer: DNSServer


    init {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "enableAccessibilityService" -> {
                    openAccessibilitySettings()
                    result.success("Opened Accessibility Settings")
                }
                else -> result.notImplemented()
            }
        }

    }
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }


}