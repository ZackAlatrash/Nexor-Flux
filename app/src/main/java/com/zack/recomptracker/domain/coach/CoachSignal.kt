package com.zack.recomptracker.domain.coach

import kotlinx.serialization.Serializable

/**
 * The deterministic engine's unit of output. A [CoachSignal] is a fully-decided coaching moment:
 * the engine has already chosen *what matters*, *why*, *how important it is*, and the *supporting
 * facts* — the cloud LLM only rephrases [verdict]/[fallbackText] later. See
 * `docs/ai-redesign/08-technical-architecture.md` §2. Pure Kotlin; no Android/LLM dependency.
 *
 * Invariants (enforced): a signal always carries a decision ([verdict]) and number-safe
 * [fallbackText] so a surface can render it with no network and no model.
 */
@Serializable
data class CoachSignal(
    val kind: SignalKind,
    val tier: SignalTier,
    val category: SignalCategory,
    /** Distance past threshold (0..100). Ranks signals *within* a tier; higher = more urgent. */
    val severity: Int,
    val facts: SignalFacts,
    /** The decision — the single most important field. Never blank. */
    val verdict: String,
    val action: CoachAction = CoachAction.None,
    val rationale: SignalRationale,
    /** Stable identity for dedup/cooldown: kind + bucketed inputs (+ ISO week for weekly signals). */
    val dedupKey: String,
    val surface: CoachSurface,
    /** Engine-authored, number-safe text rendered when the LLM is unavailable. Never blank. */
    val fallbackText: String,
) {
    init {
        require(verdict.isNotBlank()) { "CoachSignal.verdict must not be blank (kind=$kind)" }
        require(fallbackText.isNotBlank()) { "CoachSignal.fallbackText must not be blank (kind=$kind)" }
        require(severity in 0..100) { "CoachSignal.severity must be 0..100 (was $severity, kind=$kind)" }
        require(dedupKey.isNotBlank()) { "CoachSignal.dedupKey must not be blank (kind=$kind)" }
    }
}

/** Priority band. P0 may push and always outranks lower tiers; P3 is ambient / in-app only. */
@Serializable
enum class SignalTier { P0, P1, P2, P3 }

/** Which life-area the signal is about (the cross-domain axis). */
@Serializable
enum class SignalCategory { NUTRITION, TRAINING, BODY, ACTIVITY, RECOVERY, BEHAVIOR, PLAN }

/** Where the signal is allowed to appear. */
@Serializable
enum class CoachSurface { PUSH, TODAY, WEEKLY, CHAT_ONLY }

/** How much the engine trusts this signal given data sufficiency/noise. */
@Serializable
enum class Confidence { HIGH, MEDIUM, LOW, INSUFFICIENT }

/** Only smoothed, engine-computed numbers — the sole numbers allowed to reach a prompt/UI. */
@Serializable
data class SignalFacts(val values: Map<String, String> = emptyMap())

/** The trust payload: the *why*, named across domains. */
@Serializable
data class SignalRationale(
    val primaryCauseByDomain: Map<String, String> = emptyMap(),
    val behaviorToOutcome: String? = null,
    val confidence: Confidence,
)

/** A deep-link the surface can turn into a button. [None] means the signal is informational only. */
@Serializable
data class CoachAction(val type: CoachActionType, val label: String = "") {
    companion object {
        val None = CoachAction(CoachActionType.NONE)
    }
}

@Serializable
enum class CoachActionType {
    NONE,
    LOG_WEIGHT,
    LOG_STEPS,
    CONFIRM_PLANNED_MEALS,
    OPEN_WEEKLY_REVIEW,
    APPLY_TARGET,
    OPEN_TRAINING,
    OPEN_FOOD_LOG,
}

/**
 * Stable identity of each coaching moment the engine can emit. Used in [CoachSignal.dedupKey] and
 * for cooldown. Extend this enum to add detectors — nothing else in the pipeline changes.
 */
@Serializable
enum class SignalKind {
    // ── Plan / body (the recomposition thesis) ──
    RECOMP_WIN,               // weight flat while waist/skinfold falling
    FAT_GAIN_WARNING,         // weight up AND waist up
    WEEKLY_VERDICT,           // the adjustment-engine calorie verdict
    // ── Nutrition ──
    LOW_ADHERENCE,            // logging/adherence below threshold
    PROTEIN_MISS_TRAINING_DAY,// protein short specifically on trained days
    DERAILMENT_DAY,           // one day drove the week's surplus
    // ── Activity ──
    NEAT_COLLAPSE,            // steps down explains a stalled weight trend
    STEP_STREAK_AT_RISK,      // step streak about to break
    // ── Training ──
    TRAINING_PLATEAU,         // e1RM stalled
    NEW_PR,                   // new estimated 1RM (celebration)
    DELOAD_DUE,               // RIR falling + poor recovery
    WORKOUT_STREAK_AT_RISK,   // missed-workout pattern
    // ── Recovery ──
    RECOVERY_DECLINE,         // sleep/energy/soreness trending poor
    // ── Behaviour ("noticing") ──
    UNCONFIRMED_PLANNED_MEALS,// stale planned meals to confirm
    QUIET_WEIGH_INS,          // no weigh-in in a while
    // ── Silence ──
    INSUFFICIENT_DATA,        // not enough logged days — hold, don't manufacture insight
}
