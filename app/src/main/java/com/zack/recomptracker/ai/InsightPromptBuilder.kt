package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import kotlin.math.roundToInt

class InsightPromptBuilder {

    fun buildWeeklySummaryPrompt(context: InsightContext): String = buildString {
        appendLine("You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 2–3 sentences in plain English. Do not change the verdict.")
        appendLine("Be specific about which signals drove the decision. Keep the tone calm and direct.")
        appendLine()
        appendLine("Example output for a Hold verdict:")
        appendLine("\"Your weight has been stable over the past two weeks while your waist held steady — that's the exact signal recomposition looks like. No change to calories is needed; keep logging and let the trend confirm itself.\"")
        appendLine()
        appendLine("Verdict: ${verdictLabel(context.result.verdict)}")
        appendLine("Context: ${context.result.summary}")
        appendLine()
        appendLine("Reasons:")
        context.result.reasonCodes.forEach { code ->
            appendLine("- ${reasonDescription(code)}")
        }
        appendLine()
        appendLine("Signals this week:")
        appendLine("- Weight: ${weightLabel(context.input.weightTrendKgPerWeek)}")
        appendLine("- Waist: ${waistLabel(context.input.waistTrendCmPerWeek)}")
        appendLine("- Performance: ${performanceLabel(context.input.performanceTrend)}")
        appendLine("- Recovery: ${recoveryLabel(context.input.recoveryTrend)}")
        appendLine("- Adherence: ${adherenceLabel(context.input.adherencePercent)}")
        if (context.input.weeksSincePhaseStart != DEFAULT_WEEKS_FALLBACK) {
            appendLine("- Weeks in current phase: ${context.input.weeksSincePhaseStart}")
        }
        appendLine()
        appendLine("Calorie target: ${context.targetCalories} kcal | Protein target: ${context.targetProteinG}g")
    }

    fun buildProgressTrendPrompt(context: ProgressInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a body-recomposition coach interpreting an athlete's progress trends.")
        if (rich) {
            appendLine("Write a thorough, cross-signal interpretation (4–6 sentences) of what the combination of trends means for body recomposition. Connect the signals to each other; call out tension or agreement between weight, waist, lifts, and adherence.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English explaining what the combination of trends means for body recomposition.")
        }
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere. Interpret the trend only.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"Over the last four weeks your weight held steady while your waist trended down and your lifts kept climbing — that's recomposition, not a stall. Your logging has been consistent, so the trend is trustworthy. Stay the course and let another two weeks confirm it.\"")
        appendLine()
        appendLine("Window: last ${context.rangeDays} days")
        appendLine("Signals:")
        appendLine("- Weight: ${context.weightTrendKgPerWeek?.let { weightLabel(it) } ?: "no data"}")
        appendLine("- Waist: ${context.waistTrendCmPerWeek?.let { waistLabel(it) } ?: "no data"}")
        appendLine("- Lifts: ${liftTrendLabel(context.liftTrendKgPerWeek)}")
        appendLine("- Adherence: ${context.adherencePercent?.let { adherenceLabel(it) } ?: "no data"}")
    }

    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a training-recovery coach.")
        if (rich) {
            appendLine("Write a thorough, cross-signal readiness assessment (4–6 sentences) for the athlete today. Relate sleep, energy, hunger, and soreness to each other and to whether they trained.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English about the athlete's training readiness today.")
        }
        appendLine("Give practical training and recovery suggestions only. Do NOT give medical advice or diagnose anything.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"Two short nights with soreness running high and energy low suggests recovery hasn't caught up to your training. Prioritize sleep tonight and keep portions adequate. If soreness holds tomorrow, an easier session would help you bounce back.\"")
        appendLine()
        appendLine("Signals today:")
        context.sleepHours?.let { appendLine("- Sleep: ${sleepLabel(it)}") }
        context.energyScore?.let { appendLine("- Energy: ${scoreLabel(it)}") }
        context.hungerScore?.let { appendLine("- Hunger: ${scoreLabel(it)}") }
        context.sorenessScore?.let { appendLine("- Soreness: ${scoreLabel(it)}") }
        appendLine("- Trained today: ${if (context.trained) "yes" else "no"}")
    }

    fun buildRestOfDayPrompt(context: RestOfDayInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a nutrition coach advising an athlete on the rest of their day.")
        if (rich) {
            appendLine("Write a thorough, cross-signal plan (4–6 sentences): state where they stand, frame the remaining gap, and connect it to meal timing and protein distribution for the rest of the day.")
        } else {
            appendLine("Write exactly 2–3 sentences in plain English: state where they stand and what to prioritize for the remaining meals.")
        }
        appendLine("Do NOT invent specific foods, brands, or macro numbers beyond what is given. Frame the gap and give general guidance.")
        appendLine("Base everything only on the numbers below.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"You're at 1,420 of your 2,200-calorie target with 38 g of protein still to go. There's room for a solid dinner — make protein the centerpiece to close that gap. You're tracking well for the day.\"")
        appendLine()
        // Calories may go negative (overeating is useful signal to surface); protein
        // remaining clamps at 0 since "negative protein to go" is meaningless advice.
        val calRemaining = context.targetCalories - context.caloriesConsumed
        val proteinRemaining = (context.proteinTargetG - context.proteinConsumedG).coerceAtLeast(0.0)
        appendLine("Current intake:")
        appendLine("- Calories: ${context.caloriesConsumed} of ${context.targetCalories} kcal ($calRemaining remaining)")
        appendLine("- Protein: ${context.proteinConsumedG.roundToInt()} of ${context.proteinTargetG} g (${proteinRemaining.roundToInt()} g remaining)")
        appendLine("- Calorie zone: ${context.calorieZoneLowerBound}–${context.calorieZoneUpperBound} kcal")
        appendLine("- Meals logged so far: ${context.mealsLoggedCount}")
    }

    private fun verdictLabel(verdict: AdjustmentVerdict): String = when (verdict) {
        AdjustmentVerdict.HOLD -> "Hold — no change to calories"
        AdjustmentVerdict.INCREASE_CALORIES -> "Increase calories"
        AdjustmentVerdict.REDUCE_CALORIES -> "Reduce calories"
        AdjustmentVerdict.WAIT_FOR_DATA -> "Insufficient data — wait"
    }

    private fun reasonDescription(code: String): String = REASON_DESCRIPTIONS[code]
        ?: code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    private fun performanceLabel(trend: PerformanceTrend): String = when (trend) {
        PerformanceTrend.UP -> "improving"
        PerformanceTrend.STABLE -> "stable"
        PerformanceTrend.DOWN -> "declining"
        PerformanceTrend.UNKNOWN -> "no data"
    }

    private fun recoveryLabel(trend: RecoveryTrend): String = when (trend) {
        RecoveryTrend.GOOD -> "good"
        RecoveryTrend.OK -> "acceptable"
        RecoveryTrend.POOR -> "poor"
        RecoveryTrend.UNKNOWN -> "no data"
    }

    private fun weightLabel(trendKgPerWeek: Double): String = when {
        trendKgPerWeek > WEIGHT_THRESHOLD -> "trending up"
        trendKgPerWeek < -WEIGHT_THRESHOLD -> "trending down"
        else -> "stable"
    }

    private fun waistLabel(trendCmPerWeek: Double): String = when {
        trendCmPerWeek > WAIST_THRESHOLD -> "trending up"
        trendCmPerWeek < -WAIST_THRESHOLD -> "trending down"
        else -> "stable"
    }

    private fun adherenceLabel(percent: Double): String = when {
        percent >= 90.0 -> "high"
        percent >= 75.0 -> "moderate"
        else -> "low (below target)"
    }

    private fun liftTrendLabel(kgPerWeek: Double?): String = when {
        kgPerWeek == null -> "no data"
        kgPerWeek > LIFT_THRESHOLD -> "improving"
        kgPerWeek < -LIFT_THRESHOLD -> "declining"
        else -> "stable"
    }

    private fun sleepLabel(hours: Double): String = when {
        hours < 6.0 -> "poor"
        hours < 7.5 -> "acceptable"
        else -> "good"
    }

    private fun scoreLabel(score: Int): String = when {
        score <= 3 -> "low"
        score <= 6 -> "moderate"
        else -> "high"
    }

    private companion object {
        private const val DEFAULT_WEEKS_FALLBACK = 4
        private const val WEIGHT_THRESHOLD = 0.20
        private const val WAIST_THRESHOLD = 0.25
        private const val LIFT_THRESHOLD = 0.1

        private val REASON_DESCRIPTIONS = mapOf(
            "LOSING_WITH_POOR_RECOVERY" to "Weight is falling while performance or recovery is suffering",
            "GAINING_WITH_WAIST_INCREASE" to "Both weight and waist are trending upward",
            "EARLY_SCALE_JUMP" to "Large weight change in week one likely reflects water, not fat",
            "MAINTENANCE_TREND" to "Weight, waist, and performance are all stable",
            "LOW_ADHERENCE" to "Logging consistency is too low to draw a reliable conclusion",
            "INSUFFICIENT_DATA" to "Not enough days logged to make a reliable assessment",
            "NO_CLEAR_CHANGE_SIGNAL" to "No strong signal emerged this week",
            "WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP" to "Weight rising but waist stable and performance improving — likely lean mass",
        )
    }
}
