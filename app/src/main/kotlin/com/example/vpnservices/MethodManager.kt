package com.example.vpnservices

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

@RequiresApi(Build.VERSION_CODES.Q)
class MethodChannelManager(
    private val context: Context,
    private val activity: Activity,
    flutterEngine: FlutterEngine
) {
    private val CHANNEL = "web_blocker"
    private val REQUEST_ID = 1
    private val VPN_REQUEST_CODE = 100  // ✅ Defined request code

    init {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startCallScreening" -> {
                        requestCallScreeningRole()
                        result.success("Call Screening Started")
                    }

                    "stopCallScreening" -> {
                        stopCallScreening()
                        result.success("Call Screening Stopped")
                    }

                    "startVpn" -> {
                        val vpnIntent = VpnService.prepare(context) // ✅ FIXED: Use `context`
                        if (vpnIntent != null) {
                            activity.startActivityForResult(vpnIntent, VPN_REQUEST_CODE) // ✅ FIXED: Call from `activity`
                        } else {
                            startVpnService()
                        }
                        result.success("VPN Started")
                    }

                    "stopVpn" -> {
                        stopVpnService()
                        result.success("VPN Stopped")
                    }

                    else -> result.notImplemented()
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestCallScreeningRole() {
        val roleManager = context.getSystemService(ROLE_SERVICE) as RoleManager

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                Log.d("CallScreening", "Requesting role")
                activity.startActivityForResult(intent, REQUEST_ID)
            } else {
                Log.d("CallScreening", "Role already held")
                startCallScreening()
            }
        } else {
            Log.e("CallScreening", "Call Screening role is not available on this device")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ID) {
            if (resultCode == Activity.RESULT_OK) {
                startCallScreening()
            } else {
                Log.d("CallScreening", "Failed to take role")
            }
        }

        if (requestCode == VPN_REQUEST_CODE) { // ✅ Handle VPN permission result
            if (resultCode == Activity.RESULT_OK) {
                startVpnService()
            } else {
                Log.e("VPN", "User denied VPN permission")
            }
        }
    }

    private fun startCallScreening() {
        val intent = Intent(context, MyCallManagerService::class.java)
        activity.startService(intent)
    }

    private fun stopCallScreening() {
        val intent = Intent(context, MyCallManagerService::class.java)
        activity.stopService(intent)
    }

    private fun startVpnService() {
        val intent = Intent(context, MyVpnService::class.java)
        intent.action = VpnService.SERVICE_INTERFACE
        activity.startService(intent)
        Log.d("VPN", "VPN Service Started")
    }

    private fun stopVpnService() {
        val intent = Intent(context, MyVpnService::class.java)
        activity.stopService(intent)
        Log.d("VPN", "VPN Service Stopped")
    }
}
