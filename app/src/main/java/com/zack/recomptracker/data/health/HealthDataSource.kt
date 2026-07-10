package com.zack.recomptracker.data.health

import java.time.LocalDate

/**
 * The narrow slice of Health Connect the [HealthSyncCoordinator] depends on. Extracted so the
 * coordinator's read→apply orchestration can be unit-tested against a fake (the real
 * [HealthConnectRepository] talks to the device and can't run under JVM tests). The rich UI-facing
 * API (availability, permission contracts, historical nutrition import) stays on the concrete
 * repository, which the Settings/Integrations screens use directly.
 */
interface HealthDataSource {
    suspend fun hasPermissions(): Boolean

    /** Today's Health Connect snapshot (steps/weight/sleep) for [date]. */
    suspend fun readToday(date: LocalDate): HealthConnectReadResult

    /** Total steps per local calendar day over the trailing [days] days (inclusive of today). */
    suspend fun readStepsHistory(days: Long = 30): Map<LocalDate, Int>
}
