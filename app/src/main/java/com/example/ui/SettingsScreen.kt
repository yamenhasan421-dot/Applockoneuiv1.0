package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppLockerViewModel,
    onNavigateBack: () -> Unit
) {
    val gracePeriodMs by viewModel.gracePeriodMs.collectAsStateWithLifecycle()
    val relockOnScreenOff by viewModel.relockOnScreenOff.collectAsStateWithLifecycle()

    var showCustomTimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF000000))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular back button matching dashboard
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1D21))
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(18.dp))
                Text(
                    text = "Locker Settings",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 22.sp
                )
            }
        },
        containerColor = Color(0xFF000000) // Pure pitch black matching AMOLED main UI
    ) { paddingValues ->
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .background(Color(0xFF000000))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Locking Behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E82FC), // Branded active blue tint
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // Re-lock on screen off card
            GlassCard(
                onClick = { viewModel.setRelockOnScreenOff(!relockOnScreenOff) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Re-lock after screen off",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Instantly re-lock all unlocked apps when the device screen turns off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E),
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = relockOnScreenOff,
                        onCheckedChange = { viewModel.setRelockOnScreenOff(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF3E82FC),
                            uncheckedThumbColor = Color(0xFF8B949E),
                            uncheckedTrackColor = Color(0xFF22242B)
                        )
                    )
                }
            }

            // Grace period config options
            Text(
                text = "Grace Period Timeout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E82FC),
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )

            val gracePeriodOptions = listOf(
                Triple("Immediately", "Lock immediately after exiting the application.", 0L),
                Triple("After 1 minute", "Keep unlocked for 1 minute after exiting.", 60_000L),
                Triple("Keep unlocked completely", "Keep unlocked while the screen is awake, lock only when screen goes off.", Long.MAX_VALUE),
                Triple("Custom period...", "Choose a custom background grace duration.", -1L)
            )

            gracePeriodOptions.forEach { (label, subtitle, value) ->
                val isSelected = if (value == -1L) {
                    gracePeriodMs != 0L && gracePeriodMs != 60_000L && gracePeriodMs != Long.MAX_VALUE
                } else {
                    gracePeriodMs == value
                }

                GlassCard(
                    onClick = {
                        if (value == -1L) {
                            showCustomTimerDialog = true
                        } else {
                            viewModel.setGracePeriodMs(value)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null, // Handled naturally by outer card-click wrapping
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3E82FC),
                                unselectedColor = Color(0xFF323641)
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            val displaySubText = if (isSelected && value == -1L) {
                                val totalSeconds = gracePeriodMs / 1000L
                                val displayMins = totalSeconds / 60
                                val displaySecs = totalSeconds % 60
                                if (displayMins > 0) {
                                    "Custom timer: $displayMins min $displaySecs sec"
                                } else {
                                    "Custom timer: $displaySecs seconds"
                                }
                            } else {
                                subtitle
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displaySubText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // System Security Active Block
            Text(
                text = "System Security Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E82FC),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF3E82FC).copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3E82FC).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Lock Status",
                                tint = Color(0xFF3E82FC),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Security Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Built-in Device Credentials Enforced",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "For ultimate on-device privacy, AppLocker integrates directly with your Android device's built-in secure credentials lock (BiometricPrompt).\n\nWhen launching locked apps, you are verified through fingerprints, facial recognition, secure PINs, patterns, or passkeys handled exclusively within your system's hardware-encrypted enclave (TEE / Knox Sandbox) - avoiding simulated overlays and offering maximum security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E),
                        lineHeight = 17.sp
                    )
                }
            }

            // Permanent Permissions Management Section
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E82FC),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )

            val usageStatsGranted by viewModel.usageStatsGranted.collectAsStateWithLifecycle()
            val notificationsGranted by viewModel.notificationsGranted.collectAsStateWithLifecycle()
            val batteryOptimizationsIgnored by viewModel.batteryOptimizationsIgnored.collectAsStateWithLifecycle()
            val overlaysGranted by viewModel.overlaysGranted.collectAsStateWithLifecycle()

            // Observe resume lifecyle event to refresh permission status inside settings
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissionStatus()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF3E82FC).copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Item 1: Usage Access
                    SettingsPermissionToggle(
                        title = "Usage Access",
                        description = "Required to detect when locked apps are launched.",
                        isGranted = usageStatsGranted,
                        onFixClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )

                    // Item 2: Appear on Top
                    SettingsPermissionToggle(
                        title = "Appear on Top / Overlays",
                        description = "Required to display secure lock screens over locked apps.",
                        isGranted = overlaysGranted,
                        onFixClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    // Item 3: Notification permission
                    SettingsPermissionToggle(
                        title = "Notifications",
                        description = "Required to keep the locking service persistent & reliable.",
                        isGranted = notificationsGranted,
                        onFixClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    // Item 4: Battery exemptions
                    SettingsPermissionToggle(
                        title = "Background Activity",
                        description = "Required to prevent the system from killing the background monitor.",
                        isGranted = batteryOptimizationsIgnored,
                        onFixClick = {
                            com.example.SamsungBatteryHelper.requestIgnoreBatteryOptimizations(context)
                        }
                    )
                }
            }

            // Space out some padding at the bottom of the scrollable column
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCustomTimerDialog) {
        var isMinutesSelected by remember { mutableStateOf(false) }
        var customValueStr by remember { 
            mutableStateOf(
                if (gracePeriodMs > 0L && gracePeriodMs != 60_000L && gracePeriodMs != Long.MAX_VALUE) {
                    if (gracePeriodMs % 60_000L == 0L) {
                        isMinutesSelected = true
                        (gracePeriodMs / 60_000L).toString()
                    } else {
                        (gracePeriodMs / 1000L).toString()
                    }
                } else {
                    "30"
                }
            )
        }
        
        AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            title = { Text("Custom Grace Period", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Specify custom background delay duration until the application gets locked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B949E)
                    )
                    OutlinedTextField(
                        value = customValueStr,
                        onValueChange = { customValueStr = it.filter { char -> char.isDigit() } },
                        label = { Text(if (isMinutesSelected) "Minutes" else "Seconds") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3E82FC),
                            unfocusedBorderColor = Color(0xFF323641)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isMinutesSelected,
                            onClick = { isMinutesSelected = true },
                            label = { Text("Minutes") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E293B),
                                selectedLabelColor = Color(0xFF3E82FC)
                            )
                        )
                        FilterChip(
                            selected = !isMinutesSelected,
                            onClick = { isMinutesSelected = false },
                            label = { Text("Seconds") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E293B),
                                selectedLabelColor = Color(0xFF3E82FC)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rawValue = customValueStr.toLongOrNull() ?: 30L
                    val multiplier = if (isMinutesSelected) 60_000L else 1000L
                    viewModel.setGracePeriodMs(rawValue * multiplier)
                    showCustomTimerDialog = false
                }) {
                    Text("Save", color = Color(0xFF3E82FC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimerDialog = false }) {
                    Text("Cancel", color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF141416) // Matching card color background for dialog
        )
    }

}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF141416))
            .clickable(onClick = onClick)
    ) {
        content()
    }
}

@Composable
fun SettingsPermissionToggle(
    title: String,
    description: String,
    isGranted: Boolean,
    onFixClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B949E),
                lineHeight = 15.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        } else {
            Button(
                onClick = onFixClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                    contentColor = Color(0xFFEF4444)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "FIX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
