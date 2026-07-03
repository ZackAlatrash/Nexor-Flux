package com.zack.recomptracker.data.coach

import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal

/** The two notification channels, per `docs/ai-redesign/08-technical-architecture.md` §11. */
enum class CoachPushChannel(val channelId: String) {
    /** Low importance — ambient P0 nudges + celebrations. */
    COACHING("coaching"),

    /** Default importance — the spine's one weekly check-in push. */
    WEEKLY_CHECK_IN("weekly_check_in"),
}

/**
 * A fully-decided, ready-to-display push. Built from a [CoachSignal] by [from], which enforces the
 * **decision-attached gate** (§11): a push must carry a non-blank [title]/[body] (the signal's verdict —
 * "never a number without the 'so do X'") AND an actionable deep-link [target]. [from] returns `null`
 * when either is missing, so the notifier can never show an orphaned score.
 *
 * Pure: no Android types. The Android [CoachNotifier] impl turns [target] into a tap `PendingIntent`.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
data class CoachPushPayload(
    val channel: CoachPushChannel,
    val title: String,
    val body: String,
    /** The deep-link the tap opens. Never [CoachActionType.NONE] (the gate rejects that). */
    val target: CoachActionType,
) {
    companion object {
        /**
         * Build a payload from [signal] for [channel], or `null` if the decision-attached gate fails:
         * the signal must have a non-blank verdict (the title) and fallback text (the body), and an
         * actionable destination ([CoachAction] whose type is not [CoachActionType.NONE]). Informational
         * signals never become a push.
         */
        fun from(signal: CoachSignal, channel: CoachPushChannel): CoachPushPayload? {
            val target = signal.action.type
            if (target == CoachActionType.NONE) return null
            if (signal.verdict.isBlank()) return null
            if (signal.fallbackText.isBlank()) return null
            return CoachPushPayload(
                channel = channel,
                title = signal.verdict.trim(),
                body = signal.fallbackText.trim(),
                target = target,
            )
        }
    }
}

/**
 * The Android-framework seam for the coach's push layer. The interface is intentionally tiny and
 * Android-free so [CoachPushEmitter] can be TDD'd against a fake; the concrete
 * [AndroidCoachNotifier] owns all `NotificationManager`/channel/`PendingIntent` mechanics.
 *
 * [show] returns whether a notification was actually posted — it double-checks the decision-attached
 * gate is satisfied by the payload (the payload factory already guarantees this) and reports `false`
 * when the platform refuses (e.g. notifications disabled / permission denied) so the emitter records a
 * [com.zack.recomptracker.domain.coach.PushEvent] only for pushes that truly went out.
 */
interface CoachNotifier {
    /** Idempotently create the two notification channels. Safe to call repeatedly. */
    fun ensureChannels()

    /** Post [payload] as a notification; returns `true` iff a notification was actually shown. */
    fun show(payload: CoachPushPayload): Boolean
}

/** Inert [CoachNotifier] for tests / Context-free construction: shows nothing. */
object NoopCoachNotifier : CoachNotifier {
    override fun ensureChannels() = Unit
    override fun show(payload: CoachPushPayload): Boolean = false
}
