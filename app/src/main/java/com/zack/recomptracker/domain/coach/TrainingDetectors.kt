package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachDetectorSupport.fmt
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.streak.StreakCalculator

/** Minimum recent e1RM points on a lift before a plateau can be called. */
private const val PLATEAU_MIN_POINTS = 3

/** e1RM change (kg) between oldest and newest recent point below which the lift reads as flat. */
private const val PLATEAU_FLAT_BAND_KG = 1.0

/** e1RM must beat the prior max by at least this (kg) to be a real PR — filters rep-math noise. */
private const val PR_MARGIN_KG = 1.0

/**
 * #6 Training plateau (P1): a primary lift's e1RM series is flat or declining across the last
 * [PLATEAU_MIN_POINTS]+ points. Picks the lift with the most recent data / worst slope. e1RM values
 * are produced upstream by `WorkoutProgressAnalyzer.estimatedOneRepMax`; the detector only reads them.
 */
class TrainingPlateauDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val candidate = ctx.training.e1rmByExercise
            .mapNotNull { (name, points) ->
                val recent = points.sortedBy { it.date }.takeLast(PLATEAU_MIN_POINTS)
                if (recent.size < PLATEAU_MIN_POINTS) return@mapNotNull null
                val delta = recent.last().estimatedOneRepMax - recent.first().estimatedOneRepMax
                Triple(name, recent, delta)
            }
            // Only lifts that are flat/declining, then the worst (most negative) delta wins.
            .filter { it.third < PLATEAU_FLAT_BAND_KG }
            .minByOrNull { it.third }
            ?: return null

        val (name, recent, delta) = candidate
        val latest = recent.last().estimatedOneRepMax
        // Flat = 0 distance; declining scales severity up.
        val severity = severityFromDistance((-delta).coerceAtLeast(0.0), span = 10.0)
        return CoachSignal(
            kind = SignalKind.TRAINING_PLATEAU,
            tier = SignalTier.P1,
            category = SignalCategory.TRAINING,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "exercise" to name,
                    "e1rmKg" to fmt(latest, 1),
                    "e1rmDeltaKg" to fmt(delta, 1),
                    "sessions" to recent.size.toString(),
                ),
            ),
            verdict = "$name e1RM's flat over ${recent.size} sessions — hold volume and protect it, don't add.",
            action = CoachAction(CoachActionType.OPEN_TRAINING, "Review $name"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "training" to "$name e1RM ${fmt(delta, 1)} kg across ${recent.size} sessions",
                ),
                behaviorToOutcome = "flat strength in a deficit is normal — protect, don't push",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "TRAINING_PLATEAU|$name|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Your $name e1RM has been flat around ${fmt(latest, 1)} kg for ${recent.size} " +
                "sessions — hold volume and protect it rather than adding.",
        )
    }
}

/**
 * #5 New e1RM PR (P0, celebration): the latest e1RM on a lift beats its prior all-time max by
 * ≥[PR_MARGIN_KG]. Event-driven celebration tied to recomp (strength held/rising in a deficit).
 */
class NewPrDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val best = ctx.training.e1rmByExercise
            .mapNotNull { (name, points) ->
                val ordered = points.sortedBy { it.date }
                if (ordered.size < 2) return@mapNotNull null
                val latest = ordered.last().estimatedOneRepMax
                val priorMax = ordered.dropLast(1).maxOf { it.estimatedOneRepMax }
                val gain = latest - priorMax
                if (gain < PR_MARGIN_KG) return@mapNotNull null
                Triple(name, latest, priorMax)
            }
            .maxByOrNull { it.second - it.third }
            ?: return null

        val (name, latest, priorMax) = best
        val gain = latest - priorMax
        val severity = severityFromDistance(gain - PR_MARGIN_KG, span = 10.0)
        return CoachSignal(
            kind = SignalKind.NEW_PR,
            tier = SignalTier.P0,
            category = SignalCategory.TRAINING,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "exercise" to name,
                    "e1rmKg" to fmt(latest, 1),
                    "priorBestKg" to fmt(priorMax, 1),
                ),
            ),
            verdict = "New $name e1RM — ${fmt(latest, 1)} kg, up from ${fmt(priorMax, 1)}. Strength's holding.",
            action = CoachAction.None,
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "training" to "$name e1RM ${fmt(latest, 1)} kg beats prior best ${fmt(priorMax, 1)} kg",
                ),
                behaviorToOutcome = "strength up while dieting → the cut isn't costing you muscle",
                confidence = Confidence.HIGH,
            ),
            dedupKey = "NEW_PR|$name|e1rm=${CoachDetectorSupport.bucket(latest, 0.5, 1)}",
            surface = CoachSurface.TODAY,
            fallbackText = "New $name e1RM — ${fmt(latest, 1)} kg, up from ${fmt(priorMax, 1)}. " +
                "The cut isn't costing you strength.",
        )
    }
}

/** Steps rest tolerance (matches StreakCalculator: STEPS uses restDays = 0, strictly consecutive). */
private const val STEPS_REST_DAYS = 0

/**
 * #16 Step streak at risk (P2): a live step streak whose most recent qualifying day sits at the edge
 * of its rest tolerance and today isn't logged yet. Uses [StreakCalculator] for the streak math.
 */
class StepStreakAtRiskDetector(
    private val calculator: StreakCalculator = StreakCalculator(),
) : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val goal = ctx.profile.dailyStepGoal ?: return null
        if (goal <= 0) return null
        val today = ctx.asOf

        // Days that hit the step goal (excluding today, which is "not yet qualified").
        val qualifying = ctx.body.stepsByDate
            .filter { (date, steps) -> date.isBefore(today) && steps >= goal }
            .keys
            .toSet()
        if (qualifying.isEmpty()) return null

        val result = calculator.compute(qualifying, today, STEPS_REST_DAYS)
        // At risk only when the streak is live (current > 0) AND today isn't already qualified.
        val todayQualified = (ctx.body.stepsByDate[today] ?: 0) >= goal
        if (result.current <= 0 || todayQualified) return null

        val severity = severityFromDistance(result.current.toDouble(), span = 21.0)
        return CoachSignal(
            kind = SignalKind.STEP_STREAK_AT_RISK,
            tier = SignalTier.P2,
            category = SignalCategory.ACTIVITY,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "streakDays" to result.current.toString(),
                    "stepGoal" to goal.toString(),
                ),
            ),
            verdict = "Your ${result.current}-day step streak is live but today's short — get the steps in.",
            action = CoachAction(CoachActionType.LOG_STEPS, "Log steps"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("activity" to "${result.current}-day step streak, today under ${goal}"),
                confidence = Confidence.HIGH,
            ),
            dedupKey = "STEP_STREAK_AT_RISK|${today}",
            surface = CoachSurface.TODAY,
            fallbackText = "Your ${result.current}-day step streak is live but today's not there yet — " +
                "hit ${goal} steps to keep it.",
        )
    }
}
