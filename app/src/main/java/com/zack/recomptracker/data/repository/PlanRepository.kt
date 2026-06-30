package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The day-judging targets from the current preferences. */
fun PlanPreferences.toPlanTargets(): PlanTargets = PlanTargets(
    calories = targetCalories,
    proteinG = targetProteinG,
    carbsG = targetCarbsG,
    fatG = targetFatG,
    zoneLowerBound = calorieZoneLowerBound,
    zoneUpperBound = calorieZoneUpperBound,
)

/** Build a historical version row from preferences, stamped with [effectiveFrom]/[createdAt]. */
fun PlanPreferences.toPlanVersionEntity(effectiveFrom: LocalDate, createdAt: String): PlanVersionEntity =
    PlanVersionEntity(
        effectiveFrom = effectiveFrom.toString(),
        targetCalories = targetCalories,
        targetProteinG = targetProteinG,
        targetCarbsG = targetCarbsG,
        targetFatG = targetFatG,
        calorieZoneLowerBound = calorieZoneLowerBound,
        calorieZoneUpperBound = calorieZoneUpperBound,
        createdAt = createdAt,
    )

private fun PlanVersionEntity.toPlanVersion(): PlanVersion = PlanVersion(
    effectiveFrom = LocalDate.parse(effectiveFrom),
    targets = PlanTargets(
        calories = targetCalories,
        proteinG = targetProteinG,
        carbsG = targetCarbsG,
        fatG = targetFatG,
        zoneLowerBound = calorieZoneLowerBound,
        zoneUpperBound = calorieZoneUpperBound,
    ),
)

class PlanRepository(
    private val appPreferences: AppPreferences,
    private val planVersionDao: PlanVersionDao,
    private val dateProvider: DateProvider,
) {
    val preferences: Flow<PlanPreferences> = appPreferences.preferences

    /** All historical plan versions, oldest first. */
    fun observeVersions(): Flow<List<PlanVersion>> =
        planVersionDao.observeAll().map { rows -> rows.map { it.toPlanVersion() } }

    /** The plan in effect on [date] as a Flow. Falls back to current prefs when no history yet. */
    fun observePlanOn(date: LocalDate): Flow<PlanTargets> =
        combine(observeVersions(), preferences) { versions, prefs ->
            PlanHistory.planOnOrFallback(versions, date, prefs.toPlanTargets())
        }

    /** The plan in effect on [date] (one-shot). Falls back to current prefs when no history yet. */
    suspend fun planOn(date: LocalDate): PlanTargets {
        val versions = planVersionDao.getAll().map { it.toPlanVersion() }
        return PlanHistory.planOnOrFallback(versions, date, appPreferences.preferences.first().toPlanTargets())
    }

    /** Resolve many dates at once. Falls back to current prefs for every date when no history yet. */
    suspend fun targetsByDate(dates: List<LocalDate>): Map<LocalDate, PlanTargets> {
        val versions = planVersionDao.getAll().map { it.toPlanVersion() }
        if (versions.isEmpty()) {
            val fallback = appPreferences.preferences.first().toPlanTargets()
            return dates.associateWith { fallback }
        }
        return PlanHistory.resolve(versions, dates)
    }

    /**
     * Persist the plan and, if the day-judging targets changed, record a history version for
     * today (upsert by date — multiple same-day changes keep only the final value). This is the
     * single choke point: every plan writer routes through save(), so history stays complete and
     * non-target edits (e.g. the Health Connect toggle) never create a version.
     */
    suspend fun save(preferences: PlanPreferences) {
        val previous = appPreferences.preferences.first()
        appPreferences.save(preferences)
        if (PlanHistory.targetsChanged(previous.toPlanTargets(), preferences.toPlanTargets())) {
            planVersionDao.upsert(
                preferences.toPlanVersionEntity(
                    effectiveFrom = dateProvider.today(),
                    createdAt = Instant.now().toString(),
                ),
            )
        }
    }

    suspend fun resetDefaults() {
        appPreferences.resetDefaults()
        planVersionDao.upsert(
            PlanPreferences().toPlanVersionEntity(
                effectiveFrom = dateProvider.today(),
                createdAt = Instant.now().toString(),
            ),
        )
    }
}
