package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppPreferences
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

enum class ExitAction {
    NONE,
    UNLOCK,
    CANCEL
}

class OverlayActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isVisible = false

        @Volatile
        var isOverlayPending = false
    }

    private var targetPackage: String = ""
    private var activeBiometricPrompt: BiometricPrompt? = null
    private var isBiometricInProgress = false
    
    // Executor must be created during Activity lifecycle
    private lateinit var mainExecutor: Executor

    private val closeOverlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_CLOSE_OVERLAY") {
                Log.d("OverlayActivity", "Received ACTION_CLOSE_OVERLAY broadcast.")
                cancelBiometricPrompt()
                finish()
            }
        }
    }

    private fun cancelBiometricPrompt() {
        try {
            if (isBiometricInProgress) {
                activeBiometricPrompt?.cancelAuthentication()
                isBiometricInProgress = false
            }
        } catch (e: Exception) {
            Log.e("OverlayActivity", "Error cancelling biometric prompt", e)
        }
        activeBiometricPrompt = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize executor early in onCreate
        mainExecutor = ContextCompat.getMainExecutor(this)
        
        // Disable transitions instantly on start
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Set as full-screen activity, NOT overlay
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Register receiver to close overlay on screen off
        val filter = android.content.IntentFilter("ACTION_CLOSE_OVERLAY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeOverlayReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeOverlayReceiver, filter)
        }
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
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
        if (intent == null) {
            finish()
            return
        }
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
            com.example.ui.OneUILockScreen(
                appLabel = appLabel,
                appIconDrawable = appIconDrawable,
                actualUnlockSuccess = {
                    AppLockerService.setAppUnlocked(targetPackage)
                    finish()
                },
                actualCancelClick = { goHome() },
                onLaunchSystemBiometrics = { onSuccess: () -> Unit ->
                    showSystemBiometricPrompt(appLabel, onSuccess)
                }
            )
        }
    }

    private fun showSystemBiometricPrompt(appLabel: String, onSuccess: () -> Unit) {
        // CRITICAL: Check activity state before proceeding
        if (isDestroyed || isFinishing) {
            Log.w("OverlayActivity", "Activity is destroyed/finishing, cannot show biometric prompt")
            return
        }

        // Check if biometric is already in progress to avoid multiple prompts
        if (isBiometricInProgress) {
            Log.d("OverlayActivity", "Biometric authentication already in progress")
            return
        }

        // Verify biometric availability before creating prompt
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.e("OverlayActivity", "Biometric authentication not available. Code: $canAuthenticate")
            Toast.makeText(
                this, 
                "System security credentials not enrolled.", 
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Create BiometricPrompt with proper lifecycle awareness
        val biometricPrompt = BiometricPrompt(
            this,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isBiometricInProgress = false
                    
                    // Ignore user cancellation errors
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        
                        // Only show toast if activity is still alive
                        if (!isDestroyed && !isFinishing) {
                            Toast.makeText(
                                this@OverlayActivity,
                                "Credentials error: $errString",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    Log.d("OverlayActivity", "BiometricPrompt error: $errorCode - $errString")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isBiometricInProgress = false
                    
                    // Verify activity is still valid before invoking callback
                    if (!isDestroyed && !isFinishing) {
                        Toast.makeText(
                            this@OverlayActivity,
                            "Security Lock Passed",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess()
                    }
                    Log.d("OverlayActivity", "BiometricPrompt authentication succeeded")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Do NOT reset isBiometricInProgress here - prompt will retry
                    if (!isDestroyed && !isFinishing) {
                        Toast.makeText(
                            this@OverlayActivity,
                            "Verification failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Log.d("OverlayActivity", "BiometricPrompt authentication failed")
                }
            }
        )

        activeBiometricPrompt = biometricPrompt

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $appLabel")
            .setSubtitle("Authorized by Secure Lock")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setNegativeButtonText("Cancel")
            .build()

        try {
            isBiometricInProgress = true
            biometricPrompt.authenticate(promptInfo)
            Log.d("OverlayActivity", "BiometricPrompt authentication started successfully")
        } catch (e: Exception) {
            isBiometricInProgress = false
            Log.e("OverlayActivity", "Failed to start system biometric verification", e)
            if (!isDestroyed && !isFinishing) {
                Toast.makeText(
                    this,
                    "System security credentials not enrolled.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        isOverlayPending = false
        Log.d("OverlayActivity", "Activity resumed")
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        // Cancel biometric prompt when activity is paused to prevent callback crashes
        cancelBiometricPrompt()
        Log.d("OverlayActivity", "Activity paused, biometric prompt cancelled")
    }

    override fun onDestroy() {
        super.onDestroy()
        isVisible = false
        isOverlayPending = false
        cancelBiometricPrompt()
        try {
            unregisterReceiver(closeOverlayReceiver)
        } catch (e: Exception) {
            Log.w("OverlayActivity", "Error unregistering closeOverlayReceiver", e)
        }
        Log.d("OverlayActivity", "Activity destroyed")
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

    private fun goHome() {
        val startMain = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(startMain)
        finish()
    }
}
