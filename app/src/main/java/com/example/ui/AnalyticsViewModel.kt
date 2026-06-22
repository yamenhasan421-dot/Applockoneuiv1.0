package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.BlockEvent
import com.example.domain.AnalyticItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val appPreferences = AppPreferences(application)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 2. Fix Immediate UI Refresh (Reactive Flow)
    // Directly reads database events reactively from Room and converts to a Compose-friendly StateFlow.
    val analyticsData: StateFlow<List<AnalyticItem>> = database.blockEventDao().getAllEventsFlow()
        .map { events ->
            val context = getApplication<Application>()
            val pm = context.packageManager
            
            // Group events by package name to compute the count of interceptions dynamically
            events.groupBy { it.packageName }.map { (packageName, pkgEvents) ->
                val unlockCount = pkgEvents.size
                var appLabel = pkgEvents.firstOrNull()?.appLabel ?: ""
                var bitmap: Bitmap? = null
                
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    if (appLabel.isEmpty()) {
                        appLabel = pm.getApplicationLabel(appInfo).toString()
                    }
                    val icon = pm.getApplicationIcon(appInfo)
                    bitmap = icon.toBitmap(width = 128, height = 128)
                } catch (e: Exception) {
                    if (appLabel.isEmpty()) {
                        appLabel = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                    }
                }
                
                AnalyticItem(
                    packageName = packageName,
                    appName = appLabel,
                    iconBitmap = bitmap,
                    unlockCount = unlockCount
                )
            }.sortedByDescending { it.unlockCount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadAnalytics() {
        // Handled reactively by Flow, retained for compatibility
    }

    // 1. Make Demo Data Smart & Realistic
    // Dynamically reads currently locked apps from AppPreferences and populates realistic mock database events.
    fun populateDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val pm = getApplication<Application>().packageManager
                
                // Get the actual list of currently locked package names
                val lockedAppsSet = appPreferences.lockedAppsFlow.first()
                val demoApps = mutableListOf<Pair<String, String>>()
                
                if (lockedAppsSet.isNotEmpty()) {
                    lockedAppsSet.forEach { pkg ->
                        var label = ""
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            label = pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            label = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                        }
                        demoApps.add(Pair(pkg, label))
                    }
                } else {
                    // Fallback to 2 or 3 standard system apps if nothing is currently locked
                    val fallbacks = listOf(
                        Pair("com.android.chrome", "Chrome"),
                        Pair("com.google.android.youtube", "YouTube"),
                        Pair("com.whatsapp", "WhatsApp")
                    )
                    demoApps.addAll(fallbacks)
                }

                // Clear current historical data for a clean sample reset
                database.blockEventDao().clearAllEvents()

                // Generate smart randomized historical BlockEvent entries spread over the last 7 days
                val totalEvents = (25..35).random()
                for (i in 1..totalEvents) {
                    val app = demoApps.random()
                    val randomDaysAgo = (0..6).random()
                    val calendarInstance = Calendar.getInstance()
                    calendarInstance.add(Calendar.DAY_OF_YEAR, -randomDaysAgo)
                    calendarInstance.set(Calendar.HOUR_OF_DAY, (8..22).random())
                    calendarInstance.set(Calendar.MINUTE, (0..59).random())
                    
                    val event = BlockEvent(
                        packageName = app.first,
                        appLabel = app.second,
                        timestamp = calendarInstance.timeInMillis
                    )
                    database.blockEventDao().insertEvent(event)
                }

                // Compatibility Sync: update the legacy SharedPreferences key-vals in case it is queried anywhere
                val prefs = getApplication<Application>().getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.clear()
                demoApps.forEach { pair ->
                    val count = database.blockEventDao().getAllEventsFlow().first().count { it.packageName == pair.first }
                    if (count > 0) {
                        editor.putInt(pair.first, count)
                    }
                }
                editor.apply()

            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "Error populating smart demo data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun insertSampleData() {
        populateDemo()
    }

    fun clearStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Clear Room DB
                database.blockEventDao().clearAllEvents()
                
                // Clear legacy SharedPreferences
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "Error clearing stats", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
