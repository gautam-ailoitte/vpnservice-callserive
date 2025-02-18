package com.example.vpnservices

import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    private lateinit var methodChannelManager: MethodChannelManager






    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannelManager = MethodChannelManager(this,activity, flutterEngine)
    }
}
