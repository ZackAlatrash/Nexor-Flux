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
    private val backgroundScheduler: BackgroundSyncScheduler = NoopBackgroundSyncScheduler,
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
     * Backfills the last [days] days of steps from Health Connect history (manual days preserved).
     * Run once on first connect so streaks/trends reflect existing history. Serialized with
     * [syncToday] via the same mutex.
     */
    suspend fun backfillStepsHistory(days: Long = 30) = mutex.withLock {
        val history = hcRepository.readStepsHistory(days)
        if (history.isNotEmpty()) logRepository.applyHealthConnectStepsHistory(history)
    }

    /** Fire-and-forget [backfillStepsHistory]; failures are logged, never surfaced. */
    fun backfillStepsHistoryInBackground(days: Long = 30) {
        appScope.launch {
            runCatching { backfillStepsHistory(days) }
                .onFailure { Log.w(TAG, "Steps history backfill failed", it) }
        }
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

    /**
     * Suspending guarded sync for background work (WorkManager): syncs only if Health Connect is
     * enabled and permitted. Returns true if a sync ran. Unlike [syncIfDue] this awaits completion
     * so the Worker can report a result.
     */
    suspend fun syncIfEnabledAndPermitted(): Boolean {
        val prefs = planRepository.preferences.first()
        if (!prefs.healthConnectEnabled) return false
        if (!hcRepository.hasPermissions()) return false
        syncToday()
        return true
    }

    /** Schedule periodic background sync (idempotent). Call when Health Connect becomes enabled. */
    fun enableBackgroundSync() = backgroundScheduler.enable()

    /** Cancel periodic background sync. Call when Health Connect is disabled. */
    fun disableBackgroundSync() = backgroundScheduler.disable()

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
