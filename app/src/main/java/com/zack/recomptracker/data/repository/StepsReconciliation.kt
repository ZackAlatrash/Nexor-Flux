package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.StepsSource

/** Outcome of reconciling an existing day's steps with a fresh Health Connect read. */
data class StepsReconciliation(val steps: Int?, val source: String?)

/**
 * Decides the steps value + provenance for a day when a Health Connect sync arrives.
 *
 * Rule (see [StepsSource]):
 * - Health Connect has no steps for the day → keep whatever we already had.
 * - The existing value was entered **manually** and is at least the Health Connect reading →
 *   keep it; the user's deliberate entry wins over a lower/equal sensor total.
 * - Otherwise → take the Health Connect value. This covers the refresh of a prior HC value
 *   (fixing the long-standing bug where any populated value permanently blocked HC from
 *   refreshing the day), legacy null-source values, and a manual entry that real recorded
 *   steps have since overtaken — the sensor count is the better number once it's higher.
 *
 * Pure — no Android or DB dependencies, so it is unit-tested directly.
 */
fun reconcileSteps(
    existingSteps: Int?,
    existingSource: String?,
    healthConnectSteps: Int?,
): StepsReconciliation = when {
    healthConnectSteps == null -> StepsReconciliation(existingSteps, existingSource)
    existingSource == StepsSource.MANUAL && existingSteps != null && healthConnectSteps <= existingSteps ->
        StepsReconciliation(existingSteps, existingSource)
    else -> StepsReconciliation(healthConnectSteps, StepsSource.HEALTH_CONNECT)
}

/**
 * Decides the steps value + provenance when the user saves a metrics form (Body check-in / edit).
 *
 * The form pre-fills steps from the day's log, so a save must not blindly stamp the value as
 * [StepsSource.MANUAL] — doing so converted synced Health Connect steps into a frozen "manual"
 * entry on every check-in (the steps-stop-updating bug). Only a value the user actually edited
 * is a deliberate manual entry:
 * - [stepsEdited] → save the typed value as MANUAL (or clear both when the field was emptied).
 * - Untouched → preserve the day's existing steps *and* source verbatim, ignoring the possibly
 *   stale prefill (a sync may have raised the value after the form was opened).
 *
 * Pure — no Android or DB dependencies, so it is unit-tested directly.
 */
fun resolveSavedSteps(
    stepsEdited: Boolean,
    inputSteps: Int?,
    existingSteps: Int?,
    existingSource: String?,
): StepsReconciliation = if (stepsEdited) {
    StepsReconciliation(inputSteps, inputSteps?.let { StepsSource.MANUAL })
} else {
    StepsReconciliation(existingSteps, existingSource)
}
