package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AnalyticItem

@Composable
fun AnalyticsScreen(
    viewModel: AppLockerViewModel, // Retained to preserve signature and prevent any routing breakages
    onNavigateBack: () -> Unit,
    analyticsViewModel: AnalyticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val analyticsList by analyticsViewModel.analyticsData.collectAsStateWithLifecycle()
    val isLoading by analyticsViewModel.isLoading.collectAsStateWithLifecycle()

    val totalUnlocks = remember(analyticsList) {
        analyticsList.sumOf { it.unlockCount }
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
            
            // Premium Header with Back Navigation and Title
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
                            text = "Usage Analytics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Tracker activity details",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7D8B9B),
                            fontSize = 12.sp
                        )
                    }
                }

                // Stats decorative badge icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x223E82FC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Analytics logo",
                        tint = Color(0xFF3E82FC),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick overview panel (if any analytics data exists)
            if (analyticsList.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
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
                        Column {
                            Text(
                                text = "Total Unlocked Sessions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7D8B9B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalUnlocks times",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x11FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${analyticsList.size} apps",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5CA3FF)
                            )
                        }
                    }
                }
            }

            // Central Area (List or Loader or Empty State)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3E82FC))
                    }
                } else if (analyticsList.isEmpty()) {
                    // Beautiful One UI stylized empty state card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF151821)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF4C5364),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = "No Apps Unlocked Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "After you unlock protected apps using biometric or passcode locks, your security stats will automatically list here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7D8B9B),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    // Lazy List of Glassmorphic custom cards
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(analyticsList, key = { it.packageName }) { item ->
                            val appIconBitmap = remember(item.packageName) {
                                item.iconBitmap?.asImageBitmap()
                            }

                            // Dynamic translucent One UI Glassmorphism card
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp)),
                                color = Color(0xCC151821),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (appIconBitmap != null) {
                                            Image(
                                                bitmap = appIconBitmap,
                                                contentDescription = "${item.appName} icon",
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF22242B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = item.appName.firstOrNull()?.toString()?.uppercase() ?: "",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = item.appName,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.packageName,
                                                color = Color(0xFF7D8B9B),
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${item.unlockCount}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 22.sp,
                                            color = Color(0xFF5CA3FF)
                                        )
                                        Text(
                                            text = "times",
                                            fontSize = 11.sp,
                                            color = Color(0xFF7D8B9B),
                                            modifier = Modifier.padding(bottom = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { analyticsViewModel.insertSampleData() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF171E2D),
                        contentColor = Color(0xFF5CA3FF)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Dummy data",
                            tint = Color(0xFF5CA3FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Populate Demo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { analyticsViewModel.clearStats() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF2F1D1D)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Reset stats",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Reset Stats",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
