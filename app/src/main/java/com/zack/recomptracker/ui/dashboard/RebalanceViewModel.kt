package com.zack.recomptracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.RebalanceCopyFacts
import com.zack.recomptracker.ai.RebalanceCopyPromptBuilder
import com.zack.recomptracker.ai.RebalanceCopyService
import com.zack.recomptracker.ai.RebalanceCopySlot
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.rebalance.RebalanceCoordinator
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceDefaults
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * UI state for the Weekly Rebalance dashboard card. [face] == [Face.NONE] means the card renders
 * nothing — mirrors [CoachTodayUiState]'s "silent when empty" contract. All fields beyond [face] are
 * populated only for the face(s) that use them; unused fields keep their defaults.
 *
 * See `docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` §3 (UX) and §8 (copy slots).
 */
data class RebalanceCardUiState(
    val face: Face = Face.NONE,
    val headline: String = "",
    val body: String = "",
    val dayX: Int = 0,
    val ofY: Int = 0,
    val progressFraction: Float = 0f,
    val effectiveCalories: Int = 0,
    val extraSteps: Int = 0,
    val mode: RebalanceMode = RebalanceMode.BALANCED,
) {
    enum class Face { NONE, OFFER, PROGRESS, NOTE }
}

/** Supportive copy for the one case with no §8 slot: accepted late, plan starts tomorrow (spec §10). */
private const val STARTS_TOMORROW_LINE = "Your rebalance starts tomorrow — today stays your normal plan."

/**
 * A derived face plus the (optional) phrasing job to run for its body text — the ViewModel's internal
 * working representation, one step richer than the [RebalanceCardUiState] shown to the composable. Some
 * faces have nothing to phrase (the starts-tomorrow line is hardcoded, not a §8 slot), so [slot] is
 * nullable.
 */
private data class DerivedFace(
    val uiState: RebalanceCardUiState,
    val slot: RebalanceCopySlot?,
    val facts: RebalanceCopyFacts?,
)

/**
 * Drives the Weekly Rebalance dashboard card (spec §3, §7, §8). The deterministic
 * [com.zack.recomptracker.domain.rebalance.RebalanceEngine] (via [coordinator]/[store]) has already
 * decided every number and every state transition; this ViewModel only *derives which face to show*
 * from the current [RebalanceState] + [RebalanceCoordinator.endedNotice], and *phrases* the copy —
 * same division of labor as [CoachTodayViewModel].
 *
 * Face derivation (see [deriveFace]):
 *  - A "plan_edited" ended notice is auto-dismissed and never shown (the user just edited their plan
 *    on purpose; no note is due).
 *  - Any other ended notice (completion / graceful early-end) → [RebalanceCardUiState.Face.NOTE].
 *  - An OFFERED active plan → [RebalanceCardUiState.Face.OFFER].
 *  - An ACTIVE plan covering today → [RebalanceCardUiState.Face.PROGRESS] with day-X-of-Y math from
 *    [EffectiveTargets].
 *  - An ACTIVE plan accepted too late in the day to have started yet (today < start) → PROGRESS with
 *    `dayX == 0` and the hardcoded [STARTS_TOMORROW_LINE] (not a §8 copy slot).
 *  - A NO_ADJUSTMENT active plan → NOTE.
 *  - Otherwise → NONE.
 *
 * Copy: the deterministic fallback ([RebalanceCopyPromptBuilder.fallback]) is seeded synchronously
 * the instant a face is derived (never blank), then one job swaps in the cloud-phrased version via
 * [copyService] — cancelled and relaunched on every face/plan change, mirroring
 * [CoachTodayViewModel.onSignal]'s phrasing-job pattern.
 *
 * @param store the persisted [RebalanceState] stream.
 * @param coordinator the transition surface (accept/decline/dismiss/customize) + the in-memory
 *   [RebalanceCoordinator.endedNotice].
 * @param copyService the cloud phrasing decoration; guaranteed to return the fallback on any failure.
 * @param dateProvider used only to resolve "today" for [EffectiveTargets.planDayInfo].
 *
 * `internal` (unlike the public [CoachTodayViewModel]) because the constructor holds the `internal`
 * [RebalanceCopyService] — single-module app, so `internal` is still visible to the `AppContainer`
 * factory branch and the `viewModel<RebalanceViewModel>()` nav call site.
 */
internal class RebalanceViewModel(
    private val store: RebalanceStore,
    private val coordinator: RebalanceCoordinator,
    private val copyService: RebalanceCopyService,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RebalanceCardUiState())
    val uiState: StateFlow<RebalanceCardUiState> = _uiState.asStateFlow()

    /** The in-flight phrasing decoration, cancelled when a new derived face supersedes it. */
    private var phrasingJob: Job? = null

    /** Guards [onShown] so [RebalanceCoordinator.runIfDue] fires at most once per ViewModel instance. */
    private var hasTriggeredRun = false

    init {
        viewModelScope.launch {
            combine(store.state, coordinator.endedNotice) { state, ended -> state to ended }
                .collect { (state, ended) -> onStateChanged(state, ended) }
        }
    }

    private fun onStateChanged(state: RebalanceState, ended: RebalancePlan?) {
        if (ended != null && ended.endedReason == "plan_edited") {
            coordinator.dismissEndedNotice()
            return
        }
        phrasingJob?.cancel()
        val derived = deriveFace(state, ended, dateProvider.today())
        if (derived == null) {
            _uiState.value = RebalanceCardUiState()
            return
        }
        _uiState.value = derived.uiState
        val slot = derived.slot ?: return
        val facts = derived.facts ?: return
        phrasingJob = viewModelScope.launch {
            val phrased = copyService.copy(slot, facts)
            // Only apply if this derivation is still the one on screen (guards a late return).
            if (_uiState.value == derived.uiState) {
                _uiState.value = _uiState.value.copy(body = phrased)
            }
        }
    }

    /** Call when the card becomes visible — fires the once-daily coordinator run (fire-and-forget). */
    fun onShown() {
        if (hasTriggeredRun) return
        hasTriggeredRun = true
        viewModelScope.launch { coordinator.runIfDue() }
    }

    fun onAccept() {
        viewModelScope.launch { coordinator.accept() }
    }

    fun onDecline() {
        viewModelScope.launch { coordinator.decline() }
    }

    /** Dismisses whichever end-state is currently showing — an ended notice or a NO_ADJUSTMENT note. */
    fun onDismiss() {
        viewModelScope.launch {
            if (coordinator.endedNotice.value != null) {
                coordinator.dismissEndedNotice()
            } else {
                coordinator.dismissNote()
            }
        }
    }

    fun onCustomize(mode: RebalanceMode) {
        viewModelScope.launch { coordinator.customize(mode) }
    }

    private companion object {

        /** Derives the face to show, or `null` for [RebalanceCardUiState.Face.NONE]. */
        fun deriveFace(state: RebalanceState, ended: RebalancePlan?, today: LocalDate): DerivedFace? {
            if (ended != null) return noteFace(ended)

            val active = state.active ?: return null
            return when (active.status) {
                RebalanceStatus.OFFERED -> offerFace(active)
                RebalanceStatus.ACTIVE -> progressFace(active, state, today)
                RebalanceStatus.NO_ADJUSTMENT -> noteFace(active)
                RebalanceStatus.COMPLETED, RebalanceStatus.ENDED_EARLY, RebalanceStatus.DECLINED -> null
            }
        }

        private fun offerFace(plan: RebalancePlan): DerivedFace {
            val facts = copyFacts(plan, dayX = 0, ofY = plan.lengthDays)
            val uiState = RebalanceCardUiState(
                face = RebalanceCardUiState.Face.OFFER,
                headline = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_HEADLINE, facts),
                body = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts),
                dayX = 0,
                ofY = plan.lengthDays,
                effectiveCalories = effectiveCalories(plan),
                extraSteps = plan.extraDailySteps,
                mode = plan.mode,
            )
            return DerivedFace(uiState, RebalanceCopySlot.OFFER_BODY, facts)
        }

        private fun progressFace(plan: RebalancePlan, state: RebalanceState, today: LocalDate): DerivedFace {
            val dayInfo = EffectiveTargets.planDayInfo(today, state)
            val effCalories = effectiveCalories(plan)
            if (dayInfo == null) {
                // Accepted late in the day: today < startDate. Day 1 = tomorrow; today keeps the base
                // target, no chip (spec §10). Not a §8 copy slot — a hardcoded supportive line, so
                // there is nothing to phrase.
                val uiState = RebalanceCardUiState(
                    face = RebalanceCardUiState.Face.PROGRESS,
                    body = STARTS_TOMORROW_LINE,
                    dayX = 0,
                    ofY = plan.lengthDays,
                    effectiveCalories = effCalories,
                    extraSteps = plan.extraDailySteps,
                    mode = plan.mode,
                )
                return DerivedFace(uiState, slot = null, facts = null)
            }
            val facts = copyFacts(plan, dayX = dayInfo.dayX, ofY = dayInfo.ofY)
            val uiState = RebalanceCardUiState(
                face = RebalanceCardUiState.Face.PROGRESS,
                body = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.PROGRESS_LINE, facts),
                dayX = dayInfo.dayX,
                ofY = dayInfo.ofY,
                progressFraction = dayInfo.dayX.toFloat() / dayInfo.ofY.toFloat(),
                effectiveCalories = effCalories,
                extraSteps = plan.extraDailySteps,
                mode = plan.mode,
            )
            return DerivedFace(uiState, RebalanceCopySlot.PROGRESS_LINE, facts)
        }

        private fun noteFace(plan: RebalancePlan): DerivedFace {
            val slot = when {
                plan.status == RebalanceStatus.NO_ADJUSTMENT -> RebalanceCopySlot.NO_ADJUSTMENT
                plan.status == RebalanceStatus.COMPLETED -> RebalanceCopySlot.COMPLETION
                else -> RebalanceCopySlot.GRACEFUL_END // ENDED_EARLY ("unrecoverable")
            }
            val facts = copyFacts(plan, dayX = 0, ofY = plan.lengthDays)
            val uiState = RebalanceCardUiState(
                face = RebalanceCardUiState.Face.NOTE,
                body = RebalanceCopyPromptBuilder.fallback(slot, facts),
                mode = plan.mode,
            )
            return DerivedFace(uiState, slot, facts)
        }

        private fun effectiveCalories(plan: RebalancePlan): Int =
            (plan.baseCalories - plan.dailyCalorieReduction).coerceAtLeast(RebalanceDefaults.MIN_EFFECTIVE_CAL)

        private fun copyFacts(plan: RebalancePlan, dayX: Int, ofY: Int) = RebalanceCopyFacts(
            lengthDays = plan.lengthDays,
            dailyCalorieReduction = plan.dailyCalorieReduction,
            extraDailySteps = plan.extraDailySteps,
            effectiveCalories = effectiveCalories(plan),
            dayX = dayX,
            ofY = ofY,
        )
    }
}
