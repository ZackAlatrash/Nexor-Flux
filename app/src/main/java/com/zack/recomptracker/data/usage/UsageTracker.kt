package com.zack.recomptracker.data.usage

import com.zack.recomptracker.data.local.dao.UsageEventDao
import com.zack.recomptracker.data.local.entity.UsageEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Closed vocabulary of local usage-event types. `label` (where noted) carries the discriminator —
 * the insight-card kind, the tab name, or the today-slot card kind. Keep this list small and focused
 * on the AI surfaces + tab navigation (see the design spec); adding an event needs no schema change.
 */
object UsageEvents {
    /** An AI insight card became visible (label = card kind). Fires once per appearance. */
    const val INSIGHT_SHOWN = "insight_shown"
    /** An AI insight card was tapped/expanded (label = card kind). */
    const val INSIGHT_TAPPED = "insight_tapped"
    /** An AI insight card was dismissed (label = card kind). */
    const val INSIGHT_DISMISSED = "insight_dismissed"
    /** The coach chat screen was opened. */
    const val COACH_OPENED = "coach_opened"
    /** The "Today's Coaching" home slot rendered a real item (label = slot/card kind). */
    const val TODAY_SLOT_SHOWN = "today_slot_shown"
    /** The today slot's action was taken (label = slot/card kind). */
    const val TODAY_SLOT_ACTION = "today_slot_action"
    /** The weekly check-in / briefing was opened. */
    const val WEEKLY_CHECKIN_OPENED = "weekly_checkin_opened"
    /** A bottom-nav tab was selected (label = tab name). */
    const val TAB_VIEW = "tab_view"
    /** A concrete weekly-rebalance offer was persisted to the active slot (not a no-adjustment note). */
    const val REBALANCE_OFFERED = "rebalance_offered"
    /** The user accepted a weekly-rebalance offer. */
    const val REBALANCE_ACCEPTED = "rebalance_accepted"
    /** The user declined a weekly-rebalance offer. */
    const val REBALANCE_DECLINED = "rebalance_declined"
    /** An active weekly-rebalance plan reconciled to COMPLETED (ran its full course). */
    const val REBALANCE_COMPLETED = "rebalance_completed"
    /** An active weekly-rebalance plan ended before its course finished (unrecoverable or a base-plan edit). */
    const val REBALANCE_ENDED_EARLY = "rebalance_ended_early"
}

/**
 * Fire-and-forget usage tracking. Implementations must never block the caller and never crash the UI
 * — [track] returns immediately; persistence happens off the main thread. Fully local.
 */
interface UsageTracker {
    fun track(type: String, label: String? = null)
}

/**
 * Room-backed [UsageTracker]. Inserts on [Dispatchers.IO] via the injected app scope so the call
 * site returns instantly, and wraps the insert in `runCatching` so a DB failure can never propagate
 * to the UI (tracking is best-effort telemetry, not a feature the app depends on).
 */
class RoomUsageTracker(
    private val dao: UsageEventDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : UsageTracker {
    override fun track(type: String, label: String?) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                dao.insert(UsageEventEntity(timestampEpochMs = now(), type = type, label = label))
            }
        }
    }
}

/** No-op tracker for previews and tests — records nothing. */
object NoOpUsageTracker : UsageTracker {
    override fun track(type: String, label: String?) = Unit
}
