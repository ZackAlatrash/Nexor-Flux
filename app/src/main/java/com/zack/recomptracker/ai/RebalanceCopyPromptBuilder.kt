package com.zack.recomptracker.ai

/**
 * The five surfaces the Weekly Rebalance card renders copy for, plus the supportive "no adjustment"
 * note. See `docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` §3 (user experience) and
 * §8 (cloud copy service + fallback templates).
 */
enum class RebalanceCopySlot { OFFER_HEADLINE, OFFER_BODY, PROGRESS_LINE, GRACEFUL_END, COMPLETION, NO_ADJUSTMENT }

/**
 * Pre-formatted deterministic facts for one copy request. The engine has already decided every
 * number; [RebalanceCopyPromptBuilder]/[RebalanceCopyService] only ever rephrase them — see spec §8.
 */
data class RebalanceCopyFacts(
    val lengthDays: Int,
    val dailyCalorieReduction: Int,
    val extraDailySteps: Int,
    val effectiveCalories: Int,
    val dayX: Int,
    val ofY: Int,
)

/**
 * Builds the deterministic fallback copy (spec §8 templates, verbatim) and the cloud phrasing
 * prompt for one [RebalanceCopySlot]. Pure string assembly — no Android, no network, mirrors
 * [CoachPhrasingPromptBuilder]'s "engine facts in, phrasing out" contract.
 *
 * The templates are the feature's copy when cloud AI is off, times out, or errors — see
 * [RebalanceCopyService]. They must never be edited without updating spec §8, since [fallback] is
 * asserted verbatim in `RebalanceCopyServiceTest`.
 */
object RebalanceCopyPromptBuilder {

    /**
     * Returns the deterministic fallback text for [slot], substituting [facts]. Verbatim from spec
     * §8, with the `stepsClause` rule: it is appended to `OFFER_BODY` only when
     * `extraDailySteps > 0`, and a MOVE_MORE-style plan (`dailyCalorieReduction == 0` with
     * `extraDailySteps > 0`) drops the kcal phrase entirely and leads with the steps figure instead
     * — a plan can never claim "0 kcal less a day".
     */
    fun fallback(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String = when (slot) {
        RebalanceCopySlot.OFFER_HEADLINE ->
            "Your weekly goal is still within reach."

        RebalanceCopySlot.OFFER_BODY -> offerBody(facts)

        RebalanceCopySlot.PROGRESS_LINE ->
            "You're ${facts.dayX} of ${facts.ofY} days in — today's target is " +
                "${facts.effectiveCalories} kcal."

        RebalanceCopySlot.GRACEFUL_END ->
            "Let's ease off the rebalance — this week had a lot in it, and that's completely fine. " +
                "Back to your normal plan tomorrow."

        RebalanceCopySlot.COMPLETION ->
            "Rebalance complete — nicely done. Your weekly average is back on track."

        RebalanceCopySlot.NO_ADJUSTMENT ->
            "This week ran high enough that a mini-plan wouldn't add much — best to just pick up " +
                "your normal plan tomorrow."
    }

    /**
     * `OFFER_BODY` has two shapes: the normal (EAT_LESS/BALANCED) template leads with the calorie
     * reduction and appends a steps clause when present; a MOVE_MORE plan with no calorie reduction
     * (`dailyCalorieReduction == 0`) leads with the steps figure instead so the sentence never reads
     * "about 0 kcal less a day".
     */
    private fun offerBody(facts: RebalanceCopyFacts): String {
        val lead = if (facts.dailyCalorieReduction == 0 && facts.extraDailySteps > 0) {
            "about +${facts.extraDailySteps} steps a day"
        } else {
            val stepsClause = if (facts.extraDailySteps > 0) " and +${facts.extraDailySteps} steps" else ""
            "about ${facts.dailyCalorieReduction} kcal less a day$stepsClause"
        }
        return "Yesterday ran a bit high. Want a light ${facts.lengthDays}-day rebalance — " +
            "$lead — to bring your weekly average back near target?"
    }

    /** System prompt for the cloud phrasing call — spec §8 wording. */
    fun systemPrompt(): String =
        "You are a supportive nutrition coach. Rephrase a supportive nutrition message. Use ONLY " +
            "the numbers provided; invent none. Never imply the user failed, cheated, or must " +
            "'make up for' anything. 1-2 warm sentences. No markdown."

    /**
     * Assembles the user prompt: the deterministic fallback text to rephrase plus every fact value,
     * with an explicit instruction to use only those numbers. The model never sees raw engine state
     * — only the already-decided sentence and its labelled numbers, same discipline as
     * [CoachPhrasingPromptBuilder.buildUserPrompt].
     */
    fun userPrompt(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String = buildString {
        appendLine("Rephrase this supportively, keeping the same meaning:")
        appendLine(fallback(slot, facts))
        appendLine()
        appendLine("Numbers you may use (do not add any others):")
        appendLine("- lengthDays: ${facts.lengthDays}")
        appendLine("- dailyCalorieReduction: ${facts.dailyCalorieReduction}")
        appendLine("- extraDailySteps: ${facts.extraDailySteps}")
        appendLine("- effectiveCalories: ${facts.effectiveCalories}")
        appendLine("- dayX: ${facts.dayX}")
        appendLine("- ofY: ${facts.ofY}")
    }.trim()
}
