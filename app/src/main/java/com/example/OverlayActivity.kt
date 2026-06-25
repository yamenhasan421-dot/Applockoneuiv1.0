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
    private var activeBiometricPrompt: androidx.biometric.BiometricPrompt? = null

    private val closeOverlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_CLOSE_OVERLAY") {
                Log.d("OverlayActivity", "Received ACTION_CLOSE_OVERLAY broadcast. Cancelling biometric prompt and finishing activity.")
                cancelBiometricPrompt()
                finish()
            }
        }
    }

    private fun cancelBiometricPrompt() {
        try {
            activeBiometricPrompt?.cancelAuthentication()
        } catch (e: Exception) {
            Log.e("OverlayActivity", "Error cancelling biometric prompt", e)
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
            Log.w("OverlayActivity", "Error unregistering closeOverlayReceiver", e)
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

        // Beautiful solid One UI 8.5 lock interface with integrated high-fidelity secure verification
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
            Log.e("OverlayActivity", "Failed to start system biometric verification", e)
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

@Composable
fun OneUILockScreen(
    appLabel: String,
    appIconDrawable: Drawable?,
    actualUnlockSuccess: () -> Unit,
    actualCancelClick: () -> Unit,
    onLaunchSystemBiometrics: (() -> Unit) -> Unit
) {
    var exitAction by remember { mutableStateOf(ExitAction.NONE) }
    var hasEntered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasEntered = true
        // Automatically request native verification when overlay is loaded
        onLaunchSystemBiometrics {
            exitAction = ExitAction.UNLOCK
        }
    }

    val opacity by animateFloatAsState(
        targetValue = if (hasEntered && exitAction == ExitAction.NONE) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        finishedListener = { currentOpacity ->
            if (currentOpacity == 0f) {
                when (exitAction) {
                    ExitAction.UNLOCK -> actualUnlockSuccess()
                    ExitAction.CANCEL -> actualCancelClick()
                    else -> {}
                }
            }
        },
        label = "screen_fade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
            .background(Color(0xFF000000)) // AMOLED Black
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Protective status or exit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141416))
                        .clickable { exitAction = ExitAction.CANCEL },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit to Home",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Security Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF162544))
                        .border(1.dp, Color(0xFF3E82FC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured",
                            tint = Color(0xFF3E82FC),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "SECURE LOCK ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5CA3FF),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Middle Section (App Name and Icon with subtle animation)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // App Icon Box
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF111214))
                        .border(1.5.dp, Color(0xFF3E82FC).copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIconDrawable != null) {
                        Image(
                            bitmap = appIconDrawable.toBitmap().asImageBitmap(),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock icon",
                            tint = Color(0xFF3E82FC),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = appLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "This application is secured using Android System Security",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Section: Samsung Knox/System Verification Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF121318))
                    .border(1.dp, Color(0xFF1E2026), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(vertical = 28.dp, horizontal = 24.dp)
            ) {
                // Grab handle indicator
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF33353D))
                )

                Text(
                    text = "Device Security Verification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Text(
                    text = "Verification is required to open $appLabel. Tap the button below to authorize via device biometrics, face, PIN, pattern, or password.",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Wide Button to launch the native prompt
                Button(
                    onClick = {
                        onLaunchSystemBiometrics {
                            exitAction = ExitAction.UNLOCK
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3E82FC),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Verify biometric prompt icon",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "Unlock with Device Security",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Secured by Samsung Knox Platform Integrity",
                    fontSize = 11.sp,
                    color = Color(0xFF3C4048),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}
