package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.AppInfo
import com.example.AppLockerService
import com.example.SamsungBatteryHelper
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun AppLockerScreen(
    viewModel: AppLockerViewModel = viewModel(),
    onPermissionSetupClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToCategoryDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val lockedApps by viewModel.lockedApps.collectAsStateWithLifecycle()
    val appCategories by viewModel.appCategories.collectAsStateWithLifecycle()
    val appTimers by viewModel.appTimers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val gracePeriodMs by viewModel.gracePeriodMs.collectAsStateWithLifecycle()
    val categoriesList by viewModel.categoriesState.collectAsStateWithLifecycle()

    var appToLock by remember { mutableStateOf<AppInfo?>(null) }

    val isAllPermissionsGranted by viewModel.isAllPermissionsGranted.collectAsStateWithLifecycle()
    val usageStatsGranted by viewModel.usageStatsGranted.collectAsStateWithLifecycle()
    val notificationsGranted by viewModel.notificationsGranted.collectAsStateWithLifecycle()
    val batteryOptimizationsIgnored by viewModel.batteryOptimizationsIgnored.collectAsStateWithLifecycle()
    val overlaysGranted by viewModel.overlaysGranted.collectAsStateWithLifecycle()

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

    // Retrieve real-time background grace period variables from background service
    var activeTimers by remember { mutableStateOf<List<com.example.ActiveAppTimer>>(emptyList()) }
    var isServiceRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            activeTimers = AppLockerService.activeTimersList
            isServiceRunning = AppLockerService.isRunning
            delay(300) // Fast reaction polling
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure Samsung AMOLED Black background
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Sleek One UI 8.5 Top Bar matching user's reference screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left circular Back button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1D21)) // Samsung highly dark gray circle
                        .clickable {
                            // Go back to android launcher
                            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_HOME)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(homeIntent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Right Capsule Container enclosing BarChart and Settings (More)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1C1D21))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToAnalytics,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Stats & Timer Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings Menu",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3E82FC))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 40.dp, start = 20.dp, end = 20.dp)
                ) {
                    if (!isAllPermissionsGranted) {
                        item {
                            PermissionsRequiredCard(
                                viewModel = viewModel,
                                usageStatsGranted = usageStatsGranted,
                                notificationsGranted = notificationsGranted,
                                batteryOptimizationsIgnored = batteryOptimizationsIgnored,
                                overlaysGranted = overlaysGranted,
                                modifier = Modifier.padding(bottom = 20.dp)
                            )
                        }
                    }
                    
                    // CARD 1: Samsung Habits Hero Blue Banner
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color(0xFF171E2D), Color(0xFF14161A))
                                    )
                                )
                                .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(28.dp))
                                .padding(24.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Secure your personal privacy",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5CA3FF), // Light healthy blue tint
                                    fontSize = 24.sp,
                                    lineHeight = 32.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Protect sensitive applications, lock background activities, and prevent unauthorized access with Knox AppLocker security.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF8B949E), // Muted text
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // CARD 2: App Protection Status with beautiful Donut/Ring graph
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF141416))
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(
                                        text = "Screen protection today",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFA0A5B5),
                                        fontWeight = FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${lockedApps.size} Locked",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 28.sp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // Bullet legends list
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3E82FC)))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Protected",
                                            color = Color(0xFFE2E4E9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "${lockedApps.size}",
                                            color = Color(0xFF8C90A0),
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF12BB8B)))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "All Apps",
                                            color = Color(0xFFE2E4E9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "${filteredApps.size}",
                                            color = Color(0xFF8C90A0),
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                // Interactive Donut Canvas representing Locked/Unlocked items
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val lockedPercent = if (filteredApps.isEmpty()) 0f else (lockedApps.size.toFloat() / filteredApps.size.toFloat())
                                    val sweepAngle by animateFloatAsState(targetValue = lockedPercent * 360f)
                                    
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(90.dp)) {
                                        // Gray background track
                                        drawArc(
                                            color = Color(0xFF22242B),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        // Protected green/teal sweep
                                        drawArc(
                                            color = Color(0xFF12BB8B),
                                            startAngle = -90f,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        // Highlight accent blue sweep
                                        if (lockedApps.isNotEmpty()) {
                                            drawArc(
                                                color = Color(0xFF3E82FC),
                                                startAngle = -90f,
                                                sweepAngle = sweepAngle * 0.45f,
                                                useCenter = false,
                                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (filteredApps.isEmpty()) "0%" else "${(lockedPercent * 100).toInt()}%",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CARD 3: Horizontal list of lock categories
                    item {
                        Column(modifier = Modifier.padding(bottom = 20.dp)) {
                            Text(
                                text = "Most secured categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                categoriesList.chunked(3).forEach { rowCategories ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowCategories.forEach { category ->
                                            val (icon, tint, bgAccent) = when (category.categoryName) {
                                                "Social" -> Triple(Icons.Default.ChatBubble, Color(0xFF3E82FC), Color(0xFF1A2A44))
                                                "Productivity" -> Triple(Icons.Default.Business, Color(0xFF12BB8B), Color(0xFF172C27))
                                                "Media" -> Triple(Icons.Default.Image, Color(0xFFA855F7), Color(0xFF2B1D3A))
                                                "Personal" -> Triple(Icons.Default.Person, Color(0xFFFF9F0A), Color(0xFF352417))
                                                "Finance" -> Triple(Icons.Default.CreditCard, Color(0xFFEAB308), Color(0xFF2E2A15))
                                                else -> Triple(Icons.Default.Category, Color(0xFF94A3B8), Color(0xFF21252E))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(Color(0xFF141416))
                                                    .clickable { onNavigateToCategoryDetail(category.categoryName) }
                                                    .padding(16.dp)
                                            ) {
                                                Column {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(bgAccent),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = category.categoryName,
                                                            tint = tint,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    Text(
                                                        text = category.categoryName,
                                                        color = Color(0xFFE2E4E9),
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${category.lockedAppsCount} / ${category.totalApps} locked",
                                                        color = Color(0xFF8B949E),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        if (rowCategories.size < 3) {
                                            repeat(3 - rowCategories.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CARD 4: Live Grace Period Stopwatch Timer visualizer matching "App timers"
                    item {
                        Column(modifier = Modifier.padding(bottom = 20.dp)) {
                            Text(
                                text = "App timers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFF141416))
                                    .padding(20.dp)
                            ) {
                                if (activeTimers.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        activeTimers.forEach { timer ->
                                            val totalSecondsMax = timer.totalSecondsMax.coerceAtLeast(1).toFloat()
                                            val fraction = (timer.secondsLeft.toFloat() / totalSecondsMax).coerceIn(0f, 1f)
                                            
                                            val matchedApp = remember(timer.packageName, installedApps) {
                                                installedApps.find { it.packageName == timer.packageName }
                                            }
                                            val displayName = matchedApp?.appName ?: timer.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                                            val appIconBitmap = remember(matchedApp) {
                                                matchedApp?.iconBitmap?.asImageBitmap()
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (appIconBitmap != null) {
                                                            Image(
                                                                bitmap = appIconBitmap,
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFFFFAEB)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.HourglassEmpty,
                                                                    contentDescription = "Grace countdown active",
                                                                    tint = Color(0xFFD97706),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Text(
                                                                text = displayName,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                fontSize = 15.sp
                                                            )
                                                            Text(
                                                                text = "Security Grace Window Active",
                                                                color = Color(0xFF8B949E),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                AppLockerService.unlockedAppsMap.remove(timer.packageName)
                                                                // Auto refreshed
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "Lock immediately",
                                                                tint = Color(0xFFFF4949),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(Color(0xFF22242B))
                                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = "${timer.secondsLeft} s left",
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFFFBBF24),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                LinearProgressIndicator(
                                                    progress = fraction,
                                                    color = Color(0xFF3E82FC),
                                                    trackColor = Color(0xFF22242B),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Empty state: default helper layout when no timers are counting down
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22242B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = "Ready",
                                                tint = Color(0xFF8B949E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "No active countdowns",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC9CDD8),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Locked apps will trigger biometric check immediately upon launch.",
                                                color = Color(0xFF717888),
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CARD 5: Battery exemptions setup checklist
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 28.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF141416))
                                .clickable {
                                    onPermissionSetupClick()
                                    SamsungBatteryHelper.requestIgnoreBatteryOptimizations(context)
                                }
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val pmIgnored = SamsungBatteryHelper.isBatteryOptimizingIgnored(context)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (pmIgnored) Color(0xFF12BB8B).copy(alpha = 0.15f) else Color(0xFFFF4949).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (pmIgnored) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                            contentDescription = "Samsung sleep checker",
                                            tint = if (pmIgnored) Color(0xFF12BB8B) else Color(0xFFFF5252),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Exempt Background Activities",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Crucial on Samsung/OneUI. Tap to whitelist.",
                                            color = Color(0xFFA0A5B5),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Configure exemptions",
                                    tint = Color(0xFF717888),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Section Heading: Manage app locks
                    item {
                        Column {
                            Text(
                                text = "Manage app locks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 2. Beautiful text field search bar at the top of lister section
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = { Text("Search installed apps...", color = Color(0xFF717888)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFF8E929E)
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF141416),
                                    unfocusedContainerColor = Color(0xFF141416),
                                    disabledContainerColor = Color(0xFF141416),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // 3. Complete matching applications list switches grouped by category
                    val lockedAppsSet = lockedApps
                    val lockedAppsList = filteredApps.filter { lockedAppsSet.contains(it.packageName) }
                    val unlockedAppsList = filteredApps.filter { !lockedAppsSet.contains(it.packageName) }

                    // Group locked apps by category
                    val groupedLockedApps = lockedAppsList.groupBy { appInfo ->
                        appCategories[appInfo.packageName] ?: "Others"
                    }

                    if (lockedAppsList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Locked Apps (Grouped by Category)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3E82FC),
                                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                            )
                        }

                        groupedLockedApps.forEach { (categoryName, apps) ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3E82FC))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$categoryName (${apps.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E4E9),
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            items(apps, key = { "locked_${it.packageName}" }) { appInfo ->
                                val customMs = appTimers[appInfo.packageName] ?: 60_000L
                                val displayTime = if (customMs >= 60_000L && customMs % 60_000L == 0L) {
                                    "${customMs / 60_000L}m"
                                } else {
                                    "${customMs / 1000L}s"
                                }
                                AppItemRow(
                                    appInfo = appInfo,
                                    isLocked = true,
                                    categoryName = "$categoryName ($displayTime)",
                                    onToggleLock = { viewModel.unlockApp(appInfo.packageName) },
                                    onChooseCategory = {
                                        appToLock = appInfo
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }

                    if (unlockedAppsList.isNotEmpty()) {
                        item {
                            Text(
                                text = if (lockedAppsList.isNotEmpty()) "Unlocked Apps" else "All Unlocked Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B949E),
                                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                            )
                        }

                        items(unlockedAppsList, key = { "unlocked_${it.packageName}" }) { appInfo ->
                            AppItemRow(
                                appInfo = appInfo,
                                isLocked = false,
                                categoryName = null,
                                onToggleLock = {
                                    appToLock = appInfo
                                },
                                onChooseCategory = null
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // Category Selection Dialog matching OneUI 8.5 Dark Canvas
        if (appToLock != null) {
            val targetApp = appToLock!!
            val existingCategory = appCategories[targetApp.packageName] ?: ""
            var selectedCategory by remember { mutableStateOf(if (existingCategory.isNotEmpty()) existingCategory else "Social") }
            var customCategoryName by remember { mutableStateOf("") }
            val existingTimerMs = appTimers[targetApp.packageName] ?: 60_000L
            var timerSecondsInput by remember { mutableStateOf((existingTimerMs / 1000L).toString()) }

            LaunchedEffect(targetApp.packageName) {
                selectedCategory = if (existingCategory.isNotEmpty()) existingCategory else "Social"
                timerSecondsInput = (existingTimerMs / 1000L).toString()
                customCategoryName = ""
            }

            val predefinedCategories = listOf("Social", "Productivity", "Media", "Personal", "Finance", "Others")

            AlertDialog(
                onDismissRequest = { appToLock = null },
                title = {
                    Text(
                        text = "App Lock Configuration",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Assign category and individual relock timer for ${targetApp.appName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8B949E)
                        )

                        Text(
                            text = "Security Category",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Predefined options
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            predefinedCategories.forEach { category ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedCategory == category) Color(0xFF1E293B) else Color.Transparent)
                                        .clickable {
                                            selectedCategory = category
                                            customCategoryName = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (selectedCategory == category),
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF3E82FC),
                                            unselectedColor = Color(0xFF323641)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedCategory == category) Color.White else Color(0xFFC9CDD8)
                                    )
                                }
                            }

                            // Custom option
                            val isCustomSelected = selectedCategory !in predefinedCategories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCustomSelected) Color(0xFF1E293B) else Color.Transparent)
                                    .clickable {
                                        selectedCategory = "Custom"
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isCustomSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF3E82FC),
                                        unselectedColor = Color(0xFF323641)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Custom category...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCustomSelected) Color.White else Color(0xFFC9CDD8)
                                )
                            }
                        }

                        if (selectedCategory == "Custom" || selectedCategory !in predefinedCategories) {
                            OutlinedTextField(
                                value = customCategoryName,
                                onValueChange = { customCategoryName = it },
                                label = { Text("Category Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3E82FC),
                                    unfocusedBorderColor = Color(0xFF323641)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F222B)))

                        Text(
                            text = "Unlock Timer (seconds)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Configure how many seconds this app remains unlocked after you exit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E)
                        )

                        OutlinedTextField(
                            value = timerSecondsInput,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    timerSecondsInput = newValue
                                }
                            },
                            label = { Text("Relock delay (seconds)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3E82FC),
                                unfocusedBorderColor = Color(0xFF323641)
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val finalCategory = if (selectedCategory == "Custom") {
                                customCategoryName.trim().ifEmpty { "Others" }
                            } else {
                                selectedCategory
                            }
                            val secsInput = timerSecondsInput.toLongOrNull() ?: 60L
                            val finalTimerMs = if (secsInput <= 0L) 1000L else secsInput * 1000L
                            viewModel.lockApp(targetApp.packageName, finalCategory, finalTimerMs)
                            appToLock = null
                        }
                    ) {
                        Text("Confirm", color = Color(0xFF3E82FC), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { appToLock = null }) {
                        Text("Cancel", color = Color(0xFF8B949E))
                    }
                },
                containerColor = Color(0xFF141416)
            )
        }
    }
}

@Composable
fun AppItemRow(
    appInfo: AppInfo,
    isLocked: Boolean,
    categoryName: String?,
    onToggleLock: () -> Unit,
    onChooseCategory: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF141416))
            .clickable(onClick = onToggleLock)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val appIconBitmap = remember(appInfo.packageName) {
                appInfo.iconBitmap?.asImageBitmap()
            }

            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "${appInfo.appName} Icon",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22242B))
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isLocked) "Locked" else "Unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLocked) Color(0xFF3E82FC) else Color(0xFF8B949E),
                        fontWeight = if (isLocked) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )

                    if (isLocked && categoryName != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .clickable { onChooseCategory?.invoke() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5CA3FF),
                                    fontSize = 11.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit category",
                                    tint = Color(0xFF5CA3FF),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            Switch(
                checked = isLocked,
                onCheckedChange = { onToggleLock() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF3E82FC),
                    uncheckedThumbColor = Color(0xFF8B949E),
                    uncheckedTrackColor = Color(0xFF22242B)
                )
            )
        }
    }
}

@Composable
fun PermissionsRequiredCard(
    viewModel: AppLockerViewModel,
    usageStatsGranted: Boolean,
    notificationsGranted: Boolean,
    batteryOptimizationsIgnored: Boolean,
    overlaysGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C1E24), Color(0xFF141416))
                )
            )
            .border(1.dp, Color(0xFF3E82FC).copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Security Alert",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Action Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Grant permissions to protect apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                }
            }

            // Permission Items List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Usage Stats Permission
                if (!usageStatsGranted) {
                    PermissionItemRow(
                        title = "Usage Access",
                        description = "Detect when locked apps are launched",
                        icon = Icons.Default.Apps,
                        onGrantClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // fallback
                            }
                        }
                    )
                }

                // 2. Overlay Permission
                if (!overlaysGranted) {
                    PermissionItemRow(
                        title = "Appear on Top",
                        description = "Display the secure biometric locking screen",
                        icon = Icons.Default.Layers,
                        onGrantClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // fallback
                                }
                            }
                        }
                    )
                }

                // 3. Notification Permission (Android 13+)
                if (!notificationsGranted) {
                    PermissionItemRow(
                        title = "Notifications",
                        description = "Keeps secure service running reliably",
                        icon = Icons.Default.Notifications,
                        onGrantClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // fallback request
                                }
                            }
                        }
                    )
                }

                // 4. Battery Optimization Exemptions
                if (!batteryOptimizationsIgnored) {
                    PermissionItemRow(
                        title = "Background Activity",
                        description = "Prevent sleep / kill on Samsung / OneUI",
                        icon = Icons.Default.BatteryChargingFull,
                        onGrantClick = {
                            com.example.SamsungBatteryHelper.requestIgnoreBatteryOptimizations(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF22242B).copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF3E82FC),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        
        Button(
            onClick = onGrantClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3E82FC),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = "Grant",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
