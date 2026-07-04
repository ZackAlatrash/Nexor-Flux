package com.zack.recomptracker.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.local.dao.UsageCount
import com.zack.recomptracker.data.local.dao.UsageEventDao
import com.zack.recomptracker.data.usage.UsageEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A single event-type's total over the window, e.g. `insight_shown → 42`. Ordered by the fixed
 * [UsageStatsViewModel.EVENT_ORDER] so the read-out screen renders a stable list.
 *
 * @param type the raw event-type string (one of [UsageEvents]).
 * @param label a human-friendly title for the type (e.g. "Insights shown").
 * @param count total occurrences in the last-7-day window.
 */
data class UsageTypeStat(
    val type: String,
    val label: String,
    val count: Int,
)

/**
 * Per-insight-card engagement: how many times a card of [cardKind] was shown vs. tapped vs.
 * dismissed. Lets the screen show "which cards you engage with vs. scroll past".
 */
data class CardEngagement(
    val cardKind: String,
    val shown: Int,
    val tapped: Int,
    val dismissed: Int,
)

/**
 * The read-out screen's full state. Everything is derived from the last-7-day (type, label) counts;
 * the screen just renders these lists — no computation needed there.
 *
 * @param byType one row per event type (all types in [UsageStatsViewModel.EVENT_ORDER], including
 *   zero-count ones, so the screen shows the full vocabulary).
 * @param cardEngagement one row per insight-card kind seen in the window, sorted most-shown first.
 * @param totalEvents grand total of all events in the window.
 * @param loading true until the first counts emission arrives.
 */
data class UsageUiState(
    val byType: List<UsageTypeStat> = emptyList(),
    val cardEngagement: List<CardEngagement> = emptyList(),
    val totalEvents: Int = 0,
    val loading: Boolean = true,
)

/**
 * Read path for the local usage-tracking read-out screen (built by a later agent). Exposes
 * last-7-day counts grouped by event type plus a per-insight-card shown/tapped/dismissed tally, and
 * a [clear] action. Pure read + one delete; all persistence is the injected [dao].
 */
class UsageStatsViewModel(
    private val dao: UsageEventDao,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val sinceEpochMs: Long = now() - SEVEN_DAYS_MS

    val uiState: StateFlow<UsageUiState> =
        dao.observeCountsSince(sinceEpochMs)
            .map { counts -> counts.toUiState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UsageUiState(loading = true),
            )

    /** Deletes all recorded usage events (privacy: clearable, local-only). Off the main thread. */
    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dao.deleteAll() }
        }
    }

    private fun List<UsageCount>.toUiState(): UsageUiState {
        val byType = EVENT_ORDER.map { type ->
            UsageTypeStat(
                type = type,
                label = TYPE_LABELS[type] ?: type,
                count = filter { it.type == type }.sumOf { it.count },
            )
        }
        // Per-card engagement: pivot the three INSIGHT_* events by their card-kind label.
        val cardKinds = filter { it.type in INSIGHT_TYPES && it.label != null }
            .mapNotNull { it.label }
            .distinct()
        val cardEngagement = cardKinds.map { kind ->
            fun countFor(type: String) =
                filter { it.type == type && it.label == kind }.sumOf { it.count }
            CardEngagement(
                cardKind = kind,
                shown = countFor(UsageEvents.INSIGHT_SHOWN),
                tapped = countFor(UsageEvents.INSIGHT_TAPPED),
                dismissed = countFor(UsageEvents.INSIGHT_DISMISSED),
            )
        }.sortedByDescending { it.shown }
        return UsageUiState(
            byType = byType,
            cardEngagement = cardEngagement,
            totalEvents = sumOf { it.count },
            loading = false,
        )
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

        private val INSIGHT_TYPES = setOf(
            UsageEvents.INSIGHT_SHOWN,
            UsageEvents.INSIGHT_TAPPED,
            UsageEvents.INSIGHT_DISMISSED,
        )

        /** Fixed display order for the by-type list (the full event vocabulary). */
        val EVENT_ORDER: List<String> = listOf(
            UsageEvents.INSIGHT_SHOWN,
            UsageEvents.INSIGHT_TAPPED,
            UsageEvents.INSIGHT_DISMISSED,
            UsageEvents.COACH_OPENED,
            UsageEvents.TODAY_SLOT_SHOWN,
            UsageEvents.TODAY_SLOT_ACTION,
            UsageEvents.WEEKLY_CHECKIN_OPENED,
            UsageEvents.TAB_VIEW,
        )

        /** Human-friendly titles for each event type. */
        val TYPE_LABELS: Map<String, String> = mapOf(
            UsageEvents.INSIGHT_SHOWN to "Insights shown",
            UsageEvents.INSIGHT_TAPPED to "Insights expanded",
            UsageEvents.INSIGHT_DISMISSED to "Insights dismissed",
            UsageEvents.COACH_OPENED to "Coach opened",
            UsageEvents.TODAY_SLOT_SHOWN to "Today's coaching shown",
            UsageEvents.TODAY_SLOT_ACTION to "Today's coaching action",
            UsageEvents.WEEKLY_CHECKIN_OPENED to "Weekly check-in opened",
            UsageEvents.TAB_VIEW to "Tab views",
        )
    }
}
