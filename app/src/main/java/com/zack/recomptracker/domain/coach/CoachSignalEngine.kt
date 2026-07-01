package com.zack.recomptracker.domain.coach

/**
 * The deterministic proactive engine (`docs/ai-redesign/08-technical-architecture.md` §2/§9). Runs
 * every [CoachDetector] over one [CoachContext] snapshot and collects the non-null [CoachSignal]s.
 * Ranking/rate-limiting live downstream (a later phase) — this stage's only job is to decide *what
 * could* be said and compute every number.
 *
 * **Silence is first-class.** When the nutrition/body window has fewer than [MIN_LOGGED_DAYS] logged
 * days the engine emits ONLY a single [SignalKind.INSUFFICIENT_DATA] hold signal — it never
 * manufactures other insights on thin data (mirrors `AdjustmentEngine`'s `WAIT_FOR_DATA`).
 *
 * Pure Kotlin; injectable detector list for tests, plus [default] wiring the real catalog.
 */
class CoachSignalEngine(
    private val detectors: List<CoachDetector>,
) {
    fun evaluate(ctx: CoachContext): List<CoachSignal> {
        if (ctx.nutrition.loggedDaysInWindow < MIN_LOGGED_DAYS) {
            return listOf(InsufficientDataDetector.hold(ctx))
        }
        return detectors.mapNotNull { it.detect(ctx) }
    }

    companion object {
        const val MIN_LOGGED_DAYS = 14

        /** The real detector catalog, in stable order. */
        fun default(): CoachSignalEngine = CoachSignalEngine(
            listOf(
                RecompWinDetector(),
                FatGainWarningDetector(),
                LowAdherenceDetector(),
                RecoveryDeclineDetector(),
                DeloadDueDetector(),
                NeatCollapseDetector(),
                TrainingPlateauDetector(),
                NewPrDetector(),
                StepStreakAtRiskDetector(),
                UnconfirmedPlannedMealsDetector(),
                QuietWeighInsDetector(),
                DerailmentDayDetector(),
                ProteinTrainingDayDetector(),
                SleepHungerLinkDetector(),
            ),
        )
    }
}
