package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Seeds the plan-history ledger exactly once. Room migrations can't read DataStore, so the
 * v12->v13 migration only creates the (empty) table; this runs at app start and, when the table
 * is empty, writes ONE baseline row from the current DataStore plan effective from
 * [PlanHistory.BASELINE_DATE]. Every already-logged day then resolves to the current plan (no
 * behavioral regression), and the user's NEXT plan change correctly only affects today forward.
 * Idempotent: a populated table is left untouched.
 */
class PlanHistoryInitializer(
    private val planVersionDao: PlanVersionDao,
    private val currentPreferences: suspend () -> PlanPreferences,
) {
    suspend fun seedIfEmpty() {
        if (planVersionDao.count() > 0) return
        val prefs = currentPreferences()
        planVersionDao.upsert(
            prefs.toPlanVersionEntity(
                effectiveFrom = PlanHistory.BASELINE_DATE,
                createdAt = Instant.now().toString(),
            ),
        )
    }

    companion object {
        /** Convenience factory using a repository's preferences flow as the current source. */
        fun from(planVersionDao: PlanVersionDao, planRepository: PlanRepository) =
            PlanHistoryInitializer(planVersionDao) { planRepository.preferences.first() }
    }
}
