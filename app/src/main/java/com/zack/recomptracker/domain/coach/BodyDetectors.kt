package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachDetectorSupport.bucket
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.fmt
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.signed1
import kotlin.math.abs

/**
 * Body-composition detectors (`03-proactive-ai-design.md` §A). They read the smoothed trends the
 * builder already computed with the shared TrendCalculator (`ctx.body.*TrendPerWeek`) — per §2, raw
 * daily values never reach a detector, and the trend math is never re-implemented here.
 */

/** Weekly weight band (kg/wk) that reads as "flat". Mirrors `AdjustmentThresholds` 0.20. */
private const val WEIGHT_FLAT_BAND = 0.20

/** Waist must be falling at least this fast (cm/wk) to count as genuine fat loss. */
private const val WAIST_DOWN_THRESHOLD = -0.15

/**
 * #1 Recomposition win (P0): weight flat within the maintenance band while waist is genuinely down.
 * Skinfold falling raises severity. Celebrates + "keep calories where they are."
 */
class RecompWinDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val weightTrend = ctx.body.weightTrendKgPerWeek ?: return null
        val waistTrend = ctx.body.waistTrendCmPerWeek ?: return null
        if (abs(weightTrend) > WEIGHT_FLAT_BAND) return null
        if (waistTrend > WAIST_DOWN_THRESHOLD) return null

        val skinfoldTrend = ctx.body.skinfoldTrendMmPerWeek
        val skinfoldFalling = skinfoldTrend != null && skinfoldTrend < 0.0

        // Severity scales with how fast the waist is dropping; skinfold ↓ adds a bump.
        val base = severityFromDistance(waistTrend - WAIST_DOWN_THRESHOLD, span = 0.6)
        val severity = (base + if (skinfoldFalling) 15 else 0).coerceIn(0, 100)

        val facts = buildMap {
            put("weightTrendKgPerWk", fmt(weightTrend))
            put("waistTrendCmPerWk", fmt(waistTrend))
            if (skinfoldTrend != null) put("skinfoldTrendMmPerWk", fmt(skinfoldTrend))
        }

        return CoachSignal(
            kind = SignalKind.RECOMP_WIN,
            tier = SignalTier.P0,
            category = SignalCategory.BODY,
            severity = severity,
            facts = SignalFacts(facts),
            verdict = "Textbook recomp — keep calories exactly where they are.",
            action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See the week"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "body" to "waist ${signed1(waistTrend)} cm/wk while weight held ${signed1(weightTrend)} kg/wk",
                ),
                behaviorToOutcome = "intake held → fat down, lean mass holding",
                confidence = Confidence.HIGH,
            ),
            dedupKey = "RECOMP_WIN|${isoWeek(ctx.asOf)}|waist=${bucket(waistTrend, 0.1)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Scale's flat (${signed1(weightTrend)} kg/wk) but your waist is down " +
                "${signed1(waistTrend)} cm/wk — that's fat down, muscle holding. Keep calories here.",
        )
    }
}

/** Weekly weight rise (kg/wk) above which weight is climbing. */
private const val WEIGHT_UP_THRESHOLD = 0.20

/** Weekly waist rise (cm/wk) above which waist is climbing — half of the 0.5cm/2wk engine gate. */
private const val WAIST_UP_THRESHOLD = 0.25

/**
 * #2 Fat-gain warning (P0): weight up AND waist up — the engine's `GAINING_WITH_WAIST_INCREASE`
 * verdict. Drives a −100 kcal target change.
 */
class FatGainWarningDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val weightTrend = ctx.body.weightTrendKgPerWeek ?: return null
        val waistTrend = ctx.body.waistTrendCmPerWeek ?: return null
        if (weightTrend <= WEIGHT_UP_THRESHOLD || waistTrend <= WAIST_UP_THRESHOLD) return null

        val newTarget = ctx.plan.targetCalories - 100
        val severity = severityFromDistance(waistTrend - WAIST_UP_THRESHOLD, span = 0.5)

        return CoachSignal(
            kind = SignalKind.FAT_GAIN_WARNING,
            tier = SignalTier.P0,
            category = SignalCategory.BODY,
            severity = severity,
            facts = SignalFacts(
                mapOf(
                    "weightTrendKgPerWk" to fmt(weightTrend),
                    "waistTrendCmPerWk" to fmt(waistTrend),
                    "newTargetCalories" to newTarget.toString(),
                ),
            ),
            verdict = "Weight and waist are both climbing — trim about 100 kcal to $newTarget.",
            action = CoachAction(CoachActionType.APPLY_TARGET, "Apply $newTarget kcal"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf(
                    "body" to "weight ${signed1(weightTrend)} kg/wk and waist ${signed1(waistTrend)} cm/wk both rising",
                ),
                behaviorToOutcome = "surplus → fat gain, not muscle",
                confidence = Confidence.HIGH,
            ),
            dedupKey = "FAT_GAIN_WARNING|${isoWeek(ctx.asOf)}|waist=${bucket(waistTrend, 0.1)}",
            surface = CoachSurface.WEEKLY,
            fallbackText = "Weight (${signed1(weightTrend)} kg/wk) and waist (${signed1(waistTrend)} cm/wk) " +
                "are both up — this is fat, not muscle. Trim ~100 kcal to $newTarget.",
        )
    }
}

/** How many days without a weigh-in triggers the nudge. */
private const val QUIET_WEIGH_IN_DAYS = 5

/**
 * #4 Quiet weigh-ins (P2): no weight entry in [QUIET_WEIGH_IN_DAYS]+ days — the trend engine starves.
 */
class QuietWeighInsDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        val days = ctx.body.daysSinceLastWeighIn ?: return null
        if (days < QUIET_WEIGH_IN_DAYS) return null

        val severity = severityFromDistance((days - QUIET_WEIGH_IN_DAYS).toDouble(), span = 9.0)
        return CoachSignal(
            kind = SignalKind.QUIET_WEIGH_INS,
            tier = SignalTier.P2,
            category = SignalCategory.BODY,
            severity = severity,
            facts = SignalFacts(mapOf("daysSinceLastWeighIn" to days.toString())),
            verdict = "Log one weigh-in — it's been $days days and the trend's going stale.",
            action = CoachAction(CoachActionType.LOG_WEIGHT, "Log weight"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("body" to "$days days since the last weigh-in"),
                confidence = Confidence.MEDIUM,
            ),
            dedupKey = "QUIET_WEIGH_INS|days=${CoachDetectorSupport.bucketInt(days, 2)}",
            surface = CoachSurface.TODAY,
            fallbackText = "Haven't seen the scale in $days days — one weigh-in keeps your trend honest.",
        )
    }
}
