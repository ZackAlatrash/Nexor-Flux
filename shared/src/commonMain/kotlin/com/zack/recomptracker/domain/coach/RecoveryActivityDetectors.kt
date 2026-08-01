package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * #14 Recovery decline (P0): the shared [TrendCalculator.recoveryTrend] over sleep/energy/soreness
 * reads POOR. This is a high-value safety signal (over-reaching / the deficit biting), so it's P0.
 */
class RecoveryDeclineDetector(
    private val trend: TrendCalculator = TrendCalculator(),
) : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val points = ctx.toRecoveryPoints()
        if (points.isEmpty()) return null
        if (trend.recoveryTrend(points) != RecoveryTrend.POOR) return null

        val sleepAvg = points.mapNotNull { it.sleepHours }.averageOrNull()
        val energyAvg = points.mapNotNull { it.energyScore }.map { it.toDouble() }.averageOrNull()
        val sorenessAvg = points.mapNotNull { it.sorenessScore }.map { it.toDouble() }.averageOrNull()

        val facts = buildMap {
            sleepAvg?.let { put("avgSleepHours", CoachDetectorSupport.fmt(it, 1)) }
            energyAvg?.let { put("avgEnergyScore", CoachDetectorSupport.fmt(it, 1)) }
            sorenessAvg?.let { put("avgSorenessScore", CoachDetectorSupport.fmt(it, 1)) }
        }

        // Severity from the worst-crossing dimension.
        val sleepMiss = sleepAvg?.let { (6.0 - it).coerceAtLeast(0.0) / 6.0 } ?: 0.0
        val energyMiss = energyAvg?.let { (5.0 - it).coerceAtLeast(0.0) / 5.0 } ?: 0.0
        val sorenessMiss = sorenessAvg?.let { (it - 7.0).coerceAtLeast(0.0) / 3.0 } ?: 0.0
        val severity = (maxOf(sleepMiss, energyMiss, sorenessMiss) * 100.0).roundToInt().coerceIn(0, 100)

        val cue = when {
            sleepAvg != null && sleepAvg < 6.0 -> "sleep short at ${CoachDetectorSupport.fmt(sleepAvg, 1)}h"
            sorenessAvg != null && sorenessAvg > 7.0 -> "soreness up at ${CoachDetectorSupport.fmt(sorenessAvg, 1)}/10"
            energyAvg != null -> "energy down at ${CoachDetectorSupport.fmt(energyAvg, 1)}/10"
            else -> "recovery markers sliding"
        }

        return CoachSignal(
            kind = SignalKind.RECOVERY_DECLINE,
            tier = SignalTier.P0,
            category = SignalCategory.RECOVERY,
            severity = severity,
            facts = SignalFacts(facts),
            verdict = "Recovery's sliding ($cue) — take a lighter day and check you're eating enough.",
            action = CoachAction(CoachActionType.OPEN_TRAINING, "Review training"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("recovery" to cue),
                behaviorToOutcome = "accumulated fatigue / deficit → recovery markers down",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "RECOVERY_DECLINE|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Your recovery's trending poor — $cue. Consider a lighter day or a diet-break day.",
        )
    }
}

/** Minimum recovery inputs logged for *today* before Morning Readiness will speak. */
private const val READINESS_MIN_INPUTS = 2

/** A today-vs-baseline gap counts as "meaningful" past these per-metric deltas (metric's own units). */
private const val READINESS_SLEEP_GAP_H = 0.75      // hours below the user's usual sleep
private const val READINESS_ENERGY_GAP = 1.0        // energy points below usual (1–10, higher = better)
private const val READINESS_SORENESS_GAP = 1.0      // soreness points above usual (1–10, higher = worse)
private const val READINESS_HUNGER_GAP = 1.5        // hunger points above usual (1–10, higher = worse)

/**
 * Morning Readiness (P1, TODAY): the proactive daily "train as planned vs go lighter" call. Fires
 * when at least [READINESS_MIN_INPUTS] of today's recovery inputs (sleep/energy/hunger/soreness) are
 * logged, and grades each against the *user's own baseline* — the window average of that same metric
 * (excluding today), which the [CoachContext] already carries as each series. No new baseline is
 * invented; the decisive driver (e.g. sleep short vs usual, soreness up) is named in the rationale.
 *
 * Deterministic verdict is authoritative — the slot's LLM only rephrases it. Coexists with the Body
 * screen's RECOVERY_READINESS LLM card (that card is untouched; this is the proactive slot version).
 */
class MorningReadinessDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val today = ctx.asOf

        // Each dimension: (today's value, the user's own baseline = window avg excluding today).
        val sleep = todayAndBaseline(ctx.body.sleepSeries, today)
        val energy = todayAndBaseline(ctx.body.energySeries, today)
        val soreness = todayAndBaseline(ctx.body.sorenessSeries, today)
        val hunger = todayAndBaseline(ctx.body.hungerSeries, today)

        val loggedToday = listOf(sleep, energy, soreness, hunger).count { it != null }
        if (loggedToday < READINESS_MIN_INPUTS) return null

        // Score "worse-than-usual" dimensions; track the single decisive (largest) miss for the driver.
        var lowSignals = 0
        var worstMiss = 0.0
        var driver: String? = null
        val facts = LinkedHashMap<String, String>()

        sleep?.let { (t, base) ->
            facts["sleepHoursToday"] = CoachDetectorSupport.fmt(t, 1)
            facts["sleepHoursUsual"] = CoachDetectorSupport.fmt(base, 1)
            val miss = base - t // positive = slept less than usual
            if (miss >= READINESS_SLEEP_GAP_H) {
                lowSignals++
                val norm = miss / READINESS_SLEEP_GAP_H
                if (norm > worstMiss) { worstMiss = norm; driver = "sleep ${CoachDetectorSupport.fmt(t, 1)}h vs usual ${CoachDetectorSupport.fmt(base, 1)}h" }
            }
        }
        energy?.let { (t, base) ->
            facts["energyToday"] = CoachDetectorSupport.fmt(t, 1)
            facts["energyUsual"] = CoachDetectorSupport.fmt(base, 1)
            val miss = base - t // positive = lower energy than usual
            if (miss >= READINESS_ENERGY_GAP) {
                lowSignals++
                val norm = miss / READINESS_ENERGY_GAP
                if (norm > worstMiss) { worstMiss = norm; driver = "energy ${CoachDetectorSupport.fmt(t, 1)}/10 vs usual ${CoachDetectorSupport.fmt(base, 1)}/10" }
            }
        }
        soreness?.let { (t, base) ->
            facts["sorenessToday"] = CoachDetectorSupport.fmt(t, 1)
            facts["sorenessUsual"] = CoachDetectorSupport.fmt(base, 1)
            val miss = t - base // positive = more sore than usual
            if (miss >= READINESS_SORENESS_GAP) {
                lowSignals++
                val norm = miss / READINESS_SORENESS_GAP
                if (norm > worstMiss) { worstMiss = norm; driver = "soreness ${CoachDetectorSupport.fmt(t, 1)}/10 vs usual ${CoachDetectorSupport.fmt(base, 1)}/10" }
            }
        }
        hunger?.let { (t, base) ->
            facts["hungerToday"] = CoachDetectorSupport.fmt(t, 1)
            facts["hungerUsual"] = CoachDetectorSupport.fmt(base, 1)
            val miss = t - base // positive = hungrier than usual
            if (miss >= READINESS_HUNGER_GAP) {
                lowSignals++
                val norm = miss / READINESS_HUNGER_GAP
                if (norm > worstMiss) { worstMiss = norm; driver = "hunger ${CoachDetectorSupport.fmt(t, 1)}/10 vs usual ${CoachDetectorSupport.fmt(base, 1)}/10" }
            }
        }

        val lowReadiness = lowSignals >= 1 && driver != null
        facts["inputsLogged"] = loggedToday.toString()

        return if (lowReadiness) {
            val cue = driver!!
            // Severity scales with how far the decisive driver sits past its "meaningful" gap.
            val severity = severityFromDistance(worstMiss - 1.0, span = 2.0)
            CoachSignal(
                kind = SignalKind.MORNING_READINESS,
                tier = SignalTier.P1,
                category = SignalCategory.RECOVERY,
                severity = severity,
                facts = SignalFacts(facts),
                verdict = "Recovery's low ($cue) — keep today's session light.",
                action = CoachAction(CoachActionType.OPEN_TRAINING, "Adjust today"),
                rationale = SignalRationale(
                    primaryCauseByDomain = mapOf("recovery" to cue),
                    behaviorToOutcome = "readiness down vs your usual → train lighter to recover",
                    confidence = Confidence.MEDIUM,
                ),
                dedupKey = "MORNING_READINESS|$today|low",
                surface = CoachSurface.TODAY,
                fallbackText = "This morning's readiness is down — $cue. Keep today's session light and let recovery catch up.",
            )
        } else {
            // Good readiness: today's inputs are at or above the user's own baseline. This is
            // reassurance, not a decision, so it sits at P2 (maintenance) — a genuinely actionable
            // P2 (a scale check, a consistency dip) should win the single daily slot over a daily
            // "you're fine", and this must never out-rank them just because it fires every day.
            val severity = 20 // low urgency: reassurance, not a warning
            CoachSignal(
                kind = SignalKind.MORNING_READINESS,
                tier = SignalTier.P2,
                category = SignalCategory.RECOVERY,
                severity = severity,
                facts = SignalFacts(facts),
                verdict = "You're recovered — train as planned.",
                action = CoachAction(CoachActionType.OPEN_TRAINING, "Open training"),
                rationale = SignalRationale(
                    primaryCauseByDomain = mapOf("recovery" to "today's recovery inputs are in line with your usual"),
                    behaviorToOutcome = "readiness on par with baseline → session as planned",
                    confidence = Confidence.MEDIUM,
                ),
                dedupKey = "MORNING_READINESS|$today|good",
                surface = CoachSurface.TODAY,
                fallbackText = "Your recovery markers this morning are in line with your usual — you're good to train as planned.",
            )
        }
    }

    /**
     * Today's value for [series] paired with the user's own baseline = the mean of the other days in
     * the window. Returns null when today isn't logged or there's no prior day to form a baseline.
     */
    private fun todayAndBaseline(series: List<MetricPoint>, today: LocalDate): Pair<Double, Double>? {
        val todayValue = series.lastOrNull { it.date == today }?.value ?: return null
        val prior = series.filter { it.date != today }
        if (prior.isEmpty()) return null
        return todayValue to prior.map { it.value }.average()
    }
}

/** Fraction the 7-day step average must drop vs the prior 7 days to count as a collapse. */
private const val NEAT_DROP_FRACTION = 0.25

/** Weight-trend band (kg/wk) that reads as "stalled". */
private const val NEAT_STALL_BAND = 0.15

/**
 * #15 NEAT collapse (P1): 7-day step average dropped ≥25% vs the prior 7-day average **and** the
 * weight trend is stalled — steps, not calories, explain the stall. Uses the builder's smoothed
 * `avgSteps7`/`avgStepsPrev7` and `weightTrendKgPerWeek`.
 */
class NeatCollapseDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val cur = ctx.body.avgSteps7 ?: return null
        val prev = ctx.body.avgStepsPrev7 ?: return null
        val weightTrend = ctx.body.weightTrendKgPerWeek ?: return null
        if (prev <= 0) return null

        val dropFraction = (prev - cur).toDouble() / prev.toDouble()
        if (dropFraction < NEAT_DROP_FRACTION) return null
        if (abs(weightTrend) > NEAT_STALL_BAND) return null

        val dropSteps = prev - cur
        val severity = severityFromDistance(dropFraction - NEAT_DROP_FRACTION, span = 0.5)
        return CoachSignal(
            kind = SignalKind.NEAT_COLLAPSE,
            tier = SignalTier.P1,
            category = SignalCategory.ACTIVITY,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "avgSteps7" to cur.toString(),
                    "avgStepsPrev7" to prev.toString(),
                    "stepDropPercent" to CoachDetectorSupport.pct(dropFraction * 100.0),
                    "weightTrendKgPerWk" to CoachDetectorSupport.fmt(weightTrend),
                ),
            ),
            verdict = "Nudge your step goal back up before cutting food — it's movement, not intake.",
            action = CoachAction(CoachActionType.LOG_STEPS, "Check steps"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "activity" to "steps down ${dropSteps}/day (${CoachDetectorSupport.pct(dropFraction * 100.0)})",
                    "body" to "weight stalled at ${CoachDetectorSupport.signed1(weightTrend)} kg/wk",
                ),
                behaviorToOutcome = "steps down → burn down → scale stalls",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "NEAT_COLLAPSE|${isoWeek(ctx.asOf)}|drop=${CoachDetectorSupport.bucketInt((dropFraction * 100).roundToInt(), 5)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Steps are down about $dropSteps/day and the scale's stalled — it's movement, " +
                "not your intake. Nudge the step goal before cutting food.",
        )
    }
}

// ── mappers ──

private fun CoachContext.toRecoveryPoints(): List<RecoveryPoint> {
    val dates: Set<LocalDate> =
        (body.sleepSeries.map { it.date } + body.energySeries.map { it.date } + body.sorenessSeries.map { it.date }).toSet()
    val sleepByDate = body.sleepSeries.associate { it.date to it.value }
    val energyByDate = body.energySeries.associate { it.date to it.value }
    val sorenessByDate = body.sorenessSeries.associate { it.date to it.value }
    return dates.sorted().map { d ->
        RecoveryPoint(
            date = d,
            sleepHours = sleepByDate[d],
            energyScore = energyByDate[d]?.roundToInt(),
            sorenessScore = sorenessByDate[d]?.roundToInt(),
        )
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
