package com.example

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object SamsungBatteryHelper {
    private const val TAG = "SamsungBatteryHelper"

    /**
     * Guides the user directly to the app's specific details screen (App Info)
     * so they can easily set the battery usage to "Unrestricted" on modern Android.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Successfully launched ACTION_APPLICATION_DETAILS_SETTINGS for Unrestricted battery setup")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch App Info direct settings, trying standard ACTION_SETTINGS as fallback", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Absolute fallback Settings failed", ex)
            }
        }
    }

    /**
     * Checks if the app is currently ignoring battery optimizations.
     */
    fun isBatteryOptimizingIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }
}
