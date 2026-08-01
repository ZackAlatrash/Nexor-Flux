package com.zack.recomptracker.domain.coach

/**
 * The silence-is-first-class output. Emitted by [CoachSignalEngine] (not run in the normal catalog)
 * when the logged-day window is below [CoachSignalEngine.MIN_LOGGED_DAYS]: a low-tier hold that tells
 * the user to keep logging instead of manufacturing a shaky insight. Mirrors `AdjustmentEngine`'s
 * `WAIT_FOR_DATA` (`docs/ai-redesign/08-technical-architecture.md` §2, invariant #6).
 */
internal object InsufficientDataDetector {

    fun hold(ctx: CoachContext): CoachSignal {
        val logged = ctx.nutrition.loggedDaysInWindow
        val needed = CoachSignalEngine.MIN_LOGGED_DAYS
        val remaining = (needed - logged).coerceAtLeast(0)
        return CoachSignal(
            kind = SignalKind.INSUFFICIENT_DATA,
            tier = SignalTier.P3,
            category = SignalCategory.BEHAVIOR,
            severity = 0,
            facts = SignalFacts(
                mapOf(
                    "loggedDays" to logged.toString(),
                    "neededDays" to needed.toString(),
                ),
            ),
            verdict = "Keep logging — I need $needed days before I can call a trend (you're at $logged).",
            action = CoachAction(CoachActionType.OPEN_FOOD_LOG, "Log today"),
            rationale = SignalRationale(
                primaryCauseByDomain = mapOf("behavior" to "only $logged logged days so far"),
                confidence = Confidence.INSUFFICIENT,
            ),
            dedupKey = "INSUFFICIENT_DATA|logged=$logged",
            surface = CoachSurface.TODAY,
            fallbackText = "Not enough to call yet — $remaining more logged " +
                (if (remaining == 1) "day" else "days") + " and I'll have a read on your trend.",
        )
    }
}
