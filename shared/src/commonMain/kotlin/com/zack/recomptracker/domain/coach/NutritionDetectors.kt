package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.pct
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.insight.DayNutrition
import com.zack.recomptracker.domain.insight.InsightFactType
import com.zack.recomptracker.domain.insight.NutritionTargets
import com.zack.recomptracker.domain.insight.detectDerailmentDay
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** The plan's adherence floor (%). Mirrors `AdjustmentThresholds.adherenceMinimumPercent`. */
private const val ADHERENCE_MINIMUM = 80.0

/**
 * #9 Low adherence (P1): 7-day graded adherence below the plan floor. Uses the adherence value the
 * builder already computed via `AdherenceCalculator` — the detector only gates + phrases it.
 */
class LowAdherenceDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val adherence = ctx.nutrition.adherencePercent ?: return null
        if (adherence >= ADHERENCE_MINIMUM) return null

        val severity = severityFromDistance(ADHERENCE_MINIMUM - adherence, span = 40.0)
        return CoachSignal(
            kind = SignalKind.LOW_ADHERENCE,
            tier = SignalTier.P1,
            category = SignalCategory.NUTRITION,
            severity = severity,
            facts = SignalFacts(mapOf("adherencePercent" to pct(adherence))),
            verdict = "Adherence slipped to ${pct(adherence)} — one planned day gets the trend readable again.",
            action = CoachAction(CoachActionType.OPEN_FOOD_LOG, "Plan a day"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("nutrition" to "graded adherence ${pct(adherence)}, below the $ADHERENCE_MINIMUM% floor"),
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "LOW_ADHERENCE|${isoWeek(ctx.asOf)}|adh=${CoachDetectorSupport.bucketInt(adherence.roundToInt(), 5)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Your adherence is ${pct(adherence)} this week, under the $ADHERENCE_MINIMUM% mark — " +
                "one planned day keeps the numbers meaningful.",
        )
    }
}

/** Recent-window length (days) and the min logged days in it before consistency reads as "dropped". */
private const val CONSISTENCY_RECENT_DAYS = 7
private const val CONSISTENCY_RECENT_MIN_LOGGED = 4

/** The prior window must have been well-logged (a "good stretch") for the drop to be worth flagging. */
private const val CONSISTENCY_PRIOR_DAYS = 7
private const val CONSISTENCY_PRIOR_MIN_LOGGED = 6

/**
 * Consistency Check-In (P2, TODAY): logging cadence has dropped enough to degrade the adjustment
 * engine's confidence — the recent [CONSISTENCY_RECENT_DAYS] days logged fewer than
 * [CONSISTENCY_RECENT_MIN_LOGGED], *after* a prior [CONSISTENCY_PRIOR_DAYS]-day good stretch (so this
 * is a genuine slip, not a new user). Gated off the logged-day counts the context already carries in
 * `nutrition.eatenByDate`, mirroring how [QuietWeighInsDetector] watches a starving trend. Framed as a
 * DATA-QUALITY nudge — never guilt.
 */
class ConsistencyCheckInDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val today = ctx.asOf
        val logged = ctx.nutrition.eatenByDate.keys

        val recentStart = today.minus(CONSISTENCY_RECENT_DAYS - 1, DateTimeUnit.DAY)
        val priorEnd = recentStart.minus(1, DateTimeUnit.DAY)
        val priorStart = priorEnd.minus(CONSISTENCY_PRIOR_DAYS - 1, DateTimeUnit.DAY)

        val recentLogged = logged.count { it >= recentStart && it <= today }
        val priorLogged = logged.count { it >= priorStart && it <= priorEnd }

        // Recent slip AND a prior good stretch — otherwise stay silent.
        if (recentLogged >= CONSISTENCY_RECENT_MIN_LOGGED) return null
        if (priorLogged < CONSISTENCY_PRIOR_MIN_LOGGED) return null

        val needed = CONSISTENCY_RECENT_MIN_LOGGED - recentLogged
        val dayWord = if (needed == 1) "day" else "days"
        // Severity scales with how far below the recent floor the logging fell.
        val severity = severityFromDistance(
            (CONSISTENCY_RECENT_MIN_LOGGED - recentLogged).toDouble(),
            span = CONSISTENCY_RECENT_MIN_LOGGED.toDouble(),
        )
        return CoachSignal(
            kind = SignalKind.CONSISTENCY_CHECK_IN,
            tier = SignalTier.P2,
            category = SignalCategory.NUTRITION,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "recentDaysLogged" to recentLogged.toString(),
                    "recentWindowDays" to CONSISTENCY_RECENT_DAYS.toString(),
                    "priorDaysLogged" to priorLogged.toString(),
                ),
            ),
            verdict = "Logged $recentLogged of the last $CONSISTENCY_RECENT_DAYS days — $needed more $dayWord " +
                "and I can update your target with confidence.",
            action = CoachAction(CoachActionType.OPEN_FOOD_LOG, "Log today"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "nutrition" to "$recentLogged/$CONSISTENCY_RECENT_DAYS days logged recently, down from $priorLogged/$CONSISTENCY_PRIOR_DAYS",
                ),
                behaviorToOutcome = "fewer logged days → less confident target adjustments",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "CONSISTENCY_CHECK_IN|${isoWeek(today)}|logged=$recentLogged",
            surface = CoachSurface.TODAY,
            fallbackText = "You've logged $recentLogged of the last $CONSISTENCY_RECENT_DAYS days — a couple more and " +
                "your target update lands on solid data.",
        )
    }
}

/**
 * #12 Derailment day (P2): a single day drove most of the week's calorie surplus. Reroutes the
 * existing `detectDerailmentDay` `InsightFact` into a coach signal — the math is not rebuilt.
 */
class DerailmentDayDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val targets = ctx.toNutritionTargets()
        val days = ctx.toDayNutrition()
        val fact = detectDerailmentDay(days, targets) ?: return null
        if (fact.type != InsightFactType.DERAILMENT_DAY) return null

        // `PatternDetectors` encodes distance-past-threshold in `priority` (base 30). Reuse it.
        val severity = ((fact.priority - 30) * 3).coerceIn(0, 100)
        return CoachSignal(
            kind = SignalKind.DERAILMENT_DAY,
            tier = SignalTier.P2,
            category = SignalCategory.NUTRITION,
            severity = severity,
            facts = SignalFacts(mapOf("statement" to fact.statement)),
            verdict = "One day drove most of the week's surplus — plan for it and the other six hold.",
            action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See the week"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("nutrition" to fact.statement),
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "DERAILMENT_DAY|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = fact.statement + " The rest of the week was dialled in.",
        )
    }
}

/** Protein attainment gap (percentage points) between rest and training days that fires the signal. */
private const val PROTEIN_TRAINING_GAP_PP = 12.0

/** Minimum days of each kind needed to make the comparison honest. */
private const val PROTEIN_MIN_DAYS_EACH = 3

/**
 * #10 Protein miss on training days (P1): protein attainment on trained days averages materially
 * below rest days. The training-axis version of the weekday/weekend detector.
 */
class ProteinTrainingDayDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val target = ctx.plan.targetProteinG
        if (target <= 0) return null
        val trained = ctx.body.trainedDates

        val trainedDays = ctx.nutrition.eatenByDate.filterKeys { it in trained }.values
        val restDays = ctx.nutrition.eatenByDate.filterKeys { it !in trained }.values
        if (trainedDays.size < PROTEIN_MIN_DAYS_EACH || restDays.size < PROTEIN_MIN_DAYS_EACH) return null

        val trainedPct = trainedDays.map { it.proteinG }.average() / target * 100.0
        val restPct = restDays.map { it.proteinG }.average() / target * 100.0
        val gap = restPct - trainedPct
        if (gap < PROTEIN_TRAINING_GAP_PP) return null

        val severity = severityFromDistance(gap - PROTEIN_TRAINING_GAP_PP, span = 30.0)
        return CoachSignal(
            kind = SignalKind.PROTEIN_MISS_TRAINING_DAY,
            tier = SignalTier.P1,
            category = SignalCategory.NUTRITION,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "proteinPctTrainingDays" to pct(trainedPct),
                    "proteinPctRestDays" to pct(restPct),
                ),
            ),
            verdict = "Front-load protein on lifting days — you're under-fuelling the days that matter most.",
            action = CoachAction(CoachActionType.OPEN_FOOD_LOG, "Plan protein"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "nutrition" to "protein ${pct(trainedPct)} on trained days vs ${pct(restPct)} on rest days",
                ),
                behaviorToOutcome = "low protein on trained days → weaker recovery/growth stimulus",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "PROTEIN_MISS_TRAINING_DAY|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "On lifting days you're hitting ${pct(trainedPct)} protein vs ${pct(restPct)} on rest days " +
                "— fuel the days that matter most.",
        )
    }
}

/**
 * #11 Unconfirmed planned meals (P2): stale planned entries never confirmed eaten. Fires on any
 * count > 0 (spec: "≥0"), scaling severity/wording with the backlog.
 */
class UnconfirmedPlannedMealsDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val count = ctx.nutrition.unconfirmedPlannedCount
        if (count <= 0) return null

        val severity = severityFromDistance((count - 1).toDouble(), span = 5.0)
        val mealWord = if (count == 1) "meal" else "meals"
        return CoachSignal(
            kind = SignalKind.UNCONFIRMED_PLANNED_MEALS,
            tier = SignalTier.P2,
            category = SignalCategory.BEHAVIOR,
            severity = severity,
            facts = SignalFacts(mapOf("unconfirmedPlannedCount" to count.toString())),
            verdict = "Confirm or clear $count planned $mealWord so your totals are real.",
            action = CoachAction(CoachActionType.CONFIRM_PLANNED_MEALS, "Confirm meals"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("behavior" to "$count planned $mealWord never confirmed"),
                confidence = Confidence.HIGH,
            ),
            dedupKey = "UNCONFIRMED_PLANNED_MEALS|count=${CoachDetectorSupport.bucketInt(count, 2)}",
            surface = CoachSurface.TODAY,
            fallbackText = "You've got $count planned $mealWord still unconfirmed — confirm or clear them " +
                "so your totals are real.",
        )
    }
}

// ── Context → existing-detector input mappers (mapping only; math stays in the calculators) ──

private fun CoachContext.toNutritionTargets() = NutritionTargets(
    calories = plan.targetCalories,
    proteinG = plan.targetProteinG,
    carbsG = plan.targetCarbsG,
    fatG = plan.targetFatG,
    calorieZoneLower = plan.zoneLowerBound,
    calorieZoneUpper = plan.zoneUpperBound,
)

private fun CoachContext.toDayNutrition(): List<DayNutrition> =
    nutrition.eatenByDate.entries.sortedBy { it.key }.map { (date, m: MacroTotals) ->
        DayNutrition(
            date = date,
            calories = m.calories,
            proteinG = m.proteinG,
            carbsG = m.carbsG,
            fatG = m.fatG,
            logged = m.calories > 0,
        )
    }
