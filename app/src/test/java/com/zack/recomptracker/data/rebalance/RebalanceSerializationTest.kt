package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pure (de)serialization for [RebalanceState]: full round-trip + failure tolerance. */
class RebalanceSerializationTest {

    /**
     * A fully-populated OFFERED plan — every optional field non-null — as the [RebalanceState.active]
     * entry. `intensity`/`partial` deliberately use non-default values (LIGHT / true, vs. the
     * STANDARD / false Kotlin defaults) so the round-trip tests prove the codec actually threads them
     * through rather than merely matching defaults by coincidence.
     */
    private val activePlan = RebalancePlan(
        id = "11111111-1111-1111-1111-111111111111",
        triggerDateIso = "2026-07-03",
        startDateIso = "2026-07-06",
        endDateIso = "2026-07-08",
        lengthDays = 3,
        mode = RebalanceMode.MOVE_MORE,
        baseCalories = 2400,
        dailyCalorieReduction = 150,
        extraDailySteps = 1500,
        baseStepGoal = 8000,
        recentAvgSteps = 6200,
        surplusKcal = 900,
        recoveredKcal = 675,
        status = RebalanceStatus.OFFERED,
        createdAtIso = "2026-07-05T08:00:00",
        decidedAtIso = "2026-07-05T08:05:00",
        endedReason = "unrecoverable",
        intensity = RebalanceIntensity.LIGHT,
        partial = true,
    )

    private val historyRecordOne = RebalancePlan(
        id = "22222222-2222-2222-2222-222222222222",
        triggerDateIso = "2026-06-20",
        startDateIso = "2026-06-22",
        endDateIso = "2026-06-24",
        lengthDays = 3,
        mode = RebalanceMode.EAT_LESS,
        baseCalories = 2200,
        dailyCalorieReduction = 200,
        extraDailySteps = 0,
        baseStepGoal = null,
        recentAvgSteps = null,
        surplusKcal = 700,
        recoveredKcal = 600,
        status = RebalanceStatus.COMPLETED,
        createdAtIso = "2026-06-21T09:00:00",
        decidedAtIso = "2026-06-21T09:10:00",
        endedReason = "completed",
        intensity = RebalanceIntensity.FULL,
        partial = false,
    )

    private val historyRecordTwo = RebalancePlan(
        id = "33333333-3333-3333-3333-333333333333",
        triggerDateIso = "2026-06-27",
        startDateIso = "2026-06-29",
        endDateIso = "2026-07-01",
        lengthDays = 3,
        mode = RebalanceMode.BALANCED,
        baseCalories = 2300,
        dailyCalorieReduction = 120,
        extraDailySteps = 800,
        baseStepGoal = 9000,
        recentAvgSteps = 7000,
        surplusKcal = 500,
        recoveredKcal = 380,
        status = RebalanceStatus.DECLINED,
        createdAtIso = "2026-06-28T07:30:00",
        decidedAtIso = null,
        endedReason = "expired",
        intensity = RebalanceIntensity.STANDARD,
        partial = false,
    )

    private val fullState = RebalanceState(
        active = activePlan,
        history = listOf(historyRecordOne, historyRecordTwo),
        mode = RebalanceMode.MOVE_MORE,
    )

    @Test
    fun `encode then decode round-trips a fully-populated state`() {
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(fullState))
        assertEquals(fullState, decoded)
    }

    @Test
    fun `round-trip preserves every field of the active plan`() {
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(fullState))
        val decodedActive = decoded.active!!
        assertEquals(activePlan.id, decodedActive.id)
        assertEquals(activePlan.triggerDateIso, decodedActive.triggerDateIso)
        assertEquals(activePlan.startDateIso, decodedActive.startDateIso)
        assertEquals(activePlan.endDateIso, decodedActive.endDateIso)
        assertEquals(activePlan.lengthDays, decodedActive.lengthDays)
        assertEquals(activePlan.mode, decodedActive.mode)
        assertEquals(activePlan.baseCalories, decodedActive.baseCalories)
        assertEquals(activePlan.dailyCalorieReduction, decodedActive.dailyCalorieReduction)
        assertEquals(activePlan.extraDailySteps, decodedActive.extraDailySteps)
        assertEquals(activePlan.baseStepGoal, decodedActive.baseStepGoal)
        assertEquals(activePlan.recentAvgSteps, decodedActive.recentAvgSteps)
        assertEquals(activePlan.surplusKcal, decodedActive.surplusKcal)
        assertEquals(activePlan.recoveredKcal, decodedActive.recoveredKcal)
        assertEquals(activePlan.status, decodedActive.status)
        assertEquals(activePlan.createdAtIso, decodedActive.createdAtIso)
        assertEquals(activePlan.decidedAtIso, decodedActive.decidedAtIso)
        assertEquals(activePlan.endedReason, decodedActive.endedReason)
        assertEquals(activePlan.intensity, decodedActive.intensity)
        assertEquals(activePlan.partial, decodedActive.partial)
    }

    @Test
    fun `round-trip preserves history records in order`() {
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(fullState))
        assertEquals(2, decoded.history.size)
        assertEquals(historyRecordOne, decoded.history[0])
        assertEquals(historyRecordTwo, decoded.history[1])
    }

    @Test
    fun `round-trip preserves the sticky mode preference`() {
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(fullState))
        assertEquals(RebalanceMode.MOVE_MORE, decoded.mode)
    }

    @Test
    fun `round-trips a default empty state with no active plan and empty history`() {
        val empty = RebalanceState()
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(empty))
        assertEquals(empty, decoded)
    }

    @Test
    fun `round-trips a plan with all nullable fields null`() {
        val minimalPlan = activePlan.copy(
            baseStepGoal = null,
            recentAvgSteps = null,
            decidedAtIso = null,
            endedReason = null,
        )
        val state = RebalanceState(active = minimalPlan)
        val decoded = RebalanceSerialization.decode(RebalanceSerialization.encode(state))
        assertEquals(state, decoded)
    }

    @Test
    fun `decode of null returns an empty default state`() {
        assertEquals(RebalanceState(), RebalanceSerialization.decode(null))
    }

    @Test
    fun `decode of blank string returns an empty default state`() {
        assertEquals(RebalanceState(), RebalanceSerialization.decode(""))
    }

    @Test
    fun `decode of malformed json returns an empty default state`() {
        assertEquals(RebalanceState(), RebalanceSerialization.decode("{not json"))
    }

    @Test
    fun `decode of an empty json object returns an empty default state`() {
        assertEquals(RebalanceState(), RebalanceSerialization.decode("{}"))
    }

    @Test
    fun `decode of a json array instead of object returns an empty default state`() {
        assertEquals(RebalanceState(), RebalanceSerialization.decode("[]"))
    }

    @Test
    fun `decode tolerates an unknown enum name by falling back to the empty default state`() {
        // A stored blob with a mode name that no longer exists (schema drift) must never throw.
        val raw = RebalanceSerialization.encode(fullState).replace("MOVE_MORE", "SOME_UNKNOWN_MODE")
        assertEquals(RebalanceState(), RebalanceSerialization.decode(raw))
    }

    /**
     * The key legacy forward-compat test (backup-safety rule, spec §16): a plan blob written before
     * `intensity`/`partial` existed has neither key. A missing key must DEFAULT (STANDARD / false),
     * never `?: return null` the whole plan — a pre-dynamic-rebalance persisted/backed-up record must
     * still decode.
     */
    @Test
    fun `a plan blob with no intensity or partial key decodes to STANDARD intensity and partial false`() {
        val legacyPlanJson: JsonObject = buildJsonObject {
            put("id", activePlan.id)
            put("triggerDateIso", activePlan.triggerDateIso)
            put("startDateIso", activePlan.startDateIso)
            put("endDateIso", activePlan.endDateIso)
            put("lengthDays", activePlan.lengthDays)
            put("mode", activePlan.mode.name)
            put("baseCalories", activePlan.baseCalories)
            put("dailyCalorieReduction", activePlan.dailyCalorieReduction)
            put("extraDailySteps", activePlan.extraDailySteps)
            put("baseStepGoal", activePlan.baseStepGoal)
            put("recentAvgSteps", activePlan.recentAvgSteps)
            put("surplusKcal", activePlan.surplusKcal)
            put("recoveredKcal", activePlan.recoveredKcal)
            put("status", activePlan.status.name)
            put("createdAtIso", activePlan.createdAtIso)
            put("decidedAtIso", activePlan.decidedAtIso)
            put("endedReason", activePlan.endedReason)
            // Deliberately NO "intensity" or "partial" key — simulates a pre-dynamic-rebalance blob.
        }
        val legacyStateJson = buildJsonObject {
            put("active", legacyPlanJson)
            put("mode", RebalanceMode.BALANCED.name)
        }

        val decoded = RebalanceSerialization.decode(Json.encodeToString(JsonObject.serializer(), legacyStateJson))

        val decodedActive = decoded.active
        assertEquals(RebalanceIntensity.STANDARD, decodedActive?.intensity)
        assertEquals(false, decodedActive?.partial)
        // Every other field on the plan survives untouched — the missing keys must not nuke the record.
        assertEquals(activePlan.id, decodedActive?.id)
        assertEquals(activePlan.mode, decodedActive?.mode)
        assertEquals(activePlan.surplusKcal, decodedActive?.surplusKcal)
        assertEquals(activePlan.status, decodedActive?.status)
        assertFalse("history must decode too (empty, since none was included)", decoded.history.isNotEmpty())
    }
}
