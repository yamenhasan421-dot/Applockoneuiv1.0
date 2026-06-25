package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class FingerprintType {
    UNKNOWN, UNDER_DISPLAY, SIDE_MOUNTED
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_locker_prefs")

class AppPreferences(private val context: Context) {
    private val LOCKED_APPS_KEY = stringSetPreferencesKey("locked_apps")
    private val GRACE_PERIOD_KEY = longPreferencesKey("grace_period_ms")
    private val RELOCK_ON_SCREEN_OFF_KEY = booleanPreferencesKey("relock_on_screen_off")
    private val APP_CATEGORIES_KEY = stringSetPreferencesKey("app_categories_map")
    private val APP_TIMERS_KEY = stringSetPreferencesKey("app_timers_map")
    private val UNLOCK_PATTERN_KEY = stringPreferencesKey("unlock_pattern")
    private val USE_DEVICE_LOCK_KEY = booleanPreferencesKey("use_device_lock")
    private val FINGERPRINT_TYPE_KEY = stringPreferencesKey("fingerprint_type")

    val fingerprintTypeFlow: Flow<FingerprintType> = context.dataStore.data
        .map { preferences ->
            val name = preferences[FINGERPRINT_TYPE_KEY] ?: FingerprintType.UNKNOWN.name
            try {
                FingerprintType.valueOf(name)
            } catch (e: Exception) {
                FingerprintType.UNKNOWN
            }
        }

    suspend fun setFingerprintType(type: FingerprintType) {
        context.dataStore.edit { preferences ->
            preferences[FINGERPRINT_TYPE_KEY] = type.name
        }
    }

    val useDeviceLockFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_DEVICE_LOCK_KEY] ?: false // Default to false so they can opt-in to system lock, or true to default to system lock. Let's make it default to true to deliver immediately.
        }

    val unlockPatternFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[UNLOCK_PATTERN_KEY]
        }

    val lockedAppsFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[LOCKED_APPS_KEY] ?: emptySet()
        }

    val appCategoriesFlow: Flow<Map<String, String>> = context.dataStore.data
        .map { preferences ->
            val set = preferences[APP_CATEGORIES_KEY] ?: emptySet()
            set.mapNotNull { entry ->
                val parts = entry.split('|', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }

    val appTimersFlow: Flow<Map<String, Long>> = context.dataStore.data
        .map { preferences ->
            val set = preferences[APP_TIMERS_KEY] ?: emptySet()
            set.mapNotNull { entry ->
                val parts = entry.split('|', limit = 2)
                if (parts.size == 2) {
                    val pkg = parts[0]
                    val ms = parts[1].toLongOrNull() ?: 60_000L
                    pkg to ms
                } else null
            }.toMap()
        }

    val gracePeriodFlow: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[GRACE_PERIOD_KEY] ?: 60_000L // Default 1 minute
        }

    val relockOnScreenOffFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[RELOCK_ON_SCREEN_OFF_KEY] ?: true // Default true
        }

    suspend fun setGracePeriod(timeoutMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[GRACE_PERIOD_KEY] = timeoutMs
        }
    }

    suspend fun setRelockOnScreenOff(relock: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RELOCK_ON_SCREEN_OFF_KEY] = relock
        }
    }

    suspend fun lockApp(packageName: String, category: String, customGracePeriodMs: Long) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS_KEY] ?: emptySet()
            val newApps = currentApps.toMutableSet()
            newApps.add(packageName)
            preferences[LOCKED_APPS_KEY] = newApps

            val currentEntries = preferences[APP_CATEGORIES_KEY] ?: emptySet()
            val filteredEntries = currentEntries.filterNot { it.startsWith("$packageName|") }.toMutableSet()
            filteredEntries.add("$packageName|$category")
            preferences[APP_CATEGORIES_KEY] = filteredEntries

            val currentTimers = preferences[APP_TIMERS_KEY] ?: emptySet()
            val filteredTimers = currentTimers.filterNot { it.startsWith("$packageName|") }.toMutableSet()
            filteredTimers.add("$packageName|$customGracePeriodMs")
            preferences[APP_TIMERS_KEY] = filteredTimers
        }
    }

    suspend fun unlockApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS_KEY] ?: emptySet()
            val newApps = currentApps.toMutableSet()
            newApps.remove(packageName)
            preferences[LOCKED_APPS_KEY] = newApps

            val currentEntries = preferences[APP_CATEGORIES_KEY] ?: emptySet()
            val filteredEntries = currentEntries.filterNot { it.startsWith("$packageName|") }.toSet()
            preferences[APP_CATEGORIES_KEY] = filteredEntries

            val currentTimers = preferences[APP_TIMERS_KEY] ?: emptySet()
            val filteredTimers = currentTimers.filterNot { it.startsWith("$packageName|") }.toSet()
            preferences[APP_TIMERS_KEY] = filteredTimers
        }
    }

    suspend fun toggleAppLock(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS_KEY] ?: emptySet()
            val newApps = currentApps.toMutableSet()
            if (newApps.contains(packageName)) {
                newApps.remove(packageName)
                
                val currentEntries = preferences[APP_CATEGORIES_KEY] ?: emptySet()
                val filteredEntries = currentEntries.filterNot { it.startsWith("$packageName|") }.toSet()
                preferences[APP_CATEGORIES_KEY] = filteredEntries

                val currentTimers = preferences[APP_TIMERS_KEY] ?: emptySet()
                val filteredTimers = currentTimers.filterNot { it.startsWith("$packageName|") }.toSet()
                preferences[APP_TIMERS_KEY] = filteredTimers
            } else {
                newApps.add(packageName)
                
                val currentEntries = preferences[APP_CATEGORIES_KEY] ?: emptySet()
                val filteredEntries = currentEntries.filterNot { it.startsWith("$packageName|") }.toMutableSet()
                filteredEntries.add("$packageName|Others")
                preferences[APP_CATEGORIES_KEY] = filteredEntries

                val currentTimers = preferences[APP_TIMERS_KEY] ?: emptySet()
                val filteredTimers = currentTimers.filterNot { it.startsWith("$packageName|") }.toMutableSet()
                filteredTimers.add("$packageName|60000")
                preferences[APP_TIMERS_KEY] = filteredTimers
            }
            preferences[LOCKED_APPS_KEY] = newApps
        }
    }
    
    suspend fun getLockedApps(): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[LOCKED_APPS_KEY] ?: emptySet()
    }

    suspend fun setUnlockPattern(pattern: String) {
        context.dataStore.edit { preferences ->
            preferences[UNLOCK_PATTERN_KEY] = pattern
        }
    }

    suspend fun setUseDeviceLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_DEVICE_LOCK_KEY] = enabled
        }
    }
}
