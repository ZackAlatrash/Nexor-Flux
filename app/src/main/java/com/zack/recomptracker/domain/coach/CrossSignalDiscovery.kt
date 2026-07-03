package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachDetectorSupport.fmt
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.isoWeek
import com.zack.recomptracker.domain.coach.CoachDetectorSupport.severityFromDistance
import java.time.DayOfWeek
import kotlin.math.abs

/**
 * Phase 5 — Cross-Signal Discovery. The ONE genuine-LLM card: the deterministic engine evaluates a
 * small, fixed set of candidate cross-domain correlations from EXISTING context series (no new data
 * collection), picks the single strongest one that clears a minimum effect threshold, and emits ONE
 * signal carrying a concrete, number-safe, testable hypothesis. The cloud LLM (via
 * `CoachPhrasingService`) only PHRASES that verdict — it never invents a figure (invariant 1).
 *
 * Each candidate splits the window into two groups on a cross-domain condition (e.g. protein-hit vs
 * protein-miss days), computes a deterministic mean-vs-mean delta on an outcome metric, and reports a
 * signed effect size. The winner is the candidate with the largest |effect| that clears its own
 * threshold. Silence is valid: no candidate clears → no signal.
 *
 * Pure Kotlin; every number is engine-computed. See `docs/ai-redesign` Phase 5 (WI-D).
 */

/** Minimum logged days in the window before any correlation may be called (mirrors the engine gate). */
internal const val DISCOVERY_MIN_LOGGED_DAYS = 14

/** Minimum days in EACH group before a candidate correlation can be evaluated. */
internal const val DISCOVERY_MIN_DAYS_PER_GROUP = 3

/**
 * One candidate correlation's deterministic result: the two group means, the signed effect (delta),
 * and the human-facing hypothesis text. Everything here is computed from the context series only.
 */
internal data class CorrelationResult(
    /** Stable identity for the correlation (drives the experiment record + a stable dedupKey). */
    val correlationId: String,
    val effect: Double,
    val groupAMean: Double,
    val groupBMean: Double,
    val groupADays: Int,
    val groupBDays: Int,
    /** The single metric an experiment on this correlation would track, e.g. "hunger". */
    val trackedMetric: String,
    /** The tracked metric's CURRENT window value — the baseline a later evaluation compares against. */
    val baselineValue: Double,
    /** A concrete, testable hypothesis naming the two metrics + observed direction (number-safe). */
    val verdict: String,
    val fallbackText: String,
    val facts: Map<String, String>,
    val primaryCauseByDomain: Map<String, String>,
    val behaviorToOutcome: String,
    /** The minimum |effect| this candidate must clear to be eligible. */
    val threshold: Double,
) {
    val clears: Boolean get() = abs(effect) >= threshold
}

/**
 * The fixed candidate set, each computed deterministically from existing series. Returns every
 * candidate that has enough data AND clears its threshold, strongest (largest |effect|) first.
 */
internal object CrossSignalCorrelations {

    /** Nightly sleep at or above this (hours) is a good-sleep night. */
    private const val GOOD_SLEEP_HOURS = 7.0

    /** Nightly sleep below this (hours) is a poor-sleep night. */
    private const val POOR_SLEEP_HOURS = 6.0

    /** Minimum next-day energy gap (1–10) between good/poor sleep to surface the sleep↔energy link. */
    private const val SLEEP_ENERGY_MIN_GAP = 1.0

    /** Minimum next-day hunger gap (1–10) between protein-hit/miss days to surface the protein link. */
    private const val PROTEIN_HUNGER_MIN_GAP = 1.0

    /** A day counts as a protein-hit day when eaten protein is within this fraction of the target. */
    private const val PROTEIN_HIT_FRACTION = 0.9

    /** Minimum same-day energy gap (1–10) between high/low carb days to surface the carb↔energy link. */
    private const val CARB_ENERGY_MIN_GAP = 1.0

    /** Minimum weekend-vs-weekday calorie gap (kcal) to surface the weekend↔surplus link. */
    private const val WEEKEND_SURPLUS_MIN_GAP = 150.0

    /** Evaluate every candidate; return the ones that clear, strongest |effect| first. */
    fun evaluate(ctx: CoachContext): List<CorrelationResult> =
        listOfNotNull(
            proteinHitNextDayHunger(ctx),
            sleepNextDayEnergy(ctx),
            highCarbSameDayEnergy(ctx),
            weekendCalorieSurplus(ctx),
        ).filter { it.clears }
            .sortedByDescending { abs(it.effect) }

    // ── Candidate 1: protein-hit days ↔ NEXT-day hunger ──────────────────────────
    private fun proteinHitNextDayHunger(ctx: CoachContext): CorrelationResult? {
        val target = ctx.plan.targetProteinG
        if (target <= 0) return null
        val hungerByDate = ctx.body.hungerSeries.associate { it.date to it.value }
        if (hungerByDate.isEmpty()) return null

        val hitNextHunger = ArrayList<Double>()
        val missNextHunger = ArrayList<Double>()
        for ((date, macros) in ctx.nutrition.eatenByDate) {
            val nextHunger = hungerByDate[date.plusDays(1)] ?: continue
            if (macros.proteinG >= target * PROTEIN_HIT_FRACTION) hitNextHunger += nextHunger
            else missNextHunger += nextHunger
        }
        if (hitNextHunger.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null
        if (missNextHunger.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null

        val hitAvg = hitNextHunger.average()
        val missAvg = missNextHunger.average()
        // Effect = how much LOWER hunger runs the day after hitting protein (helpful direction positive).
        val effect = missAvg - hitAvg
        val hitStr = fmt(hitAvg, 1)
        val missStr = fmt(missAvg, 1)
        return CorrelationResult(
            correlationId = "PROTEIN_HIT_NEXT_DAY_HUNGER",
            effect = effect,
            groupAMean = hitAvg,
            groupBMean = missAvg,
            groupADays = hitNextHunger.size,
            groupBDays = missNextHunger.size,
            trackedMetric = "hunger",
            baselineValue = ctx.body.hungerSeries.map { it.value }.average(),
            verdict = "On days after you hit your protein target your hunger averages $hitStr/10 versus " +
                "$missStr/10 after missing it — hitting protein may curb next-day cravings.",
            fallbackText = "After protein-target days your next-day hunger averages $hitStr/10 versus " +
                "$missStr/10 otherwise — worth testing whether hitting protein tames the cravings.",
            facts = mapOf(
                "hitDayNextHunger" to hitStr,
                "missDayNextHunger" to missStr,
                "hitDays" to hitNextHunger.size.toString(),
                "missDays" to missNextHunger.size.toString(),
            ),
            primaryCauseByDomain = mapOf(
                "nutrition" to "hunger $hitStr/10 after protein-hit days vs $missStr/10 after misses",
            ),
            behaviorToOutcome = "hitting protein tends to run alongside lower next-day hunger",
            threshold = PROTEIN_HUNGER_MIN_GAP,
        )
    }

    // ── Candidate 2: sleep ↔ NEXT-day energy ─────────────────────────────────────
    private fun sleepNextDayEnergy(ctx: CoachContext): CorrelationResult? {
        val energyByDate = ctx.body.energySeries.associate { it.date to it.value }
        if (energyByDate.isEmpty()) return null

        val goodSleepEnergy = ArrayList<Double>()
        val poorSleepEnergy = ArrayList<Double>()
        for (night in ctx.body.sleepSeries) {
            val nextEnergy = energyByDate[night.date.plusDays(1)] ?: continue
            when {
                night.value >= GOOD_SLEEP_HOURS -> goodSleepEnergy += nextEnergy
                night.value < POOR_SLEEP_HOURS -> poorSleepEnergy += nextEnergy
            }
        }
        if (goodSleepEnergy.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null
        if (poorSleepEnergy.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null

        val goodAvg = goodSleepEnergy.average()
        val poorAvg = poorSleepEnergy.average()
        // Effect = how much HIGHER next-day energy runs after good sleep (helpful direction positive).
        val effect = goodAvg - poorAvg
        val goodStr = fmt(goodAvg, 1)
        val poorStr = fmt(poorAvg, 1)
        return CorrelationResult(
            correlationId = "SLEEP_NEXT_DAY_ENERGY",
            effect = effect,
            groupAMean = goodAvg,
            groupBMean = poorAvg,
            groupADays = goodSleepEnergy.size,
            groupBDays = poorSleepEnergy.size,
            trackedMetric = "energy",
            baselineValue = ctx.body.energySeries.map { it.value }.average(),
            verdict = "After good sleep (7h+) your next-day energy averages $goodStr/10 versus $poorStr/10 " +
                "after short nights — protecting sleep may lift your energy.",
            fallbackText = "Your energy runs $goodStr/10 the day after good sleep versus $poorStr/10 after " +
                "short nights — worth testing whether guarding sleep raises your energy.",
            facts = mapOf(
                "goodSleepNextEnergy" to goodStr,
                "poorSleepNextEnergy" to poorStr,
                "goodSleepDays" to goodSleepEnergy.size.toString(),
                "poorSleepDays" to poorSleepEnergy.size.toString(),
            ),
            primaryCauseByDomain = mapOf(
                "recovery" to "energy $goodStr/10 after good sleep vs $poorStr/10 after short nights",
            ),
            behaviorToOutcome = "longer sleep tends to run alongside higher next-day energy",
            threshold = SLEEP_ENERGY_MIN_GAP,
        )
    }

    // ── Candidate 3: higher-carb days ↔ same-day energy ──────────────────────────
    private fun highCarbSameDayEnergy(ctx: CoachContext): CorrelationResult? {
        val energyByDate = ctx.body.energySeries.associate { it.date to it.value }
        if (energyByDate.isEmpty()) return null

        // Median carb split so groups are balanced without inventing an absolute threshold.
        val carbDays = ctx.nutrition.eatenByDate
            .filterKeys { energyByDate.containsKey(it) }
            .mapValues { it.value.carbsG }
        if (carbDays.size < DISCOVERY_MIN_DAYS_PER_GROUP * 2) return null
        val median = carbDays.values.sorted().let { s ->
            if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        }

        val highCarbEnergy = ArrayList<Double>()
        val lowCarbEnergy = ArrayList<Double>()
        for ((date, carbs) in carbDays) {
            val energy = energyByDate[date] ?: continue
            if (carbs >= median) highCarbEnergy += energy else lowCarbEnergy += energy
        }
        if (highCarbEnergy.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null
        if (lowCarbEnergy.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null

        val highAvg = highCarbEnergy.average()
        val lowAvg = lowCarbEnergy.average()
        // Effect = how much HIGHER energy runs on higher-carb days (helpful direction positive).
        val effect = highAvg - lowAvg
        val highStr = fmt(highAvg, 1)
        val lowStr = fmt(lowAvg, 1)
        return CorrelationResult(
            correlationId = "HIGH_CARB_SAME_DAY_ENERGY",
            effect = effect,
            groupAMean = highAvg,
            groupBMean = lowAvg,
            groupADays = highCarbEnergy.size,
            groupBDays = lowCarbEnergy.size,
            trackedMetric = "energy",
            baselineValue = ctx.body.energySeries.map { it.value }.average(),
            verdict = "On your higher-carb days your energy averages $highStr/10 versus $lowStr/10 on " +
                "lower-carb days — carbs may be fuelling your energy.",
            fallbackText = "Your energy averages $highStr/10 on higher-carb days versus $lowStr/10 on " +
                "lower-carb days — worth testing whether more carbs lift your energy.",
            facts = mapOf(
                "highCarbEnergy" to highStr,
                "lowCarbEnergy" to lowStr,
                "highCarbDays" to highCarbEnergy.size.toString(),
                "lowCarbDays" to lowCarbEnergy.size.toString(),
            ),
            primaryCauseByDomain = mapOf(
                "nutrition" to "energy $highStr/10 on higher-carb days vs $lowStr/10 on lower-carb days",
            ),
            behaviorToOutcome = "higher carb intake tends to run alongside higher same-day energy",
            threshold = CARB_ENERGY_MIN_GAP,
        )
    }

    // ── Candidate 4: weekend ↔ calorie surplus ───────────────────────────────────
    private fun weekendCalorieSurplus(ctx: CoachContext): CorrelationResult? {
        val weekendCals = ArrayList<Double>()
        val weekdayCals = ArrayList<Double>()
        for ((date, macros) in ctx.nutrition.eatenByDate) {
            val cals = macros.calories.toDouble()
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                weekendCals += cals
            } else {
                weekdayCals += cals
            }
        }
        if (weekendCals.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null
        if (weekdayCals.size < DISCOVERY_MIN_DAYS_PER_GROUP) return null

        val weekendAvg = weekendCals.average()
        val weekdayAvg = weekdayCals.average()
        // Effect = how many MORE kcal weekends run than weekdays (surplus direction positive).
        val effect = weekendAvg - weekdayAvg
        val weekendStr = fmt(weekendAvg, 0)
        val weekdayStr = fmt(weekdayAvg, 0)
        val deltaStr = fmt(abs(effect), 0)
        return CorrelationResult(
            correlationId = "WEEKEND_CALORIE_SURPLUS",
            effect = effect,
            groupAMean = weekendAvg,
            groupBMean = weekdayAvg,
            groupADays = weekendCals.size,
            groupBDays = weekdayCals.size,
            trackedMetric = "weekend_calories",
            baselineValue = weekendAvg,
            verdict = "Your weekend days average $weekendStr kcal versus $weekdayStr on weekdays " +
                "(+$deltaStr) — weekends may be where the week's surplus builds.",
            fallbackText = "Weekends average $weekendStr kcal versus $weekdayStr on weekdays " +
                "(+$deltaStr kcal) — worth testing whether reining in weekends steadies the week.",
            facts = mapOf(
                "weekendCalories" to weekendStr,
                "weekdayCalories" to weekdayStr,
                "deltaCalories" to deltaStr,
            ),
            primaryCauseByDomain = mapOf(
                "nutrition" to "$weekendStr kcal on weekends vs $weekdayStr on weekdays",
            ),
            behaviorToOutcome = "weekend eating tends to run alongside the week's calorie surplus",
            threshold = WEEKEND_SURPLUS_MIN_GAP,
        )
    }
}

/**
 * #19 Cross-Signal Discovery (P1, Phase 5): the flagship genuine-LLM card. Evaluates
 * [CrossSignalCorrelations] deterministically, picks the strongest candidate that clears its
 * threshold, and emits ONE [CoachSignal] carrying a testable hypothesis + a [CoachActionType.TRACK_EXPERIMENT]
 * "Track this" action. Given a HIGH severity so, the week it fires, it wins the P1 slot (Q9). Gated to
 * fire at most once per ~7 days via a stable weekly [CoachSignal.dedupKey] + the pipeline's 7-day
 * cooldown. Silent when no candidate clears the threshold or the window has too few logged days.
 *
 * Invariant 1: every number in the verdict/facts is engine-computed here; the LLM only phrases.
 */
class CrossSignalDiscoveryDetector : CoachDetector {
    override fun detect(ctx: CoachContext): CoachSignal? {
        if (ctx.nutrition.loggedDaysInWindow < DISCOVERY_MIN_LOGGED_DAYS) return null

        val winner = CrossSignalCorrelations.evaluate(ctx).firstOrNull() ?: return null

        // HIGH severity so the discovery wins the P1 slot the week it fires, scaled by how far the
        // effect clears its threshold (a fully-clear effect reads maximal).
        val severity = (DISCOVERY_SEVERITY_FLOOR +
            severityFromDistance(abs(winner.effect) - winner.threshold, span = winner.threshold * 4.0))
            .coerceIn(0, 100)

        return CoachSignal(
            kind = SignalKind.CROSS_SIGNAL_DISCOVERY,
            tier = SignalTier.P1,
            category = SignalCategory.BEHAVIOR,
            severity = severity,
            // Carry the experiment seed as facts so the slot can map signal → experiment without the
            // context: the correlation id, the metric to track, and its current window value (baseline).
            facts = SignalFacts(
                winner.facts + mapOf(
                    "correlationId" to winner.correlationId,
                    "trackedMetric" to winner.trackedMetric,
                    "baselineValue" to fmt(winner.baselineValue, 2),
                ),
            ),
            verdict = winner.verdict,
            action = CoachAction(CoachActionType.TRACK_EXPERIMENT, "Track this"),
            rationale = SignalRationale(
                primaryCauseByDomain = winner.primaryCauseByDomain,
                behaviorToOutcome = winner.behaviorToOutcome,
                confidence = Confidence.LOW,
            ),
            // Stable per-correlation identity + ISO week: fires at most once per correlation per week,
            // and the 7-day cooldown then holds it off for the following week.
            dedupKey = "CROSS_SIGNAL_DISCOVERY|${winner.correlationId}|${isoWeek(ctx.asOf)}",
            surface = CoachSurface.TODAY,
            fallbackText = winner.fallbackText,
        )
    }

    private companion object {
        /** The severity floor that keeps a firing discovery above ordinary P1 signals (Q9). */
        const val DISCOVERY_SEVERITY_FLOOR = 60
    }
}

/**
 * The active experiment's identity + baseline, passed to [ExperimentEvaluation] from the store. Kept
 * as a pure `domain/coach` value so the evaluation stays Android-free and unit-testable — the
 * `data/coach` `CoachExperiment` maps onto it 1:1.
 */
data class ActiveExperiment(
    val correlationId: String,
    val hypothesis: String,
    val trackedMetric: String,
    val baselineValue: Double,
    val startDateIso: String,
)

/** Whether the tracked metric moved in the hoped direction, given the correlation being tested. */
enum class ExperimentDirection { IMPROVED, NO_CHANGE, WORSENED }

/**
 * Phase 5F — minimal-but-real experiment evaluation. Given the single [ActiveExperiment] and the
 * current [CoachContext], it recomputes the tracked metric's current window value, compares it to the
 * stored baseline, decides whether it moved in the hoped direction, and emits a deterministic
 * [SignalKind.EXPERIMENT_RESULT] TODAY signal. The caller (digest coordinator) only surfaces this once
 * the experiment is ≥[MIN_EXPERIMENT_DAYS] days old, then clears the experiment.
 *
 * Deterministic verdict + fallback; every number engine-computed. Silent (`null`) when the tracked
 * metric has no current value to compare.
 */
object ExperimentEvaluation {

    /** An experiment runs at least this long before its result is evaluated (Q9 "week's up"). */
    const val MIN_EXPERIMENT_DAYS = 7

    /** Minimum absolute move (metric units) before we call the metric "changed" rather than flat. */
    private const val MOVE_EPSILON = 0.3

    /**
     * The direction that counts as "improved" per correlation. For every candidate the hoped direction
     * is the same metric moving to a *better* value: lower hunger, higher energy, fewer weekend calories.
     */
    private fun hopedLowerIsBetter(correlationId: String): Boolean = when (correlationId) {
        "PROTEIN_HIT_NEXT_DAY_HUNGER" -> true       // less hunger is better
        "SLEEP_NEXT_DAY_ENERGY" -> false            // more energy is better
        "HIGH_CARB_SAME_DAY_ENERGY" -> false        // more energy is better
        "WEEKEND_CALORIE_SURPLUS" -> true           // fewer weekend calories is better
        else -> false
    }

    /** Current window value of the tracked metric, or `null` when it can't be computed. */
    fun currentValue(experiment: ActiveExperiment, ctx: CoachContext): Double? = when (experiment.trackedMetric) {
        "hunger" -> ctx.body.hungerSeries.map { it.value }.averageOrNull()
        "energy" -> ctx.body.energySeries.map { it.value }.averageOrNull()
        "weekend_calories" -> ctx.nutrition.eatenByDate
            .filterKeys { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            .values.map { it.calories.toDouble() }.averageOrNull()
        else -> null
    }

    /** Classify the move from [baseline] to [current] given whether lower is the hoped direction. */
    fun classify(correlationId: String, baseline: Double, current: Double): ExperimentDirection {
        val delta = current - baseline
        if (abs(delta) < MOVE_EPSILON) return ExperimentDirection.NO_CHANGE
        val improved = if (hopedLowerIsBetter(correlationId)) delta < 0 else delta > 0
        return if (improved) ExperimentDirection.IMPROVED else ExperimentDirection.WORSENED
    }

    /**
     * The [SignalKind.EXPERIMENT_RESULT] follow-up signal, or `null` when the tracked metric has no
     * current value. The caller decides *when* to call this (experiment ≥ [MIN_EXPERIMENT_DAYS] old)
     * and clears the experiment afterwards.
     */
    fun evaluate(experiment: ActiveExperiment, ctx: CoachContext): CoachSignal? {
        val current = currentValue(experiment, ctx) ?: return null
        val direction = classify(experiment.correlationId, experiment.baselineValue, current)
        val decimals = if (experiment.trackedMetric == "weekend_calories") 0 else 1
        val baseStr = fmt(experiment.baselineValue, decimals)
        val nowStr = fmt(current, decimals)
        val unit = if (experiment.trackedMetric == "weekend_calories") " kcal" else "/10"
        val metricLabel = when (experiment.trackedMetric) {
            "hunger" -> "hunger"
            "energy" -> "energy"
            "weekend_calories" -> "weekend calories"
            else -> experiment.trackedMetric
        }
        val movedPhrase = when (direction) {
            ExperimentDirection.IMPROVED -> "moved the right way"
            ExperimentDirection.WORSENED -> "moved the wrong way"
            ExperimentDirection.NO_CHANGE -> "held about steady"
        }
        val verdict = "Your experiment's week is up: $metricLabel went from $baseStr$unit to $nowStr$unit — " +
            "it $movedPhrase."
        return CoachSignal(
            kind = SignalKind.EXPERIMENT_RESULT,
            tier = SignalTier.P1,
            category = SignalCategory.BEHAVIOR,
            severity = 65,
            facts = SignalFacts(
                mapOf(
                    "trackedMetric" to metricLabel,
                    "baselineValue" to baseStr,
                    "currentValue" to nowStr,
                    "direction" to direction.name,
                    "correlationId" to experiment.correlationId,
                ),
            ),
            verdict = verdict,
            action = CoachAction.None,
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("behavior" to "$metricLabel $baseStr$unit → $nowStr$unit"),
                behaviorToOutcome = "you tracked \"${experiment.hypothesis.trimEnd('.')}\"; here's what your data did",
                confidence = Confidence.LOW,
            ),
            dedupKey = "EXPERIMENT_RESULT|${experiment.correlationId}|${experiment.startDateIso}",
            surface = CoachSurface.TODAY,
            fallbackText = verdict,
        )
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
