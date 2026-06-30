package com.zack.recomptracker.data.health

import android.util.Log
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for Health Connect "sync today" across the app: the Settings "Sync now"
 * button, the foreground app-open auto-sync, and (later) background work all route through here so
 * there is exactly one read+apply+timestamp path. Steps are reconciled by provenance in
 * [LogRepository.applyHealthConnectSync]; the sync timestamp is persisted to [PlanPreferences].
 */
class HealthSyncCoordinator(
    private val hcRepository: HealthConnectRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val appScope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    /**
     * Reads today's Health Connect data, applies it, and records the sync timestamp. Serialized so
     * the foreground and Settings paths can't overlap. Returns the read result.
     */
    suspend fun syncToday(): HealthConnectReadResult = mutex.withLock {
        val date = dateProvider.today()
        val result = hcRepository.readToday(date)
        Log.d(TAG, "syncToday: steps=${result.steps} weightKg=${result.weightKg} sleepHours=${result.sleepHours}")
        logRepository.applyHealthConnectSync(date, result)
        val prefs = planRepository.preferences.first()
        // Non-target edit → never creates a plan version (see PlanRepository.save).
        planRepository.save(prefs.copy(healthConnectLastSyncEpochMs = now()))
        result
    }

    /**
     * Fire-and-forget auto-sync, safe to call on every app foreground. Runs only when Health
     * Connect is enabled, the debounce window has elapsed, and permissions are granted. Failures
     * are logged, never surfaced.
     */
    fun syncIfDue(minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS) {
        appScope.launch {
            val prefs = planRepository.preferences.first()
            if (!isAutoSyncDue(prefs.healthConnectEnabled, prefs.healthConnectLastSyncEpochMs, now(), minIntervalMs)) {
                return@launch
            }
            runCatching {
                if (hcRepository.hasPermissions()) syncToday()
            }.onFailure { Log.w(TAG, "Auto-sync failed", it) }
        }
    }

    companion object {
        private const val TAG = "HealthSyncCoordinator"

        /** Debounce window for auto-sync: at most once per 15 min of foregrounding. */
        const val DEFAULT_MIN_INTERVAL_MS = 15 * 60 * 1000L

        /**
         * Pure gate for auto-sync: enabled, and either never synced or the last sync is at least
         * [minIntervalMs] old. The permission check is intentionally excluded (it is suspend) and
         * applied by the caller.
         */
        fun isAutoSyncDue(
            enabled: Boolean,
            lastSyncEpochMs: Long?,
            nowEpochMs: Long,
            minIntervalMs: Long,
        ): Boolean =
            enabled && (lastSyncEpochMs == null || nowEpochMs - lastSyncEpochMs >= minIntervalMs)
    }
}
