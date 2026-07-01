package com.zack.recomptracker.domain.coach

/**
 * One deterministic coaching rule. A detector inspects the [CoachContext] snapshot and either fires
 * (returns a fully-decided [CoachSignal] - facts, verdict, tier, severity, dedupKey) or stays silent
 * (returns null when its threshold is not met). Detectors are pure Kotlin and independently testable
 * with plain objects; the math they need lives in the existing domain calculators, never here.
 *
 * See docs/ai-redesign/08-technical-architecture.md sections 2 and 9, and the trigger catalog in
 * docs/ai-redesign/03-proactive-ai-design.md section 1.
 */
fun interface CoachDetector {
    fun detect(ctx: CoachContext): CoachSignal?
}
