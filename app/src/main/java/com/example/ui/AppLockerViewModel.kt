package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.drawable.toBitmap
import com.example.data.AppPreferences
import com.example.data.AppDatabase
import com.example.data.BlockEvent
import com.example.domain.AppInfo
import com.example.domain.CategoryUiState
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppLockerViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = kotlinx.coroutines.flow.combine(
        _installedApps,
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.appName.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val lockedApps: StateFlow<Set<String>> = appPreferences.lockedAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val appCategories: StateFlow<Map<String, String>> = appPreferences.appCategoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun getCategoryForApp(packageName: String, customCategory: String?): String {
        if (!customCategory.isNullOrBlank()) {
            return customCategory
        }
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("chat") || pkg.contains("social") || pkg.contains("messenger") || pkg.contains("whatsapp") || pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("twitter") || pkg.contains("reddit") || pkg.contains("viber") || pkg.contains("discord") || pkg.contains("telegram") || pkg.contains("snapchat") || pkg.contains("tiktok") -> "Social"
            pkg.contains("mail") || pkg.contains("drive") || pkg.contains("calendar") || pkg.contains("office") || pkg.contains("sheet") || pkg.contains("doc") || pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") || pkg.contains("pdf") || pkg.contains("clock") || pkg.contains("calculator") || pkg.contains("keep") || pkg.contains("notes") || pkg.contains("notebook") -> "Productivity"
            pkg.contains("gallery") || pkg.contains("photos") || pkg.contains("youtube") || pkg.contains("vlc") || pkg.contains("player") || pkg.contains("music") || pkg.contains("spotify") || pkg.contains("netflix") || pkg.contains("camera") || pkg.contains("video") || pkg.contains("audio") || pkg.contains("sound") -> "Media"
            pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("pay") || pkg.contains("finance") || pkg.contains("card") || pkg.contains("crypto") || pkg.contains("cash") || pkg.contains("gpay") || pkg.contains("stripe") || pkg.contains("paypal") -> "Finance"
            pkg.contains("contact") || pkg.contains("phone") || pkg.contains("call") || pkg.contains("message") || pkg.contains("mms") || pkg.contains("sms") || pkg.contains("setting") || pkg.contains("file") || pkg.contains("files") || pkg.contains("download") -> "Personal"
            else -> "Others"
        }
    }

    val categoriesState: StateFlow<List<CategoryUiState>> = kotlinx.coroutines.flow.combine(
        _installedApps,
        lockedApps,
        appCategories
    ) { apps, locked, customCategories ->
        val predefined = listOf("Social", "Productivity", "Media", "Personal", "Finance", "Others")
        
        val groupings = apps.groupBy { appInfo ->
            getCategoryForApp(appInfo.packageName, customCategories[appInfo.packageName])
        }

        val otherCustomCategories = customCategories.values.distinct()
            .filter { it !in predefined && it.isNotBlank() }

        val allCategories = predefined + otherCustomCategories

        allCategories.map { category ->
            val appsInCat = groupings[category] ?: emptyList()
            val lockedCount = appsInCat.count { locked.contains(it.packageName) }
            CategoryUiState(
                categoryName = category,
                totalApps = appsInCat.size,
                lockedAppsCount = lockedCount
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val appTimers: StateFlow<Map<String, Long>> = appPreferences.appTimersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val gracePeriodMs: StateFlow<Long> = appPreferences.gracePeriodFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 60_000L
    )

    val relockOnScreenOff: StateFlow<Boolean> = appPreferences.relockOnScreenOffFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val unlockPattern: StateFlow<String?> = appPreferences.unlockPatternFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val useDeviceLock: StateFlow<Boolean> = appPreferences.useDeviceLockFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _isAllPermissionsGranted = MutableStateFlow(false)
    val isAllPermissionsGranted: StateFlow<Boolean> = _isAllPermissionsGranted.asStateFlow()

    private val _usageStatsGranted = MutableStateFlow(false)
    val usageStatsGranted: StateFlow<Boolean> = _usageStatsGranted.asStateFlow()

    private val _notificationsGranted = MutableStateFlow(false)
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    private val _batteryOptimizationsIgnored = MutableStateFlow(false)
    val batteryOptimizationsIgnored: StateFlow<Boolean> = _batteryOptimizationsIgnored.asStateFlow()

    private val _overlaysGranted = MutableStateFlow(false)
    val overlaysGranted: StateFlow<Boolean> = _overlaysGranted.asStateFlow()

    fun refreshPermissionStatus() {
        val context = getApplication<Application>()
        val usageGranted = checkUsageStatsPermission(context)
        val notifGranted = checkNotificationsPermission(context)
        val battIgnored = checkBatteryOptimizationsIgnored(context)
        val overlayGranted = checkOverlayPermission(context)

        _usageStatsGranted.value = usageGranted
        _notificationsGranted.value = notifGranted
        _batteryOptimizationsIgnored.value = battIgnored
        _overlaysGranted.value = overlayGranted

        _isAllPermissionsGranted.value = usageGranted && notifGranted && battIgnored && overlayGranted
    }

    private fun checkUsageStatsPermission(context: android.content.Context): Boolean {
        val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun checkNotificationsPermission(context: android.content.Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBatteryOptimizationsIgnored(context: android.content.Context): Boolean {
        return com.example.SamsungBatteryHelper.isBatteryOptimizingIgnored(context)
    }

    private fun checkOverlayPermission(context: android.content.Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    init {
        loadInstalledApps()
        refreshPermissionStatus()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val apps = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == getApplication<Application>().packageName) {
                    return@mapNotNull null
                }
                
                try {
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    val bitmap = icon?.toBitmap(width = 128, height = 128)
                    AppInfo(packageName, appName, icon = icon, iconBitmap = bitmap)
                } catch (e: Exception) {
                    Log.e("AppLockerViewModel", "Error loading app info", e)
                    null
                }
            }.sortedBy { it.appName.lowercase() }
            
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun lockApp(packageName: String, category: String, customGracePeriodMs: Long) {
        viewModelScope.launch {
            appPreferences.lockApp(packageName, category, customGracePeriodMs)
        }
    }

    fun unlockApp(packageName: String) {
        viewModelScope.launch {
            appPreferences.unlockApp(packageName)
        }
    }

    fun toggleAppLock(packageName: String) {
        viewModelScope.launch {
            appPreferences.toggleAppLock(packageName)
        }
    }

    fun setCategoryLockState(categoryName: String, lock: Boolean) {
        viewModelScope.launch {
            val apps = _installedApps.value
            val customCategories = appCategories.value
            val appsInCat = apps.filter { appInfo ->
                getCategoryForApp(appInfo.packageName, customCategories[appInfo.packageName]) == categoryName
            }
            
            appsInCat.forEach { appInfo ->
                if (lock) {
                    val customPeriod = appTimers.value[appInfo.packageName] ?: 60_000L
                    appPreferences.lockApp(appInfo.packageName, categoryName, customPeriod)
                } else {
                    appPreferences.unlockApp(appInfo.packageName)
                }
            }
        }
    }

    fun setGracePeriodMs(timeoutMs: Long) {
        viewModelScope.launch {
            appPreferences.setGracePeriod(timeoutMs)
        }
    }

    fun setRelockOnScreenOff(relock: Boolean) {
        viewModelScope.launch {
            appPreferences.setRelockOnScreenOff(relock)
        }
    }

    fun setUnlockPattern(pattern: String) {
        viewModelScope.launch {
            appPreferences.setUnlockPattern(pattern)
        }
    }

    fun setUseDeviceLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setUseDeviceLock(enabled)
        }
    }

    private val database = AppDatabase.getDatabase(getApplication())
    val blockEventsFlow = database.blockEventDao().getAllEventsFlow()

    // Analytics JSON Flow for the WebView Recharts
    val analyticsJson: StateFlow<String> = blockEventsFlow.map { events ->
        val totalBlocks = events.size
        
        // Sum estimated minutes saved (5 mins / 300 seconds per block event)
        val totalTimeSavedMinutes = events.sumOf { it.estimatedTimeSavedSeconds } / 60

        // Get Top Blocked Apps (up to 5)
        val appCounts = events.groupBy { it.appLabel }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { entry ->
                mapOf(
                    "name" to entry.key,
                    "count" to entry.value,
                    "time" to entry.value * 5
                )
            }

        // Get Last 7 Days History from furthest to today
        val historyList = mutableListOf<Map<String, Any>>()
        val sdf = SimpleDateFormat("EEE", Locale.getDefault()) // "Mon", "Tue", etc.
        
        for (i in 6 downTo 0) {
            val dateCal = Calendar.getInstance()
            dateCal.add(Calendar.DAY_OF_YEAR, -i)
            
            val label = sdf.format(dateCal.time)
            
            // Start of day
            dateCal.set(Calendar.HOUR_OF_DAY, 0)
            dateCal.set(Calendar.MINUTE, 0)
            dateCal.set(Calendar.SECOND, 0)
            val startMs = dateCal.timeInMillis
            
            // End of day
            dateCal.set(Calendar.HOUR_OF_DAY, 23)
            dateCal.set(Calendar.MINUTE, 59)
            dateCal.set(Calendar.SECOND, 59)
            val endMs = dateCal.timeInMillis

            val countInDay = events.count { it.timestamp in startMs..endMs }
            historyList.add(
                mapOf(
                    "date" to label,
                    "count" to countInDay
                )
            )
        }

        // Generate JSON string safely
        val appsJson = appCounts.joinToString(prefix = "[", postfix = "]") { app ->
            """{"name":"${app["name"]}","count":${app["count"]},"time":${app["time"]}}"""
        }

        val historyJson = historyList.joinToString(prefix = "[", postfix = "]") { day ->
            """{"date":"${day["date"]}","count":${day["count"]}}"""
        }

        val json = """
            {
              "totalBlocks": $totalBlocks,
              "totalTimeSavedMinutes": $totalTimeSavedMinutes,
              "appsData": $appsJson,
              "historyData": $historyJson
            }
        """.trimIndent()
        
        json
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "{}"
    )

    fun populateDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
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

                // Clear current data first for clean reset
                db.blockEventDao().clearAllEvents()

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
                    db.blockEventDao().insertEvent(event)
                }

                // Sync to legacy SharedPreferences for full compatibility
                val prefs = getApplication<Application>().getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.clear()
                demoApps.forEach { pair ->
                    val count = db.blockEventDao().getAllEventsFlow().first().count { it.packageName == pair.first }
                    if (count > 0) {
                        editor.putInt(pair.first, count)
                    }
                }
                editor.apply()
            } catch (e: Exception) {
                Log.e("AppLockerViewModel", "Error populating smart demo data", e)
            }
        }
    }

    fun insertSampleData() {
        populateDemo()
    }

    fun clearStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear Room DB
                val db = AppDatabase.getDatabase(getApplication())
                db.blockEventDao().clearAllEvents()

                // Clear legacy SharedPreferences
                val prefs = getApplication<Application>().getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.e("AppLockerViewModel", "Error clearing stats", e)
            }
        }
    }
}
