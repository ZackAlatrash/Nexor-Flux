package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.bucket
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.fmt
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.LocalDate
import kotlin.math.roundToInt

/** How many of the most recent RIR readings frame the deload judgement. */
private const val DELOAD_RIR_WINDOW = 6

/** Recent-half average reps-in-reserve at or below this reads as "grinding close to failure". */
private const val DELOAD_RIR_LOW_THRESHOLD = 1.5

/** The recent half must sit at least this far below the earlier half to count as "falling". */
private const val DELOAD_RIR_FALL_MARGIN = 0.5

/**
 * #17 Deload due (P1): reps-in-reserve is trending **down** (grinding closer to failure across recent
 * sessions) **and** recovery is POOR. This is the classic overreaching pattern
 * (`docs/ai-redesign/08-technical-architecture.md` §2, `04-data-utilization.md` §3 link #3): falling
 * RIR × `TrendCalculator.recoveryTrend == POOR` over sleep/energy/soreness. Recovery is graded by the
 * shared calculator exactly as [RecoveryDeclineDetector] does; every fact comes from the RIR + recovery
 * numbers. Verdict recommends a lighter/deload week to recover.
 */
class DeloadDueDetector(
    private val trend: TrendCalculator = TrendCalculator(),
) : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val rir = ctx.training.recentRir.takeLast(DELOAD_RIR_WINDOW)
        // Need enough readings to split into an earlier vs recent half.
        if (rir.size < 4) return null

        val mid = rir.size / 2
        val earlierAvg = rir.take(mid).map { it.toDouble() }.average()
        val recentAvg = rir.drop(mid).map { it.toDouble() }.average()

        // RIR must be both low (near failure) AND falling (recent half below the earlier half).
        val falling = earlierAvg - recentAvg >= DELOAD_RIR_FALL_MARGIN
        if (recentAvg > DELOAD_RIR_LOW_THRESHOLD || !falling) return null

        // Recovery gate — reuse the shared recovery grading (mirrors RecoveryDeclineDetector).
        val recoveryPoints = ctx.toRecoveryPoints()
        if (recoveryPoints.isEmpty()) return null
        if (trend.recoveryTrend(recoveryPoints) != RecoveryTrend.POOR) return null

        val sleepAvg = recoveryPoints.mapNotNull { it.sleepHours }.averageOrNull()
        val sorenessAvg = recoveryPoints.mapNotNull { it.sorenessScore }.map { it.toDouble() }.averageOrNull()

        val facts = buildMap {
            put("recentAvgRir", fmt(recentAvg, 1))
            put("earlierAvgRir", fmt(earlierAvg, 1))
            sleepAvg?.let { put("avgSleepHours", fmt(it, 1)) }
            sorenessAvg?.let { put("avgSorenessScore", fmt(it, 1)) }
        }

        // Severity from how far RIR has fallen below the "grinding" threshold; a full point past
        // (i.e. RIR ~0.5 recent) reads near-maximal.
        val severity = severityFromDistance(
            distance = (DELOAD_RIR_LOW_THRESHOLD - recentAvg).coerceAtLeast(0.0) + (earlierAvg - recentAvg),
            span = 3.0,
        )

        return CoachSignal(
            kind = SignalKind.DELOAD_DUE,
            tier = SignalTier.P1,
            category = SignalCategory.RECOVERY,
            severity = severity,
            facts = SignalFacts(facts),
            verdict = "Your reps-in-reserve is dropping (now ${fmt(recentAvg, 1)}) while recovery's poor — " +
                "take a lighter deload week.",
            action = CoachAction(CoachActionType.OPEN_TRAINING, "Plan a deload"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "training" to "RIR fell from ${fmt(earlierAvg, 1)} to ${fmt(recentAvg, 1)}",
                    "recovery" to "recovery grading POOR",
                ),
                behaviorToOutcome = "grinding reps + poor recovery → overreaching; deload to recover",
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "DELOAD_DUE|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.TODAY,
            fallbackText = "You're grinding closer to failure (reps-in-reserve down to ${fmt(recentAvg, 1)}) " +
                "and recovery's poor — a lighter deload week will let you rebound.",
        )
    }
}

/** Nightly sleep at or above this (hours) counts as a good-sleep day. */
private const val GOOD_SLEEP_HOURS = 7.0

/** Nightly sleep below this (hours) counts as a poor-sleep day. */
private const val POOR_SLEEP_HOURS = 6.0

/** Minimum days in each sleep group before the link can be called. */
private const val SLEEP_MIN_DAYS_PER_GROUP = 3

/** Minimum hunger-average gap (1–10 scale) between the groups to surface the link. */
private const val SLEEP_MIN_HUNGER_GAP = 1.5

/**
 * #18 Sleep↔hunger link (P2): the data-dense variant of the doc's sleep↔performance / sleep↔hunger
 * cross-domain link (`04-data-utilization.md` §3 link #5). Pairs nightly sleep with same-day hunger,
 * splits days into good-sleep (>= [GOOD_SLEEP_HOURS]) vs poor-sleep (< [POOR_SLEEP_HOURS]) groups, and
 * fires only when poor-sleep days run meaningfully hungrier. Mirrors the correlation SHAPE of
 * `domain/insight/CrossMetricDetector` (min-days-per-group + min-gap, helpful direction only) — framed
 * as a tendency, never a proven cause.
 */
class SleepHungerLinkDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val hungerByDate = ctx.body.hungerSeries.associate { it.date to it.value }
        if (hungerByDate.isEmpty()) return null

        val goodSleepHunger = ArrayList<Double>()
        val poorSleepHunger = ArrayList<Double>()
        for (night in ctx.body.sleepSeries) {
            val hunger = hungerByDate[night.date] ?: continue
            when {
                night.value >= GOOD_SLEEP_HOURS -> goodSleepHunger += hunger
                night.value < POOR_SLEEP_HOURS -> poorSleepHunger += hunger
                // Mid-range nights (6–7h) are ambiguous — excluded from both groups.
            }
        }
        if (goodSleepHunger.size < SLEEP_MIN_DAYS_PER_GROUP) return null
        if (poorSleepHunger.size < SLEEP_MIN_DAYS_PER_GROUP) return null

        val goodAvg = goodSleepHunger.average()
        val poorAvg = poorSleepHunger.average()
        val gap = poorAvg - goodAvg
        // Only surface the helpful direction: poor-sleep days hungrier than good-sleep days.
        if (gap < SLEEP_MIN_HUNGER_GAP) return null

        val severity = severityFromDistance(gap - SLEEP_MIN_HUNGER_GAP, span = 4.0)
        val poorStr = fmt(poorAvg, 1)
        val goodStr = fmt(goodAvg, 1)
        return CoachSignal(
            kind = SignalKind.SLEEP_HUNGER_LINK,
            tier = SignalTier.P2,
            category = SignalCategory.RECOVERY,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "poorSleepHunger" to poorStr,
                    "goodSleepHunger" to goodStr,
                    "poorSleepDays" to poorSleepHunger.size.toString(),
                    "goodSleepDays" to goodSleepHunger.size.toString(),
                ),
            ),
            verdict = "You run hungrier on low-sleep days — protecting sleep curbs cravings and helps adherence.",
            // Informational: no existing action type logs sleep, and honesty beats a mislabelled deep-link.
            action = CoachAction.None,
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "recovery" to "hunger $poorStr/10 on poor-sleep days vs $goodStr/10 on good-sleep days",
                ),
                behaviorToOutcome = "short sleep tends to run alongside higher hunger",
                confidence = Confidence.LOW,
            ),
            dedupKey = "SLEEP_HUNGER_LINK|${isoWeek(ctx.asOf)}|gap=${bucket(gap, 0.5, 1)}",
            surface = CoachSurface.TODAY,
            fallbackText = "On short-sleep nights your hunger averages $poorStr/10 versus $goodStr/10 after " +
                "good sleep — protecting sleep tends to curb the cravings.",
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
