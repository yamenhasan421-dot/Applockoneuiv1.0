package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.data.AppPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

class FullScreenLockActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isVisible = false

        @Volatile
        var isOverlayPending = false
    }

    private var targetPackage: String = ""
    private var activeBiometricPrompt: androidx.biometric.BiometricPrompt? = null

    private val closeOverlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_CLOSE_OVERLAY") {
                Log.d("FullScreenLockActivity", "Received ACTION_CLOSE_OVERLAY broadcast. Cancelling biometric prompt and finishing activity.")
                cancelBiometricPrompt()
                finish()
            }
        }
    }

    private fun cancelBiometricPrompt() {
        try {
            activeBiometricPrompt?.cancelAuthentication()
        } catch (e: Exception) {
            Log.e("FullScreenLockActivity", "Error cancelling biometric prompt", e)
        }
        activeBiometricPrompt = null
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        isOverlayPending = false
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isVisible = false
        isOverlayPending = false
        cancelBiometricPrompt()
        try {
            unregisterReceiver(closeOverlayReceiver)
        } catch (e: Exception) {
            Log.w("FullScreenLockActivity", "Error unregistering closeOverlayReceiver", e)
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable transitions instantly on start
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Register receiver to close overlay on screen off
        val filter = android.content.IntentFilter("ACTION_CLOSE_OVERLAY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeOverlayReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeOverlayReceiver, filter)
        }
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: ""
        if (targetPackage.isEmpty()) {
            finish()
            return
        }
        
        // Extract locked app profile information from PackageManager
        val pm = packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            targetPackage.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }

        val appIconDrawable = try {
            pm.getApplicationIcon(targetPackage)
        } catch (e: Exception) {
            null
        }

        setContent {
            OneUILockScreen(
                appLabel = appLabel,
                appIconDrawable = appIconDrawable,
                actualUnlockSuccess = {
                    AppLockerService.setAppUnlocked(targetPackage)
                    finish()
                },
                actualCancelClick = { goHome() },
                onLaunchSystemBiometrics = { onSuccess ->
                    showSystemBiometricPrompt(appLabel, onSuccess)
                }
            )
        }
    }

    private fun showSystemBiometricPrompt(appLabel: String, onSuccess: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, "Credentials error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Security Lock Passed", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Verification failed", Toast.LENGTH_SHORT).show()
                }
            })
        activeBiometricPrompt = biometricPrompt

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $appLabel")
            .setSubtitle("Authorized by Secure Lock")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("FullScreenLockActivity", "Failed to start system biometric verification", e)
            Toast.makeText(this, "System security credentials not enrolled.", Toast.LENGTH_LONG).show()
        }
    }

    private fun goHome() {
        val startMain = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(startMain)
        finish()
    }
}
