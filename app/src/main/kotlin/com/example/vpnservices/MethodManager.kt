package com.example.vpnservices

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

@RequiresApi(Build.VERSION_CODES.Q)
class MethodChannelManager(private val context: Context, private val activity: Activity, flutterEngine: FlutterEngine) {
    private val CHANNEL = "web_blocker"
    private val REQUEST_ID = 1
    init {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {

                "startCallScreening"->{
                    requestCallScreeningRole()
                    result.success("Call Screening Started")
                }"stopCallScreening"->{
                stopCallScreening()
                result.success("Call Screening Stopped")
            }
                "startVpn" -> {
                    startVpnService()
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
    private fun requestCallScreeningRole() { // No need for result parameter here
        val roleManager = context.getSystemService(ROLE_SERVICE) as RoleManager

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) { // Check if the role is *already* held
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                Log.d("role takeover", "Requesting role")
                activity.startActivityForResult(intent, REQUEST_ID)
            } else {
                // We already have the role! Start the service directly
                Log.d("role takeover", "Role already held")
                startCallScreening()
            }
        } else {
            // Handle the case where the role isn't available
            Log.e("CallScreening", "Call Screening role is not available on this device")
            // Consider showing a message to the user
        }
    }



    // In onActivityResult:
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ID) {
            if (resultCode == Activity.RESULT_OK) {
                // Role granted! Start the service
                startCallScreening()
            } else {
                // Role denied. Handle this gracefully.
                Log.d("role takeover", "Failed to take role")
                // Inform the user, maybe with a dialog
            }
        }
    }

    private fun startCallScreening() {
        // This will activate your call screening service
        val intent = Intent()
        intent.setClass(context, MyCallManagerService::class.java)
        activity.startService(intent)
    }


    private fun stopCallScreening() {
        // Stop the call screening service if necessary
        val intent = Intent()
        intent.setClass(context, MyCallManagerService::class.java)
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