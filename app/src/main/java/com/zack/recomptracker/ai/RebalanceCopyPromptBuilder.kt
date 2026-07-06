package com.zack.recomptracker.ai

/**
 * The surfaces the Weekly Rebalance card renders copy for, plus the two supportive "no plan needed"
 * notes. See `docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` §3 (user experience) and
 * §8 (cloud copy service + fallback templates), and the dynamic-intensity
 * `2026-07-06-dynamic-weekly-rebalance-design.md` §11 (size-scaled `OFFER_BODY`, `OFFER_PARTIAL_LINE`,
 * the reworded resume `NO_ADJUSTMENT`, and `REASSURANCE`).
 */
internal enum class RebalanceCopySlot {
    OFFER_HEADLINE, OFFER_BODY, OFFER_PARTIAL_LINE, PROGRESS_LINE, GRACEFUL_END, COMPLETION,
    NO_ADJUSTMENT, REASSURANCE,
}

/**
 * Pre-formatted deterministic facts for one copy request. The engine has already decided every
 * number; [RebalanceCopyPromptBuilder]/[RebalanceCopyService] only ever rephrase them — see spec §8.
 * [surplusKcal]/[recoveredKcal]/[partial] back the dynamic-intensity slots (spec §11): the size-scaled
 * `OFFER_BODY` intro keys on [surplusKcal], and `OFFER_PARTIAL_LINE` reports [recoveredKcal] of
 * [surplusKcal] (shown by the UI only when [partial]).
 */
internal data class RebalanceCopyFacts(
    val lengthDays: Int,
    val dailyCalorieReduction: Int,
    val extraDailySteps: Int,
    val effectiveCalories: Int,
    val dayX: Int,
    val ofY: Int,
    val surplusKcal: Int = 0,
    val recoveredKcal: Int = 0,
    val partial: Boolean = false,
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
internal object RebalanceCopyPromptBuilder {

    /**
     * Returns the deterministic fallback text for [slot], substituting [facts]. Verbatim from spec
     * §8 (dynamic-intensity reword: spec §11), with the `stepsClause` rule: it is appended to
     * `OFFER_BODY` only when `extraDailySteps > 0`, and a MOVE_MORE-style plan
     * (`dailyCalorieReduction == 0` with `extraDailySteps > 0`) drops the kcal phrase entirely and
     * leads with the steps figure instead — a plan can never claim "0 kcal less a day".
     */
    fun fallback(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String = when (slot) {
        RebalanceCopySlot.OFFER_HEADLINE ->
            "Your weekly goal is still within reach."

        RebalanceCopySlot.OFFER_BODY -> offerBody(facts)

        RebalanceCopySlot.OFFER_PARTIAL_LINE ->
            "Recovers about ${facts.recoveredKcal} of ${facts.surplusKcal} — a big one, but this " +
                "keeps you moving the right way."

        RebalanceCopySlot.PROGRESS_LINE ->
            "You're ${facts.dayX} of ${facts.ofY} days in — today's target is " +
                "${facts.effectiveCalories} kcal."

        RebalanceCopySlot.GRACEFUL_END ->
            "Let's ease off the rebalance — this week had a lot in it, and that's completely fine. " +
                "Back to your normal plan tomorrow."

        RebalanceCopySlot.COMPLETION ->
            "Rebalance complete — nicely done. Your weekly average is back on track."

        // The resume note (surplus > HUGE_SURPLUS_KCAL) — a blowout beyond what even Light can claw
        // back in 7 days. Reworded per spec §11/§16: never frame it as a failure to make up for.
        RebalanceCopySlot.NO_ADJUSTMENT ->
            "One rough patch won't derail you — you can't sensibly claw all of it back without it " +
                "feeling like a punishment. Just resume your normal plan tomorrow."

        // The reassurance note (surplus < SMALL_SURPLUS_KCAL) — a small slip that would make a mini-plan
        // noise, not a decision.
        RebalanceCopySlot.REASSURANCE ->
            "You're still on track — your weekly average is still near target. Nothing to do."
    }

    /**
     * `OFFER_BODY` intro scales with the size of the surplus (spec §11): under 700 kcal reads as a
     * "slightly high stretch", 700..<1400 as "a couple of heavier days", 1400+ as "a big few days".
     * The body then leads with the calorie reduction (appending a steps clause when present); a
     * MOVE_MORE plan with no calorie reduction (`dailyCalorieReduction == 0`) leads with the steps
     * figure instead so the sentence never reads "about 0 kcal less a day".
     */
    private fun offerBody(facts: RebalanceCopyFacts): String {
        val intro = when {
            facts.surplusKcal < 700 -> "A slightly high stretch nudged your week up."
            facts.surplusKcal < 1400 -> "A couple of heavier days nudged your week up."
            else -> "A big few days pushed your week up."
        }
        val lead = if (facts.dailyCalorieReduction == 0 && facts.extraDailySteps > 0) {
            "about +${facts.extraDailySteps} steps a day"
        } else {
            val stepsClause = if (facts.extraDailySteps > 0) " and +${facts.extraDailySteps} steps" else ""
            "about ${facts.dailyCalorieReduction} kcal less a day$stepsClause"
        }
        return "$intro Here's a gentle ${facts.lengthDays}-day way back — $lead."
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
        appendLine("- surplusKcal: ${facts.surplusKcal}")
        appendLine("- recoveredKcal: ${facts.recoveredKcal}")
        appendLine("- partial: ${facts.partial}")
    }.trim()
}
