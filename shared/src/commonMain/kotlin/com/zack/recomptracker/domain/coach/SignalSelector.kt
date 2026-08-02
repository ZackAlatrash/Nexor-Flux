package com.zack.recomptracker.domain.coach

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The deterministic "rank -> one winner" stage of the proactive engine
 * (`docs/ai-redesign/08-technical-architecture.md` §1 and §9). Pure Kotlin: given the signals a
 * detector catalog fired plus the dedup ledger, it decides *whether* to speak and *which one thing*
 * to say — the cloud LLM only phrases the winner later, never chooses it.
 *
 * All state is passed in (the [seen] ledger, [today]) so the logic stays side-effect-free and unit
 * testable with no persistence or Android. Ordering is total and stable, so the same batch always
 * produces the same winner.
 *
 * Pipeline, in order:
 * 1. **Rank** — `tier` (P0 highest) then `severity` descending then `kind.ordinal` ascending (a stable
 *    final tiebreak so ties never depend on input order).
 * 2. **Dedup within the batch** — signals sharing a [CoachSignal.dedupKey] collapse to the single
 *    highest-ranked one; the rest go to [SelectionResult.suppressed].
 * 3. **Cooldown** — a signal whose `dedupKey` was surfaced fewer than [cooldownDays] ago (per [seen])
 *    is suppressed and can't win. "Never re-surface until its signal changes or >= cooldownDays pass."
 * 4. **One winner** — the single highest-ranked eligible signal, or `null` when none are eligible
 *    (silence is a valid, first-class output). Losers stay in [SelectionResult.ranked] so callers can
 *    demote them to passive per-screen surfaces — they are never discarded.
 */
class SignalSelector {

    /**
     * @param signals the firing signals for this slot (any order).
     * @param seen dedupKey -> the date it was last surfaced; drives cooldown.
     * @param today the evaluation date.
     * @param cooldownDays a dedupKey can't re-surface until this many days have elapsed (default 7,
     *   per §9). `< 1` disables cooldown.
     */
    fun select(
        signals: List<CoachSignal>,
        seen: Map<String, LocalDate>,
        today: LocalDate,
        cooldownDays: Int = DEFAULT_COOLDOWN_DAYS,
    ): SelectionResult {
        // 1. Total, stable rank.
        val ranked = signals.sortedWith(RANK)

        val suppressed = mutableListOf<CoachSignal>()
        val eligible = mutableListOf<CoachSignal>()
        val keptKeys = mutableSetOf<String>()

        for (signal in ranked) {
            // 2. Duplicate suppression — the first (best-ranked) signal per dedupKey wins the key.
            if (!keptKeys.add(signal.dedupKey)) {
                suppressed += signal
                continue
            }
            // 3. Cooldown — suppress a key surfaced too recently.
            if (onCooldown(signal.dedupKey, seen, today, cooldownDays)) {
                suppressed += signal
                continue
            }
            eligible += signal
        }

        // 4. One winner (or silence).
        return SelectionResult(
            winner = eligible.firstOrNull(),
            ranked = eligible,
            suppressed = suppressed,
        )
    }

    /**
     * Convenience filter: run [select] over only the signals allowed on [surface]. Signals bound to
     * another surface are ignored entirely (not reported as suppressed — they simply aren't candidates
     * for this slot).
     */
    fun selectForSurface(
        surface: CoachSurface,
        signals: List<CoachSignal>,
        seen: Map<String, LocalDate>,
        today: LocalDate,
        cooldownDays: Int = DEFAULT_COOLDOWN_DAYS,
    ): SelectionResult =
        select(signals.filter { it.surface == surface }, seen, today, cooldownDays)

    private fun onCooldown(
        dedupKey: String,
        seen: Map<String, LocalDate>,
        today: LocalDate,
        cooldownDays: Int,
    ): Boolean {
        if (cooldownDays < 1) return false
        val lastSurfaced = seen[dedupKey] ?: return false
        val elapsed = lastSurfaced.daysUntil(today)
        return elapsed < cooldownDays
    }

    companion object {
        const val DEFAULT_COOLDOWN_DAYS = 7

        /** P0 first, then higher severity, then lower kind.ordinal — total and deterministic. */
        private val RANK: Comparator<CoachSignal> =
            compareBy<CoachSignal> { it.tier.ordinal }
                .thenByDescending { it.severity }
                .thenBy { it.kind.ordinal }
    }
}

/**
 * Output of [SignalSelector.select]. [winner] is the single signal to feature (or `null` for silence);
 * [ranked] is every *eligible* signal in rank order (winner first) so callers can demote losers to
 * passive surfaces; [suppressed] is everything dropped by dedup or cooldown.
 */
data class SelectionResult(
    val winner: CoachSignal?,
    val ranked: List<CoachSignal>,
    val suppressed: List<CoachSignal>,
)
