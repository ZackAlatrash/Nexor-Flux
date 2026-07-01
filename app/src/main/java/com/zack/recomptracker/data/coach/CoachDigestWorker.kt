package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zack.recomptracker.RecompTrackerApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background pass of the proactive-coaching spine so the "Today's Coaching" slot is fresh
 * even when the user hasn't opened the app that day. Runs the same deterministic
 * [CoachDigestCoordinator.run] as the foreground [CoachDigestCoordinator.runIfDue] path — pure
 * CPU/DB work, no cloud call (phrasing is deferred to when a surface opens), so the worker stays
 * deterministic and needs **no constraints**. Enqueued when AI insights are enabled, cancelled when
 * disabled (see [CoachDigestScheduler]). Near-verbatim sibling of `HealthSyncWorker`.
 */
class CoachDigestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RecompTrackerApp ?: return Result.success()
        return runCatching { app.container.coachDigestCoordinator.run() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val UNIQUE_NAME = "coach_digest_periodic"
        private const val INTERVAL_HOURS = 24L

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<CoachDigestWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
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

/** Indirection so [CoachDigestCoordinator] stays free of WorkManager/Context (and unit-testable). */
interface CoachDigestScheduler {
    fun enable()
    fun disable()
}

/** Default no-op (used in tests / when the background digest isn't wired). */
object NoopCoachDigestScheduler : CoachDigestScheduler {
    override fun enable() {}
    override fun disable() {}
}

/** WorkManager-backed scheduler used in the app. */
class WorkManagerCoachDigestScheduler(
    private val context: Context,
) : CoachDigestScheduler {
    override fun enable() = CoachDigestWorker.enqueue(context)
    override fun disable() = CoachDigestWorker.cancel(context)
}
