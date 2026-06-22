package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppPreferences
import com.example.data.AppDatabase
import com.example.data.BlockEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppLockerService : Service() {

    companion object {
        @Volatile
        var instance: AppLockerService? = null

        @Volatile
        var isRunning = false
        
        @Volatile
        var gracePeriodMs: Long = 60_000L

        @Volatile
        var relockOnScreenOff: Boolean = true

        // Thread-safe map tracking all currently unlocked apps:
        // Key: Package Name
        // Value: Exit Timestamp (0L if currently open/active in foreground, > 0L if in background counting down)
        val unlockedAppsMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

        // Real-time states exposed to the Compose UI for high-accuracy per-app ticking timers
        @Volatile
        var activeTimersList: List<ActiveAppTimer> = emptyList()

        @Volatile
        var appTimersMap: Map<String, Long> = emptyMap()

        fun setAppUnlocked(packageName: String) {
            unlockedAppsMap[packageName] = 0L
            instance?.incrementUnlockCount(packageName)
        }

        fun clearUnlockedState() {
            if (relockOnScreenOff) {
                unlockedAppsMap.clear()
                activeTimersList = emptyList()
                instance?.currentForegroundPackage = ""
            }
        }

        fun getAppGracePeriodMs(packageName: String): Long {
            return appTimersMap[packageName] ?: gracePeriodMs
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var appPreferences: AppPreferences
    private lateinit var usageStatsManager: UsageStatsManager
    private var screenOffReceiver: ScreenOffReceiver? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        appPreferences = AppPreferences(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        registerScreenOffReceiver()
        startForegroundServiceNotification()
        startTracking()
    }

    private fun registerScreenOffReceiver() {
        screenOffReceiver = ScreenOffReceiver()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "applocker_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AppLocker Active")
            .setContentText("Monitoring protected applications...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }
    
    private var currentForegroundPackage = ""

    private fun startTracking() {
        serviceScope.launch {
            launch {
                appPreferences.gracePeriodFlow.collect {
                    gracePeriodMs = it
                }
            }
            launch {
                appPreferences.relockOnScreenOffFlow.collect {
                    relockOnScreenOff = it
                }
            }
            launch {
                appPreferences.appTimersFlow.collect {
                    appTimersMap = it
                }
            }
            while (isRunning) {
                checkForegroundApp()
                updateGracePeriodCountdown()
                delay(100L) // Near-instant check interval for foolproof overlay enforcement
            }
        }
    }

    /**
     * Highly robust foreground app retriever that blends dynamic system usage events
     * with historical interval-based usage stats for absolute reliability.
     */
    private fun getForegroundPackageName(): String? {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 1000 * 60 * 10 // Look back 10 minutes for absolute immunity to static view duration limits

        // Method 1: Check dynamic UsageEvents (the most instant and precise Android mechanism)
        try {
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            var latestResumed: String? = null
            var latestTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || 
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > latestTime) {
                        latestResumed = event.packageName
                        latestTime = event.timeStamp
                    }
                }
            }
            if (latestResumed != null) {
                return latestResumed
            }
        } catch (e: Exception) {
            Log.e("AppLockerService", "Error querying UsageEvents", e)
        }

        // Method 2: Fallback to general UsageStats in case events were missed or context is stale
        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - 1000 * 60, // Look back 60 seconds
                endTime
            )
            if (stats != null && stats.isNotEmpty()) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                return sorted.firstOrNull()?.packageName
            }
        } catch (e: Exception) {
            Log.e("AppLockerService", "Error querying queryUsageStats", e)
        }

        return null
    }

    private suspend fun checkForegroundApp() {
        val detectedPackage = getForegroundPackageName() ?: return

        val packageChanged = detectedPackage != currentForegroundPackage
        if (packageChanged) {
            val previousPackage = currentForegroundPackage
            currentForegroundPackage = detectedPackage

            // --- Exit State Trigger ---
            // If the user has exited an unlocked app, we record the exit timestamp.
            // Do NOT record exit timestamp if the new foreground package is OUR OWN app/overlay (since unlocking/verifying is in progress)
            if (currentForegroundPackage != packageName) {
                for ((pkg, exitTime) in unlockedAppsMap) {
                    if (pkg != currentForegroundPackage && exitTime == 0L) {
                        unlockedAppsMap[pkg] = System.currentTimeMillis()
                        Log.d("AppLockerService", "User exited unlocked application: $pkg. Timer started.")
                    }
                }
            }
        }

        if (currentForegroundPackage == packageName) {
            // Ignore showing locked overlay for our own settings & password prompt activity
            return
        }

        // --- Continuous Lock/Overlay Verification ---
        val lockedApps = appPreferences.lockedAppsFlow.first()
        if (lockedApps.contains(currentForegroundPackage)) {
            val exitTime = unlockedAppsMap[currentForegroundPackage]
            val appGraceMs = getAppGracePeriodMs(currentForegroundPackage)

            val isUnlocked = exitTime != null && (exitTime == 0L || (System.currentTimeMillis() - exitTime) <= appGraceMs)

            if (!isUnlocked) {
                // If not unlocked, enforce the lock overlay continuously if not visible and not already pending
                if (!OverlayActivity.isVisible && !OverlayActivity.isOverlayPending) {
                    OverlayActivity.isOverlayPending = true
                    Log.d("AppLockerService", "Locked app $currentForegroundPackage in foreground but overlay is not visible. Forcing overlay.")
                    showLockOverlay(currentForegroundPackage)
                    logBlockEvent(currentForegroundPackage)
                }
            } else {
                // If unlocked but was counting down in background, reset its exit timestamp to 0L since they are inside it again
                if (exitTime != null && exitTime > 0L) {
                    Log.d("AppLockerService", "Returned within grace period for $currentForegroundPackage. Resetting exit time.")
                    unlockedAppsMap[currentForegroundPackage] = 0L
                }
            }
        }
    }

    /**
     * Evaluates active background countdown states for each app and exposes list of ticking timers.
     */
    private fun updateGracePeriodCountdown() {
        val list = mutableListOf<ActiveAppTimer>()
        val currentTime = System.currentTimeMillis()

        val iterator = unlockedAppsMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pkg = entry.key
            val exitTime = entry.value

            if (exitTime > 0L) {
                val elapsed = currentTime - exitTime
                val appGraceMs = getAppGracePeriodMs(pkg)
                val remainingMs = appGraceMs - elapsed
                if (remainingMs > 0L && remainingMs < 7200L * 1000L) { // Guard against Max Value (indefinite relock off)
                    list.add(
                        ActiveAppTimer(
                            packageName = pkg,
                            secondsLeft = ((remainingMs / 1000L).toInt() + 1),
                            totalSecondsMax = (appGraceMs / 1000L).toInt()
                        )
                    )
                } else if (elapsed > appGraceMs) {
                    // Evict from unlocked map if expired in background
                    iterator.remove()
                }
            }
        }
        activeTimersList = list.sortedBy { it.secondsLeft }
    }

    private fun showLockOverlay(targetPackage: String) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            putExtra("TARGET_PACKAGE", targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        serviceJob.cancel()
        screenOffReceiver?.let { unregisterReceiver(it) }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun logBlockEvent(packageName: String) {
        val label = getAppLabel(packageName)
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@AppLockerService)
                val event = BlockEvent(
                    packageName = packageName,
                    appLabel = label,
                    timestamp = System.currentTimeMillis()
                )
                db.blockEventDao().insertEvent(event)
                Log.d("AppLockerService", "Logged block event: $packageName ($label)")
            } catch (e: Exception) {
                Log.e("AppLockerService", "Error inserting block event", e)
            }
        }
    }

    fun incrementUnlockCount(packageName: String) {
        try {
            val prefs = getSharedPreferences("unlock_analytics_prefs", Context.MODE_PRIVATE)
            val current = prefs.getInt(packageName, 0)
            prefs.edit().putInt(packageName, current + 1).apply()
            Log.d("AppLockerService", "incrementUnlockCount: $packageName to ${current + 1}")
        } catch (e: Exception) {
            Log.e("AppLockerService", "Error incrementing unlock count", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
