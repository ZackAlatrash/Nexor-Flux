package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.RebalanceDayBar
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Developer-only scenarios that force the Weekly Rebalance dashboard card into a specific phase so the
 * redesigned UI can be eyeballed on-device without waiting for real data to trigger each state. NOT part
 * of the production rebalance flow — [RebalanceDebugScenarios.build] mints plausible [RebalancePlan]s and
 * [RebalanceState]s that the existing `RebalanceViewModel` face-derivation reads exactly as if the engine
 * had produced them.
 *
 * See the offer/progress/note face mapping in
 * [com.zack.recomptracker.ui.dashboard.RebalanceViewModel.deriveFace]: `active.status == OFFERED` → the
 * offer popup; `ACTIVE` → progress (day-X-of-Y via
 * [com.zack.recomptracker.domain.rebalance.EffectiveTargets.planDayInfo]); `NO_ADJUSTMENT` → a neutral
 * note; the `endedNotice` plan's `COMPLETED`/`ENDED_EARLY` status → the completion / graceful-end notes.
 */
enum class RebalanceDebugScenario(val label: String, val description: String) {
    OFFER_BALANCED("Offer · Balanced", "Popup: −200 kcal + 1,200 steps, 4 days"),
    OFFER_EAT_LESS("Offer · Eat less", "Popup: calorie-only, −300 kcal, 3 days"),
    OFFER_MOVE_MORE("Offer · Move more", "Popup: steps-only, +2,500 steps, 4 days"),
    PROGRESS_DAY0("Progress · starts tomorrow", "Ribbon: accepted late, day 0"),
    PROGRESS_MID("Progress · day 2 of 4", "Ribbon on Today card + tap detail"),
    PROGRESS_FINAL("Progress · final day", "Ribbon: day 4 of 4"),
    COMPLETION("Completed", "Gold celebration note"),
    GRACEFUL_END("Ended early", "Green graceful-end note"),
    NO_ADJUSTMENT("No-adjustment note", "Green 'keep your normal plan' note"),
    CLEAR("Clear / back to normal", "Removes any forced state"),
}

/**
 * The three UI-driving sources a scenario sets, mirroring the tuple
 * `RebalanceViewModel` combines: the persisted [RebalanceState], the in-memory ended notice, and the
 * offer's weekly-bars viz. Applied by [RebalanceCoordinator.debugApply].
 */
data class RebalanceDebugApplication(
    val state: RebalanceState,
    val endedNotice: RebalancePlan?,
    val offerWindow: List<RebalanceDayBar>?,
)

/**
 * Pure builder for the debug scenarios: given a scenario, `today`, and a base calorie target, produces the
 * exact [RebalanceDebugApplication] to install. Deterministic — every date is derived from the `today`
 * param (no clock reads) and no randomness — so on-device runs are reproducible. Ids/timestamps come from
 * the injected [newId]/[nowIso] so the caller controls minting.
 */
object RebalanceDebugScenarios {

    fun build(
        scenario: RebalanceDebugScenario,
        today: LocalDate,
        baseCalories: Int,
        newId: () -> String,
        nowIso: () -> String,
    ): RebalanceDebugApplication = when (scenario) {
        RebalanceDebugScenario.OFFER_BALANCED -> RebalanceDebugApplication(
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.OFFERED,
                    mode = RebalanceMode.BALANCED,
                    triggerDate = today.minusDays(1),
                    start = today.plusDays(1),
                    end = today.plusDays(4),
                    lengthDays = 4,
                    baseCalories = baseCalories,
                    reduction = 200,
                    extraSteps = 1200,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 460,
                    createdAt = nowIso(),
                    decidedAt = null,
                ),
            ),
            endedNotice = null,
            offerWindow = syntheticOfferWindow(today, baseCalories, reduction = 200, lengthDays = 4),
        )

        RebalanceDebugScenario.OFFER_EAT_LESS -> RebalanceDebugApplication(
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.OFFERED,
                    mode = RebalanceMode.EAT_LESS,
                    triggerDate = today.minusDays(1),
                    start = today.plusDays(1),
                    end = today.plusDays(3),
                    lengthDays = 3,
                    baseCalories = baseCalories,
                    reduction = 300,
                    extraSteps = 0,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 675,
                    createdAt = nowIso(),
                    decidedAt = null,
                ),
            ),
            endedNotice = null,
            offerWindow = syntheticOfferWindow(today, baseCalories, reduction = 300, lengthDays = 3),
        )

        RebalanceDebugScenario.OFFER_MOVE_MORE -> RebalanceDebugApplication(
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.OFFERED,
                    mode = RebalanceMode.MOVE_MORE,
                    triggerDate = today.minusDays(1),
                    start = today.plusDays(1),
                    end = today.plusDays(4),
                    lengthDays = 4,
                    baseCalories = baseCalories,
                    reduction = 0,
                    extraSteps = 2500,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 400,
                    createdAt = nowIso(),
                    decidedAt = null,
                ),
            ),
            endedNotice = null,
            offerWindow = syntheticOfferWindow(today, baseCalories, reduction = 0, lengthDays = 4),
        )

        RebalanceDebugScenario.PROGRESS_DAY0 -> RebalanceDebugApplication(
            // Accepted late in the day: today < startDate, so planDayInfo(today) → null → the
            // "starts tomorrow" (day 0) branch in progressFace.
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.ACTIVE,
                    mode = RebalanceMode.BALANCED,
                    triggerDate = today.minusDays(1),
                    start = today.plusDays(1),
                    end = today.plusDays(4),
                    lengthDays = 4,
                    baseCalories = baseCalories,
                    reduction = 200,
                    extraSteps = 1200,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 460,
                    createdAt = nowIso(),
                    decidedAt = nowIso(),
                ),
            ),
            endedNotice = null,
            offerWindow = null,
        )

        RebalanceDebugScenario.PROGRESS_MID -> RebalanceDebugApplication(
            // planDayInfo(today): between(start=today-1, today)=1, +1 = dayX 2; ofY = lengthDays 4.
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.ACTIVE,
                    mode = RebalanceMode.BALANCED,
                    triggerDate = today.minusDays(2),
                    start = today.minusDays(1),
                    end = today.plusDays(2),
                    lengthDays = 4,
                    baseCalories = baseCalories,
                    reduction = 200,
                    extraSteps = 1200,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 460,
                    createdAt = nowIso(),
                    decidedAt = today.minusDays(1).atStartOfDay().toString(),
                ),
            ),
            endedNotice = null,
            offerWindow = null,
        )

        RebalanceDebugScenario.PROGRESS_FINAL -> RebalanceDebugApplication(
            // planDayInfo(today): between(start=today-3, today)=3, +1 = dayX 4; ofY = lengthDays 4.
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.ACTIVE,
                    mode = RebalanceMode.BALANCED,
                    triggerDate = today.minusDays(4),
                    start = today.minusDays(3),
                    end = today,
                    lengthDays = 4,
                    baseCalories = baseCalories,
                    reduction = 200,
                    extraSteps = 1200,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 600,
                    recovered = 460,
                    createdAt = nowIso(),
                    decidedAt = today.minusDays(3).atStartOfDay().toString(),
                ),
            ),
            endedNotice = null,
            offerWindow = null,
        )

        RebalanceDebugScenario.COMPLETION -> RebalanceDebugApplication(
            state = RebalanceState(),
            endedNotice = plan(
                id = newId(),
                status = RebalanceStatus.COMPLETED,
                mode = RebalanceMode.BALANCED,
                triggerDate = today.minusDays(6),
                start = today.minusDays(5),
                end = today.minusDays(1),
                lengthDays = 4,
                baseCalories = baseCalories,
                reduction = 200,
                extraSteps = 1200,
                baseStepGoal = 9000,
                recentAvgSteps = 8000,
                surplus = 600,
                recovered = 460,
                createdAt = nowIso(),
                decidedAt = today.minusDays(6).atStartOfDay().toString(),
                endedReason = "completed",
            ),
            offerWindow = null,
        )

        RebalanceDebugScenario.GRACEFUL_END -> RebalanceDebugApplication(
            state = RebalanceState(),
            endedNotice = plan(
                id = newId(),
                status = RebalanceStatus.ENDED_EARLY,
                mode = RebalanceMode.BALANCED,
                triggerDate = today.minusDays(4),
                start = today.minusDays(3),
                end = today.minusDays(1),
                lengthDays = 4,
                baseCalories = baseCalories,
                reduction = 200,
                extraSteps = 1200,
                baseStepGoal = 9000,
                recentAvgSteps = 8000,
                surplus = 600,
                recovered = 460,
                createdAt = nowIso(),
                decidedAt = today.minusDays(4).atStartOfDay().toString(),
                endedReason = "unrecoverable",
            ),
            offerWindow = null,
        )

        RebalanceDebugScenario.NO_ADJUSTMENT -> RebalanceDebugApplication(
            state = RebalanceState(
                active = plan(
                    id = newId(),
                    status = RebalanceStatus.NO_ADJUSTMENT,
                    mode = RebalanceMode.BALANCED,
                    triggerDate = today.minusDays(1),
                    start = today.minusDays(1),
                    end = today.minusDays(1),
                    lengthDays = 0,
                    baseCalories = baseCalories,
                    reduction = 0,
                    extraSteps = 0,
                    baseStepGoal = 9000,
                    recentAvgSteps = 8000,
                    surplus = 2200,
                    recovered = 0,
                    createdAt = nowIso(),
                    decidedAt = null,
                ),
            ),
            endedNotice = null,
            offerWindow = null,
        )

        RebalanceDebugScenario.CLEAR -> RebalanceDebugApplication(
            state = RebalanceState(),
            endedNotice = null,
            offerWindow = null,
        )
    }

    /**
     * Every-field [RebalancePlan] factory so each scenario reads as a flat argument list. Positional
     * date params are stringified to ISO here.
     */
    private fun plan(
        id: String,
        status: RebalanceStatus,
        mode: RebalanceMode,
        triggerDate: LocalDate,
        start: LocalDate,
        end: LocalDate,
        lengthDays: Int,
        baseCalories: Int,
        reduction: Int,
        extraSteps: Int,
        baseStepGoal: Int?,
        recentAvgSteps: Int?,
        surplus: Int,
        recovered: Int,
        createdAt: String,
        decidedAt: String?,
        endedReason: String? = null,
    ): RebalancePlan = RebalancePlan(
        id = id,
        triggerDateIso = triggerDate.toString(),
        startDateIso = start.toString(),
        endDateIso = end.toString(),
        lengthDays = lengthDays,
        mode = mode,
        baseCalories = baseCalories,
        dailyCalorieReduction = reduction,
        extraDailySteps = extraSteps,
        baseStepGoal = baseStepGoal,
        recentAvgSteps = recentAvgSteps,
        surplusKcal = surplus,
        recoveredKcal = recovered,
        status = status,
        createdAtIso = createdAt,
        decidedAtIso = decidedAt,
        endedReason = endedReason,
    )

    /**
     * A deterministic weekly-bars viz for an offer popup, matching
     * [RebalanceCoordinator.buildOfferWindow]'s shape: 7 trailing history bars (`today-7 .. today-1`)
     * followed by [lengthDays] plan bars (`today+1 ..`). Two history days are clearly over the base
     * target (the days that "drove" the offer); the rest hover near it. Plan bars carry the floored
     * effective target and are never `isOver`.
     */
    private fun syntheticOfferWindow(
        today: LocalDate,
        baseCalories: Int,
        reduction: Int,
        lengthDays: Int,
    ): List<RebalanceDayBar> {
        val history = (7 downTo 1).map { offset ->
            val d = today.minusDays(offset.toLong())
            // Two clearly-over days (offsets 3 and 2 → +450 and +600); the others sit just under the
            // target (−50..−10, deterministic) so they are never flagged over.
            val value = when (offset) {
                3 -> baseCalories + 450
                2 -> baseCalories + 600
                else -> baseCalories - 50 + (offset % 3) * 20 // −50, −30, or −10; always < base
            }
            RebalanceDayBar(
                label = dayLabel(d),
                valueKcal = value,
                targetKcal = baseCalories,
                isPlanDay = false,
                isOver = value > baseCalories,
            )
        }
        val effective = RebalancePlanMath.effectiveCalories(baseCalories, reduction)
        val planDays = (0 until lengthDays).map { i ->
            val d = today.plusDays(1L + i)
            RebalanceDayBar(
                label = dayLabel(d),
                valueKcal = effective,
                targetKcal = baseCalories,
                isPlanDay = true,
                isOver = false,
            )
        }
        return history + planDays
    }

    private fun dayLabel(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
