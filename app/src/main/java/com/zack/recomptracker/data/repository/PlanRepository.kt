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

    /** The plan in effect on [date] as a Flow (re-emits when history changes). */
    fun observePlanOn(date: LocalDate): Flow<PlanTargets> =
        observeVersions().map { versions -> PlanHistory.planOn(versions, date) }

    /** The plan in effect on [date] (one-shot read). */
    suspend fun planOn(date: LocalDate): PlanTargets =
        PlanHistory.planOn(planVersionDao.getAll().map { it.toPlanVersion() }, date)

    /** Resolve many dates at once (one DAO read). */
    suspend fun targetsByDate(dates: List<LocalDate>): Map<LocalDate, PlanTargets> =
        PlanHistory.resolve(planVersionDao.getAll().map { it.toPlanVersion() }, dates)

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
