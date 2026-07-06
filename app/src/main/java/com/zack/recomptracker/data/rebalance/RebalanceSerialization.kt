package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Pure (Android-free) JSON (de)serialization for [RebalanceState], extracted so the value logic is
 * unit-testable without DataStore I/O. Mirrors [com.zack.recomptracker.data.coach.CoachExperimentSerialization]
 * — same failure-tolerant contract (a malformed/stale-schema string never throws) — but built with
 * manual `JsonObject`/`buildJsonObject` construction (à la `parseTavilyResponse` in
 * `data/remote/WebSearchModels.kt` / `UserProfilePreferencesStore`) rather than `@Serializable` data
 * classes, since [RebalancePlan]/[RebalanceState] live in `domain/rebalance` and are deliberately not
 * annotated there (Task 1 scope; this file does not modify that boundary).
 *
 * Deserialization is failure-tolerant: a blank string, invalid JSON, a JSON value that isn't an
 * object, or an unknown/malformed enum name all decode to [RebalanceState] (the all-defaults empty
 * state) rather than throwing, so a corrupt persisted value can never crash a surface reading it.
 */
internal object RebalanceSerialization {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: RebalanceState): String = encodeState(state).toString()

    /** Decode [raw]; returns [RebalanceState] (the empty default) for any blank/malformed/unknown-schema input. */
    fun decode(raw: String?): RebalanceState {
        if (raw.isNullOrBlank()) return RebalanceState()
        return runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return RebalanceState()
            decodeState(root)
        }.getOrNull() ?: RebalanceState()
    }

    // ── State ────────────────────────────────────────────────────────────────

    private fun encodeState(state: RebalanceState): JsonObject = buildJsonObject {
        state.active?.let { put("active", encodePlan(it)) }
        putJsonArray("history") { state.history.forEach { add(encodePlan(it)) } }
        put("mode", state.mode.name)
    }

    /** Returns the empty default [RebalanceState] on any unknown enum name or shape mismatch. */
    private fun decodeState(root: JsonObject): RebalanceState {
        val active = (root["active"] as? JsonObject)?.let { decodePlan(it) ?: return RebalanceState() }
        val history = (root["history"] as? JsonArray)?.map { el ->
            // `map` is inline, so these non-local returns exit decodeState itself; a non-inline call would silently change semantics.
            val obj = el as? JsonObject ?: return RebalanceState()
            decodePlan(obj) ?: return RebalanceState()
        } ?: emptyList()
        val mode = root["mode"]?.jsonPrimitive?.contentOrNull?.let { name ->
            runCatching { RebalanceMode.valueOf(name) }.getOrNull() ?: return RebalanceState()
        } ?: RebalanceState().mode
        return RebalanceState(active = active, history = history, mode = mode)
    }

    // ── Plan ─────────────────────────────────────────────────────────────────

    private fun encodePlan(plan: RebalancePlan): JsonObject = buildJsonObject {
        put("id", plan.id)
        put("triggerDateIso", plan.triggerDateIso)
        put("startDateIso", plan.startDateIso)
        put("endDateIso", plan.endDateIso)
        put("lengthDays", plan.lengthDays)
        put("mode", plan.mode.name)
        put("baseCalories", plan.baseCalories)
        put("dailyCalorieReduction", plan.dailyCalorieReduction)
        put("extraDailySteps", plan.extraDailySteps)
        putNullableInt("baseStepGoal", plan.baseStepGoal)
        putNullableInt("recentAvgSteps", plan.recentAvgSteps)
        put("surplusKcal", plan.surplusKcal)
        put("recoveredKcal", plan.recoveredKcal)
        put("status", plan.status.name)
        put("createdAtIso", plan.createdAtIso)
        put("decidedAtIso", plan.decidedAtIso)
        put("endedReason", plan.endedReason)
        put("intensity", plan.intensity.name)
        put("partial", plan.partial)
    }

    /** Returns `null` for a missing/blank required field, a malformed number, or an unknown enum name. */
    private fun decodePlan(obj: JsonObject): RebalancePlan? {
        val id = obj.str("id") ?: return null
        val triggerDateIso = obj.str("triggerDateIso") ?: return null
        val startDateIso = obj.str("startDateIso") ?: return null
        val endDateIso = obj.str("endDateIso") ?: return null
        val lengthDays = obj.int("lengthDays") ?: return null
        val mode = obj.str("mode")?.let { runCatching { RebalanceMode.valueOf(it) }.getOrNull() } ?: return null
        val baseCalories = obj.int("baseCalories") ?: return null
        val dailyCalorieReduction = obj.int("dailyCalorieReduction") ?: return null
        val extraDailySteps = obj.int("extraDailySteps") ?: return null
        val baseStepGoal = obj.int("baseStepGoal")
        val recentAvgSteps = obj.int("recentAvgSteps")
        val surplusKcal = obj.int("surplusKcal") ?: return null
        val recoveredKcal = obj.int("recoveredKcal") ?: return null
        val status = obj.str("status")?.let { runCatching { RebalanceStatus.valueOf(it) }.getOrNull() } ?: return null
        val createdAtIso = obj.str("createdAtIso") ?: return null
        val decidedAtIso = obj.str("decidedAtIso")
        val endedReason = obj.str("endedReason")
        // Backup-safety rule (spec §16): a missing/malformed "intensity" key DEFAULTs to STANDARD —
        // unlike every required field above, this must never `?: return null` the whole plan, since a
        // pre-dynamic-rebalance persisted/backed-up record has neither key at all.
        val intensity = obj.str("intensity")
            ?.let { runCatching { RebalanceIntensity.valueOf(it) }.getOrNull() }
            ?: RebalanceIntensity.STANDARD
        val partial = obj.bool("partial") ?: false

        return RebalancePlan(
            id = id,
            triggerDateIso = triggerDateIso,
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            lengthDays = lengthDays,
            mode = mode,
            baseCalories = baseCalories,
            dailyCalorieReduction = dailyCalorieReduction,
            extraDailySteps = extraDailySteps,
            baseStepGoal = baseStepGoal,
            recentAvgSteps = recentAvgSteps,
            surplusKcal = surplusKcal,
            recoveredKcal = recoveredKcal,
            status = status,
            createdAtIso = createdAtIso,
            decidedAtIso = decidedAtIso,
            endedReason = endedReason,
            intensity = intensity,
            partial = partial,
        )
    }

    // ── JsonObject field read/write helpers ─────────────────────────────────

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    /** Writes [value], or explicit JSON `null` when absent (so a decoded object always has the key). */
    private fun JsonObjectBuilder.putNullableInt(key: String, value: Int?) {
        put(key, value?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?))
    }
}
