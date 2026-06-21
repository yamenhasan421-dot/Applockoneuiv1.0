package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.AnalyticItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _analyticsData = MutableStateFlow<List<AnalyticItem>>(emptyList())
    val analyticsData: StateFlow<List<AnalyticItem>> = _analyticsData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val prefs = context.getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                val allPrefs = prefs.all

                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                
                // Build a map of package name -> AppInfo details
                val appMap = resolveInfos.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == context.packageName) {
                        return@mapNotNull null
                    }
                    try {
                        val appName = resolveInfo.loadLabel(pm).toString()
                        val icon = resolveInfo.loadIcon(pm)
                        val bitmap = icon?.toBitmap(width = 128, height = 128)
                        packageName to Pair(appName, bitmap)
                    } catch (e: Exception) {
                        null
                    }
                }.toMap()

                val items = allPrefs.mapNotNull { (packageName, countVal) ->
                    val count = (countVal as? Int) ?: 0
                    if (count > 0) {
                        val appDetails = appMap[packageName]
                        val appLabel = appDetails?.first ?: packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                        val bitmap = appDetails?.second
                        AnalyticItem(
                            packageName = packageName,
                            appName = appLabel,
                            iconBitmap = bitmap,
                            unlockCount = count
                        )
                    } else {
                        null
                    }
                }.sortedByDescending { it.unlockCount }

                _analyticsData.value = items
            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "Error loading analytics", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun insertSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val installedPackages = resolveInfos.map { it.activityInfo.packageName }
                    .filter { it != context.packageName }
                    .shuffled()
                    .take(3)

                val editor = prefs.edit()
                if (installedPackages.isNotEmpty()) {
                    installedPackages.forEachIndexed { index, pkg ->
                        editor.putInt(pkg, (index + 1) * 4 + (1..6).random())
                    }
                } else {
                    editor.putInt("com.android.chrome", 12)
                    editor.putInt("com.whatsapp", 8)
                    editor.putInt("com.instagram.android", 15)
                }
                editor.apply()
                loadAnalytics()
            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "Error inserting sample data", e)
            }
        }
    }

    fun clearStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                loadAnalytics()
            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "Error clearing stats", e)
            }
        }
    }
}
