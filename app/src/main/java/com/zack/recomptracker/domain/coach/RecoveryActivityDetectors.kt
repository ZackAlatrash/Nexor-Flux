package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.LocalDate
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
