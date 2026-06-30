package com.zack.recomptracker.data.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zack.recomptracker.RecompTrackerApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync so streaks/trends don't silently break overnight when the user doesn't
 * open the app. Runs the same guarded coordinator path as foreground sync. Enqueued when Health
 * Connect is enabled and cancelled when it's disabled (see [BackgroundSyncScheduler]).
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RecompTrackerApp ?: return Result.success()
        return runCatching { app.container.healthSyncCoordinator.syncIfEnabledAndPermitted() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val UNIQUE_NAME = "health_connect_periodic_sync"
        private const val INTERVAL_HOURS = 4L

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // KEEP: don't reset the schedule if it's already running on re-enable.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

/** Indirection so [HealthSyncCoordinator] stays free of WorkManager/Context (and unit-testable). */
interface BackgroundSyncScheduler {
    fun enable()
    fun disable()
}

/** Default no-op (used in tests / when background sync isn't wired). */
object NoopBackgroundSyncScheduler : BackgroundSyncScheduler {
    override fun enable() {}
    override fun disable() {}
}

/** WorkManager-backed scheduler used in the app. */
class WorkManagerBackgroundSyncScheduler(
    private val context: Context,
) : BackgroundSyncScheduler {
    override fun enable() = HealthSyncWorker.enqueue(context)
    override fun disable() = HealthSyncWorker.cancel(context)
}
