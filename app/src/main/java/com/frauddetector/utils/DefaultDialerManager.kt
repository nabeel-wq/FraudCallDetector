package com.frauddetector.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages default dialer status and requests.
 */
class DefaultDialerManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "DefaultDialerManager"
        private const val REQUEST_CODE_SET_DEFAULT_DIALER = 2001
    }
    
    /**
     * Check if app is currently the default dialer.
     */
    fun isDefaultDialer(): Boolean {
        val telecomManager = activity.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        val isDefault = defaultDialer == activity.packageName
        
        Log.d(TAG, "Is default dialer: $isDefault (current: $defaultDialer, app: ${activity.packageName})")
        return isDefault
    }
    
    /**
     * Request to become the default dialer.
     */
    fun requestDefaultDialer() {
        if (isDefaultDialer()) {
            Log.d(TAG, "Already default dialer")
            return
        }
        
        Log.d(TAG, "Requesting default dialer role")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - use RoleManager
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    activity.startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
                }
            } else {
                Log.e(TAG, "ROLE_DIALER not available on this device")
            }
        } else {
            // Android 9 and below - use TelecomManager
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
        }
    }
    
    /**
     * Handle result from default dialer request.
     * Call this from Activity's onActivityResult.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CODE_SET_DEFAULT_DIALER) {
            return false
        }
        
        if (resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "User accepted default dialer request")
            return true
        } else {
            Log.w(TAG, "User declined default dialer request")
            // TODO: Show dialog explaining why default dialer is needed
            return false
        }
    }
    
    /**
     * Show educational dialog explaining why app needs to be default dialer.
     */
    fun showEducationalDialog() {
        // TODO: Implement dialog
        // Explain that app needs to be default dialer to:
        // - Auto-answer calls
        // - Analyze calls in real-time
        // - Protect user from scam calls
    }
}
