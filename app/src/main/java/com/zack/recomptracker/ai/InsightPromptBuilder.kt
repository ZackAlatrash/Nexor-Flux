package com.zack.recomptracker.ai

import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.pow
import java.util.Locale

class InsightPromptBuilder {

    fun buildProgressTrendPrompt(context: ProgressInsightContext): String = buildString {
        appendLine("You are a body-recomposition coach delivering a Recomp Progress Verdict on an athlete's trends.")
        appendLine("Write exactly 1–2 short sentences in plain English. No preamble or filler.")
        appendLine("Connect the signals to each other and name what they mean together for recomposition.")
        appendLine("Name the SINGLE limiting factor — the one signal most holding recomposition back (or, if all are on track, say the plan is working).")
        appendLine("Only describe fat loss when waist is trending down; a flat waist means fat is steady, not falling.")
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere.")
        appendLine("END WITH EXACTLY ONE ACTION: the single lever to pull next (e.g. \"add a set to the lagging lift\", \"train the missed sessions\", \"hold the plan and trust the trend\"). Not two actions, and not a pure description.")
        appendLine("If a prior-window weight figure is given, say whether the trend is accelerating, steady, or slowing versus it.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output (style only — do not reuse these numbers):")
        appendLine("\"Weight is flat at -0.05 kg/wk while waist is down 0.3 cm/wk — recomposition is working; hold the plan and keep training hard.\"")
        appendLine("\"Lifts are flat at +0.0 kg/wk e1RM while waist holds — strength is the limiter; add a set to your lagging lift.\"")
        appendLine()
        appendLine("Window: last ${context.rangeDays} days")
        appendLine("Signals:")
        appendLine("- Weight: ${context.weightTrendKgPerWeek?.let { "${signed(it, 2)} kg/wk (${weightLabel(it)})" } ?: "no data"}")
        appendLine("- Waist: ${context.waistTrendCmPerWeek?.let { "${signed(it, 1)} cm/wk (${waistLabel(it)})" } ?: "no data"}")
        appendLine("- Lifts: ${context.liftTrendKgPerWeek?.let { "${signed(it, 1)} kg/wk e1RM (${liftTrendLabel(it)})" } ?: "no data"}")
        appendLine("- Training frequency: ${trainingFrequencyLine(context)}")
        appendLine("- Adherence: ${context.adherencePercent?.let { "${it.roundToInt()}% (${adherenceLabel(it)})" } ?: "no data"}")
        context.priorWeightTrendKgPerWeek?.let {
            appendLine("- Prior-window weight: ${signed(it, 2)} kg/wk (compare current weight against this)")
        }
        appendLine()
        appendLine("Now reply with ONLY the coaching sentence(s). Do not print any field label or echo this prompt.")
    }

    /**
     * Training-frequency signal line: actual sessions/week, framed against the profile target when one
     * is set (e.g. "2.5/wk vs 4/wk target (below target)"). "no data" when nothing was trained.
     */
    private fun trainingFrequencyLine(context: ProgressInsightContext): String {
        val actual = context.trainingSessionsPerWeek ?: return "no data"
        val actualStr = String.format(Locale.US, "%.1f", actual)
        val target = context.weeklyGymSessionsTarget
        return if (target != null && target > 0) {
            "$actualStr/wk vs $target/wk target (${frequencyLabel(actual, target)})"
        } else {
            "$actualStr/wk"
        }
    }

    private fun frequencyLabel(actual: Double, target: Int): String = when {
        actual >= target - FREQUENCY_TOLERANCE -> "on target"
        actual >= target * 0.6 -> "below target"
        else -> "well below target"
    }

    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext): String = buildString {
        appendLine("You are a training-recovery coach.")
        appendLine("Write exactly 1–2 short sentences: assess readiness today and give one concrete suggestion. No preamble or filler.")
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

        private const val WEIGHT_THRESHOLD = 0.20
        private const val WAIST_THRESHOLD = 0.25
        private const val LIFT_THRESHOLD = 0.1
        // Within half a session per week of the target counts as "on target".
        private const val FREQUENCY_TOLERANCE = 0.5
    }
}
