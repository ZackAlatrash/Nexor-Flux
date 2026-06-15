package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.pow
import java.util.Locale

class InsightPromptBuilder {

    fun buildWeeklySummaryPrompt(context: InsightContext): String = buildString {
        appendLine("You are a concise body-recomposition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write 1–2 short sentences. Structure: observation (the most decisive number, compared to the goal rate when one is given) → why it matters → the verdict as the action. Do not change the verdict.")
        appendLine("Name what is driving the decision — the single decisive signal, or the combination that matters (e.g. waist flat + lifts up = lean mass).")
        appendLine("When both a previous target and the calorie target below are shown, you MUST name the new number as the action, with a verb matching the direction — \"trim from 2500 to 2300\" when the new target is lower, \"raise from 2300 to 2450\" when it is higher. Pin the change on the data, not the athlete.")
        appendLine("If NO previous target is shown, do not invent a target change — just state the verdict directly (e.g. \"hold calories\", \"keep calories where they are\").")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Tone: calm, direct, supportive. Never use shame, blame, or red-flag language.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — DO NOT reuse these numbers, they are not this athlete's data):")
        appendLine("\"Weight is down 0.30 kg/wk with waist flat and adherence at 88% — fat loss is on track, hold calories.\"")
        appendLine()
        appendLine("Answer using ONLY the verdict and signals below.")
        appendLine("Verdict: ${verdictLabel(context.result.verdict)}")
        appendLine("Context: ${context.result.summary}")
        appendLine()
        appendLine("Reasons:")
        context.result.reasonCodes.forEach { code ->
            appendLine("- ${reasonDescription(code)}")
        }
        appendLine()
        appendLine("Signals this week:")
        appendLine("- Weight: ${signed(context.input.weightTrendKgPerWeek, 2)} kg/wk (${weightLabel(context.input.weightTrendKgPerWeek)})")
        appendLine("- Waist: ${signed(context.input.waistTrendCmPerWeek, 1)} cm/wk (${waistLabel(context.input.waistTrendCmPerWeek)})")
        appendLine("- Performance: ${performanceLabel(context.input.performanceTrend)}")
        appendLine("- Recovery: ${recoveryLabel(context.input.recoveryTrend)}")
        appendLine("- Adherence: ${context.input.adherencePercent.roundToInt()}% (${adherenceLabel(context.input.adherencePercent)})")
        context.desiredWeeklyRateKg?.let {
            appendLine("- Goal rate: ${signed(it, 2)} kg/wk (compare weight against this)")
        }
        if (context.input.weeksSincePhaseStart != DEFAULT_WEEKS_FALLBACK) {
            appendLine("- Weeks in current phase: ${context.input.weeksSincePhaseStart}")
        }
        context.priorTargetCalories?.let {
            appendLine("- Previous target: $it kcal (new target below reflects this week's change)")
        }
        appendLine()
        appendLine("Calorie target: ${context.targetCalories} kcal | Protein target: ${context.targetProteinG}g")
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or the word \"Verdict\"; do not echo this prompt.")
    }

    fun buildProgressTrendPrompt(context: ProgressInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a body-recomposition coach interpreting an athlete's progress trends.")
        if (rich) {
            appendLine("Write a thorough, cross-signal interpretation (4–6 sentences) of what the combination of trends means for body recomposition. Connect the signals to each other; call out tension or agreement between weight, waist, lifts, and adherence.")
        } else {
            appendLine("Write exactly 1–2 short sentences in plain English: name the key signals and what they mean together for recomposition. No preamble or filler.")
        }
        appendLine("Connect the signals to each other and name what they mean together for recomposition.")
        appendLine("Only describe fat loss when waist is trending down; a flat waist means fat is steady, not falling.")
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere. End with exactly ONE thing to keep doing or watch (not two).")
        appendLine("If a prior-window weight figure is given, say whether the trend is accelerating, steady, or slowing versus it.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — do not reuse these numbers):")
        appendLine("\"Weight is flat at -0.05 kg/wk while waist is down 0.3 cm/wk and lifts are up 0.4 kg/wk — textbook recomposition, keep training hard and trust the trend.\"")
        appendLine()
        appendLine("Window: last ${context.rangeDays} days")
        appendLine("Signals:")
        appendLine("- Weight: ${context.weightTrendKgPerWeek?.let { "${signed(it, 2)} kg/wk (${weightLabel(it)})" } ?: "no data"}")
        appendLine("- Waist: ${context.waistTrendCmPerWeek?.let { "${signed(it, 1)} cm/wk (${waistLabel(it)})" } ?: "no data"}")
        appendLine("- Lifts: ${context.liftTrendKgPerWeek?.let { "${signed(it, 1)} kg/wk e1RM (${liftTrendLabel(it)})" } ?: "no data"}")
        appendLine("- Adherence: ${context.adherencePercent?.let { "${it.roundToInt()}% (${adherenceLabel(it)})" } ?: "no data"}")
        context.priorWeightTrendKgPerWeek?.let {
            appendLine("- Prior-window weight: ${signed(it, 2)} kg/wk (compare current weight against this)")
        }
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a training-recovery coach.")
        if (rich) {
            appendLine("Write a thorough, cross-signal readiness assessment (4–6 sentences) for the athlete today. Relate sleep, energy, hunger, and soreness to each other and to whether they trained.")
        } else {
            appendLine("Write exactly 1–2 short sentences: assess readiness today and give one concrete suggestion. No preamble or filler.")
        }
        appendLine("Lead with the SINGLE most decisive signal (even if several point the same way), then give exactly ONE concrete suggestion. Do NOT give medical advice or diagnose anything.")
        appendLine("Frame that signal against the athlete's own recent average when one is given (e.g. \"5 h, below your usual 7.4\"); hedge single-day readings rather than overstating them.")
        appendLine("Respect the trained flag: on a rest day, frame the suggestion around recovery or light movement, not adding hard training.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — do not reuse these numbers):")
        appendLine("\"On 5 hours of sleep — below your usual 7.4 — with soreness at 8/10, recovery is behind; keep today's session light.\"")
        appendLine()
        appendLine("Signals today:")
        context.sleepHours?.let {
            val avg = context.avgSleepHours?.let { a -> " vs your usual ${String.format(Locale.US, "%.1f", a)} h" } ?: ""
            appendLine("- Sleep: ${String.format(Locale.US, "%.1f", it)} h (${sleepLabel(it)})$avg")
        }
        context.energyScore?.let {
            val avg = context.avgEnergyScore?.let { a -> " vs your usual $a/10" } ?: ""
            appendLine("- Energy: $it/10 (${scoreLabel(it)})$avg")
        }
        context.hungerScore?.let { appendLine("- Hunger: $it/10 (${scoreLabel(it)})") }
        context.sorenessScore?.let { appendLine("- Soreness: $it/10 (${scoreLabel(it)})") }
        appendLine("- Trained today: ${if (context.trained) "yes" else "no"}")
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    fun buildRestOfDayPrompt(context: RestOfDayInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a nutrition coach advising an athlete on the rest of their day.")
        if (rich) {
            appendLine("Write a thorough, cross-signal plan (4–6 sentences): state where they stand, frame the remaining gap, and connect it to meal timing and protein distribution for the rest of the day.")
        } else {
            appendLine("Write exactly 1–2 short sentences: state where they stand and name one priority for remaining meals. No preamble or filler.")
        }
        appendLine("Open by stating where they stand against plan — calories consumed vs target (or the calorie zone) and, if a percent of the day is given, how much of the day is left — THEN name the single biggest gap (calories or protein) and exactly ONE concrete priority for the remaining meals.")
        appendLine("A NEGATIVE \"remaining\" means they are already OVER target — say \"over\" or \"past target\", never \"short\". When over on calories, steer the remaining meals toward protein-dense, lower-calorie choices.")
        appendLine("Do NOT invent specific foods, brands, or macro numbers beyond what is given. Frame the gap and give general guidance.")
        appendLine("Use the day-elapsed percent to judge pace — ahead of or behind where they should be by now.")
        appendLine("Base everything only on the numbers below.")
        appendLine("Lead with the most decisive number from the numbers below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — do not reuse these numbers):")
        appendLine("\"Just over halfway through the day you're at 1,420 of 2,200 kcal with 38 g protein left — on pace, so make dinner protein-heavy to close the gap.\"")
        appendLine()
        // Calories may go negative (overeating is useful signal to surface); protein
        // remaining clamps at 0 since "negative protein to go" is meaningless advice.
        val calRemaining = context.targetCalories - context.caloriesConsumed
        val proteinRemaining = (context.proteinTargetG - context.proteinConsumedG).coerceAtLeast(0.0)
        appendLine("Current intake:")
        val calStanding = if (calRemaining < 0) "$calRemaining remaining, i.e. ${-calRemaining} OVER target" else "$calRemaining remaining"
        appendLine("- Calories: ${context.caloriesConsumed} of ${context.targetCalories} kcal ($calStanding)")
        appendLine("- Protein: ${context.proteinConsumedG.roundToInt()} of ${context.proteinTargetG} g (${proteinRemaining.roundToInt()} g remaining)")
        appendLine("- Calorie zone: ${context.calorieZoneLowerBound}–${context.calorieZoneUpperBound} kcal")
        appendLine("- Meals logged so far: ${context.mealsLoggedCount}")
        context.fractionOfDayElapsed?.let {
            appendLine("- Day elapsed: ${(it * 100).roundToInt()}%")
        }
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    fun buildPatternInsightPrompt(context: PatternInsightContext): String = buildString {
        appendLine("You are a body-recomposition coach highlighting one pattern you noticed in the athlete's recent data.")
        appendLine("Write exactly 1–2 short sentences: rephrase the finding, then add ONE small, specific suggestion to act on it. Lead with the specific number. No preamble or filler.")
        appendLine("Offer the suggestion, don't command it; use only the finding below and do not invent or calculate any new numbers.")
        appendLine("Keep a calm, supportive, non-judgmental tone — frame it as an observation worth acting on, not a scolding.")
        appendLine()
        appendLine("Finding: ${context.fact.statement}")
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any label or echo this prompt.")
    }

    fun buildTargetChangePrompt(context: TargetChangeContext): String = buildString {
        val direction = if (context.newTarget > context.oldTarget) "raise" else "trim"
        appendLine("You are a body-recomposition coach explaining why the athlete's calorie target is changing this week.")
        appendLine("Write 1–2 short sentences. Structure: what the data showed → why that means the target should move → the new number.")
        appendLine("Pin the change on the data and metabolism, never on the athlete's effort or willpower.")
        appendLine("Lead with the most decisive number; use the verb \"$direction\" to match the direction. Calm, supportive tone, no shame.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example when raising (losing too fast — style only, do not reuse these numbers):")
        appendLine("\"You've been losing 0.60 kg/wk, faster than your 0.40 goal, so your burn looks higher than planned — raise calories from 2300 to 2450.\"")
        appendLine("Example when trimming (loss stalled — style only):")
        appendLine("\"Weight has stalled at -0.02 kg/wk despite good adherence, so the deficit has shrunk — trim calories from 2500 to 2300 to get loss moving again.\"")
        appendLine("When trimming because loss stalled, say the deficit has shrunk or loss has stalled; do NOT claim the burn is higher (that would contradict a cut).")
        appendLine()
        appendLine("Why the target is changing:")
        appendLine("- Current target: ${context.oldTarget} kcal")
        appendLine("- New target: ${context.newTarget} kcal")
        appendLine("- Weight trend: ${signed(context.weightTrendKgPerWeek, 2)} kg/wk")
        context.desiredWeeklyRateKg?.let { appendLine("- Goal rate: ${signed(it, 2)} kg/wk (compare the weight trend against this)") }
        appendLine("- Adherence: ${context.adherencePercent.roundToInt()}%")
        appendLine("Reasons:")
        context.reasonCodes.forEach { appendLine("- ${reasonDescription(it)}") }
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    fun buildNoiseDefuserPrompt(context: NoiseDefuserContext): String = buildString {
        appendLine("You are a body-recomposition coach reassuring an athlete about a single day's scale reading that looks alarming but conflicts with the real trend.")
        appendLine("Write 1–2 short sentences: acknowledge today's jump, contrast it with the smoothed trend, and explain it is normal day-to-day fluctuation (water, food, glycogen) — so there is nothing to act on today.")
        appendLine("Lead with the numbers. Calm, reassuring tone; never alarm. Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — do not reuse these numbers):")
        appendLine("\"You're up 0.9 kg this morning, but your trend is still flat at -0.02 kg/wk — that's water and food weight, not fat, so nothing to change.\"")
        appendLine()
        appendLine("Today's reading:")
        appendLine("- Today vs last weigh-in: ${signed(context.todayDeltaKg, 1)} kg")
        appendLine("- Smoothed trend: ${signed(context.smoothedTrendKgPerWeek, 2)} kg/wk")
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    fun buildCrossMetricPrompt(context: CrossMetricContext): String = buildString {
        appendLine("You are a body-recomposition coach surfacing a link you noticed between two of the athlete's habits.")
        appendLine("Write exactly 1–2 short sentences: state the link and lead with the number, hedge it as a tendency (\"you tend to…\") rather than a proven cause, then add ONE small suggestion to use it.")
        appendLine("Use only the finding below; do not invent or calculate any new numbers. Supportive, non-judgmental tone.")
        appendLine()
        appendLine("Finding: ${context.fact.statement}")
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any label or echo this prompt.")
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
        else -> "low, below target"
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

    companion object {
        /**
         * Formats [value] to [decimals] places with an explicit leading sign:
         * positives get "+", negatives keep "-", and anything that rounds to zero
         * renders unsigned (no "+0.00" or "-0.00"). US locale so the decimal is always a dot.
         */
        internal fun signed(value: Double, decimals: Int): String {
            val factor = 10.0.pow(decimals)
            val rounded = round(value * factor) / factor
            // -0.0 == 0.0 is true in IEEE 754; assigning the 0.0 literal canonicalizes the
            // sign bit so a value that rounds to zero never prints as "-0.00".
            val norm = if (rounded == 0.0) 0.0 else rounded
            val body = String.format(Locale.US, "%.${decimals}f", norm)
            return if (norm > 0.0) "+$body" else body
        }

        /**
         * Trims [text] to at most [max] sentences. Splits only on sentence-ending
         * punctuation followed by whitespace + a capital letter, so decimals like
         * "1.5 kg" and inline dashes are not treated as sentence breaks.
         */
        fun limitToSentences(text: String, max: Int): String {
            val sentenceBreak = Regex("""(?<=[.!?])\s+(?=[A-Z])""")
            val sentences = text.split(sentenceBreak).filter { it.isNotBlank() }
            if (sentences.size <= max) return text
            val truncated = sentences.take(max).joinToString(" ")
            return if (truncated.last() in listOf('.', '!', '?')) truncated else "$truncated."
        }

        private const val DEFAULT_WEEKS_FALLBACK = 4
        private const val WEIGHT_THRESHOLD = 0.20
        private const val WAIST_THRESHOLD = 0.25
        private const val LIFT_THRESHOLD = 0.1

        private val REASON_DESCRIPTIONS = mapOf(
            "LOSING_WITH_POOR_RECOVERY" to "Weight is falling while performance or recovery is suffering",
            "GAINING_WITH_WAIST_INCREASE" to "Both weight and waist are trending upward",
            "EARLY_SCALE_JUMP" to "Large weight change in week one likely reflects water, not fat",
            "MAINTENANCE_TREND" to "Weight, waist, and performance are all stable",
            "LOW_ADHERENCE" to "Intake was too far from target too often to draw a reliable conclusion",
            "INSUFFICIENT_DATA" to "Not enough days logged to make a reliable assessment",
            "NO_CLEAR_CHANGE_SIGNAL" to "No strong signal emerged this week",
            "WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP" to "Weight rising but waist stable and performance improving — likely lean mass",
        )
    }
}
