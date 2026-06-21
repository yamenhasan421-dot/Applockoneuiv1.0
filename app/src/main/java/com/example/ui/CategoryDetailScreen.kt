package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    viewModel: AppLockerViewModel,
    categoryName: String,
    onNavigateBack: () -> Unit
) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val lockedApps by viewModel.lockedApps.collectAsStateWithLifecycle()
    val appCategories by viewModel.appCategories.collectAsStateWithLifecycle()

    // Filter apps that belong to this category
    val filteredApps = remember(installedApps, appCategories, categoryName) {
        installedApps.filter { appInfo ->
            viewModel.getCategoryForApp(appInfo.packageName, appCategories[appInfo.packageName]) == categoryName
        }
    }

    // Number of locked apps in this category
    val lockedCount = remember(filteredApps, lockedApps) {
        filteredApps.count { lockedApps.contains(it.packageName) }
    }

    // Is lock-all active (if at least one or all is locked, let's treat "lock all" toggle based on whether any/all is locked)
    // To be most premium: master toggle is checked if ALL apps in this category are locked, or let's say checked if > 0 are locked.
    // Let's use check if ALL apps are locked (or count of locked == filteredApps.size when not empty)
    val isAllLocked = remember(filteredApps, lockedApps) {
        filteredApps.isNotEmpty() && filteredApps.all { lockedApps.contains(it.packageName) }
    }

    // Category Specific Colors & Icons matching predefined theme
    val (icon, tint, bgTint) = remember(categoryName) {
        when (categoryName) {
            "Social" -> Triple(Icons.Default.ChatBubble, Color(0xFF3E82FC), Color(0xFF1A2A44))
            "Productivity" -> Triple(Icons.Default.Business, Color(0xFF12BB8B), Color(0xFF172C27))
            "Media" -> Triple(Icons.Default.Image, Color(0xFFA855F7), Color(0xFF2B1D3A))
            "Personal" -> Triple(Icons.Default.Person, Color(0xFFFF9F0A), Color(0xFF352417))
            "Finance" -> Triple(Icons.Default.CreditCard, Color(0xFFEAB308), Color(0xFF2E2A15))
            else -> Triple(Icons.Default.Category, Color(0xFF94A3B8), Color(0xFF21252E))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1016), // Dark Obsidian
                        Color(0xFF060709)  // Pure AMOLED black
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F212A))
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "$lockedCount of ${filteredApps.size} apps secured",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7D8B9B),
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(bgTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "$categoryName Icon",
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Master Toggle Card
            if (filteredApps.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF151821),
                    border = BorderStroke(1.dp, Color(0x11FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAllLocked) "Category Fully Secured" else "Secure Category",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAllLocked) "Unlock all apps in this category" else "Lock all apps in this category instantly",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7D8B9B)
                            )
                        }
                        Switch(
                            checked = isAllLocked,
                            onCheckedChange = { checkState ->
                                viewModel.setCategoryLockState(categoryName, checkState)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = tint,
                                uncheckedThumbColor = Color(0xFF7D8B9B),
                                uncheckedTrackColor = Color(0xFF1F212A)
                            )
                        )
                    }
                }
            }

            // App List Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = Color(0xFF3E4351),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Apps In This Category",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF7D8B9B)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { appInfo ->
                            val isAppLocked = lockedApps.contains(appInfo.packageName)
                            val appIconBitmap = remember(appInfo.packageName) {
                                appInfo.iconBitmap?.asImageBitmap()
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                                color = Color(0xFF13151D),
                                border = BorderStroke(1.dp, Color(0x11FFFFFF)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (appIconBitmap != null) {
                                            Image(
                                                bitmap = appIconBitmap,
                                                contentDescription = "${appInfo.appName} icon",
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF22242B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = appInfo.appName.firstOrNull()?.toString()?.uppercase() ?: "",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = appInfo.appName,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = appInfo.packageName,
                                                color = Color(0xFF7D8B9B),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isAppLocked,
                                        onCheckedChange = { checkState ->
                                            if (checkState) {
                                                viewModel.lockApp(appInfo.packageName, categoryName, 60_000L)
                                            } else {
                                                viewModel.unlockApp(appInfo.packageName)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = tint,
                                            uncheckedThumbColor = Color(0xFF7D8B9B),
                                            uncheckedTrackColor = Color(0xFF1F212A)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
