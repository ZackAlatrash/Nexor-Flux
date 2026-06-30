package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.StepsSource

/** Outcome of reconciling an existing day's steps with a fresh Health Connect read. */
data class StepsReconciliation(val steps: Int?, val source: String?)

/**
 * Decides the steps value + provenance for a day when a Health Connect sync arrives.
 *
 * Rule (see [StepsSource]):
 * - Health Connect has no steps for the day → keep whatever we already had.
 * - The existing value was entered **manually** → keep it; the user's deliberate entry wins.
 * - Otherwise (existing source is Health Connect, or unknown/legacy) → take the Health Connect
 *   value. This fixes the long-standing bug where any populated value permanently blocked HC
 *   from refreshing the day (a morning "2,000" never updated to the real evening total).
 *
 * Pure — no Android or DB dependencies, so it is unit-tested directly.
 */
fun reconcileSteps(
    existingSteps: Int?,
    existingSource: String?,
    healthConnectSteps: Int?,
): StepsReconciliation = when {
    healthConnectSteps == null -> StepsReconciliation(existingSteps, existingSource)
    existingSource == StepsSource.MANUAL -> StepsReconciliation(existingSteps, existingSource)
    else -> StepsReconciliation(healthConnectSteps, StepsSource.HEALTH_CONNECT)
}
