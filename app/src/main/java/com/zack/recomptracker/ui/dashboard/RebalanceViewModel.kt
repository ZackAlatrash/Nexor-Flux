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
import com.zack.recomptracker.domain.rebalance.RebalanceDayBar
import com.zack.recomptracker.domain.rebalance.RebalanceDefaults
import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    val intensity: RebalanceIntensity = RebalanceIntensity.STANDARD,
    val partial: Boolean = false,
    val noteKind: NoteKind? = null,
    val weeklyBars: ImmutableList<RebalanceDayBar> = persistentListOf(),
    val baseCalories: Int = 0,
) {
    enum class Face { NONE, OFFER, PROGRESS, NOTE }
}

/**
 * Which flavour of NOTE face is showing — selects the note's icon/tone in the redesign. [REASSURANCE]
 * is the small-end note (surplus below [RebalanceDefaults.SMALL_SURPLUS_KCAL]); [NO_ADJUSTMENT] doubles
 * as the resume note (a surplus too large to sensibly claw back). Both are derived from
 * `plan.surplusKcal`, not a persisted flag (spec §16).
 */
enum class NoteKind { COMPLETION, GRACEFUL_END, NO_ADJUSTMENT, REASSURANCE }

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

    /**
     * Whether the OFFER card is collapsed to its minimized peek (in-memory, never persisted — a
     * minimize is a per-session UI gesture, not a decision). Reset to `false` whenever a *new* offer
     * arrives (see [onStateChanged]); minimizing never declines the offer.
     */
    private val _offerMinimized = MutableStateFlow(false)
    val offerMinimized: StateFlow<Boolean> = _offerMinimized.asStateFlow()

    /**
     * Whether the offer copy is currently being (re)phrased by the cloud — drives the "Generating"
     * edge-glow on the OFFER card. Only meaningful for OFFER; stays `false` for every other face.
     */
    private val _phrasing = MutableStateFlow(false)
    val phrasing: StateFlow<Boolean> = _phrasing.asStateFlow()

    /** The in-flight phrasing decoration, cancelled when a new derived face supersedes it. */
    private var phrasingJob: Job? = null

    /** Guards [onShown] so [RebalanceCoordinator.runIfDue] fires at most once per ViewModel instance. */
    private var hasTriggeredRun = false

    /**
     * The plan id of the OFFER currently on screen, so [onStateChanged] resets [offerMinimized] only
     * when a genuinely *new* offer arrives — not on every re-emission (which would fight the user
     * minimizing it). `null` whenever the face is not OFFER.
     */
    private var lastOfferPlanId: String? = null

    init {
        viewModelScope.launch {
            combine(
                store.state,
                coordinator.endedNotice,
                coordinator.lastOfferWindow,
            ) { state, ended, window -> Triple(state, ended, window) }
                .collect { (state, ended, window) -> onStateChanged(state, ended, window) }
        }
    }

    private fun onStateChanged(
        state: RebalanceState,
        ended: RebalancePlan?,
        window: List<RebalanceDayBar>?,
    ) {
        if (ended != null && ended.endedReason == "plan_edited") {
            coordinator.dismissEndedNotice()
            return
        }
        phrasingJob?.cancel()
        _phrasing.value = false
        val derived = deriveFace(state, ended, window, dateProvider.today())
        if (derived == null) {
            lastOfferPlanId = null
            _uiState.value = RebalanceCardUiState()
            return
        }
        // A fresh OFFER (a new plan id) auto-expands; any other face clears the tracked id so the
        // next OFFER counts as fresh. The engine already mints a fresh UUID per offer, so a repeat
        // id is not a leak we've observed — the id compare is belt-and-braces to guarantee we never
        // re-expand (and stomp the user's minimize) on a plain re-emission of the same offer.
        if (derived.uiState.face == RebalanceCardUiState.Face.OFFER) {
            val offerId = state.active?.id
            if (offerId != lastOfferPlanId) {
                _offerMinimized.value = false
                lastOfferPlanId = offerId
            }
        } else {
            lastOfferPlanId = null
        }
        _uiState.value = derived.uiState
        val slot = derived.slot ?: return
        val facts = derived.facts ?: return
        val phrasingOffer = derived.uiState.face == RebalanceCardUiState.Face.OFFER
        if (phrasingOffer) _phrasing.value = true
        lateinit var job: Job
        job = viewModelScope.launch {
            try {
                val phrased = copyService.copy(slot, facts)
                // Only apply if this derivation is still the one on screen (guards a late return).
                if (_uiState.value == derived.uiState) {
                    _uiState.value = _uiState.value.copy(body = phrased)
                }
            } finally {
                // Clear the glow only if we're still the current job — a job superseded by a newer
                // emission must not stomp the flag the newer job just raised.
                if (phrasingOffer && phrasingJob === job) _phrasing.value = false
            }
        }
        phrasingJob = job
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

    /**
     * Change the offer's **mix** dial (spec §2). Keeps the current intensity — the two dials compose, so
     * a mode change must not silently reset how much of the surplus is being clawed back.
     */
    fun onCustomize(mode: RebalanceMode) {
        val intensity = _uiState.value.intensity
        viewModelScope.launch { coordinator.customize(mode, intensity) }
    }

    /**
     * Change the offer's **intensity** dial (spec §2). Keeps the current mode. Wired to the second dial
     * in a later UI task; delegating here keeps the offer overlay's call sites compiling now.
     */
    fun onCustomizeIntensity(intensity: RebalanceIntensity) {
        val mode = _uiState.value.mode
        viewModelScope.launch { coordinator.customize(mode, intensity) }
    }

    /** Collapse the OFFER card to its minimized peek. Pure UI — never declines the offer. */
    fun onMinimizeOffer() {
        _offerMinimized.value = true
    }

    /** Re-expand a minimized OFFER card. Pure UI — never mutates the plan. */
    fun onExpandOffer() {
        _offerMinimized.value = false
    }

    /** Cancel the current ACTIVE plan (from the progress detail). Reverts today onward to the base plan. */
    fun onCancelActive() {
        viewModelScope.launch { coordinator.cancelActive() }
    }

    private companion object {

        /** Derives the face to show, or `null` for [RebalanceCardUiState.Face.NONE]. */
        fun deriveFace(
            state: RebalanceState,
            ended: RebalancePlan?,
            window: List<RebalanceDayBar>?,
            today: LocalDate,
        ): DerivedFace? {
            if (ended != null) return noteFace(ended)

            val active = state.active ?: return null
            return when (active.status) {
                RebalanceStatus.OFFERED -> offerFace(active, window)
                RebalanceStatus.ACTIVE -> progressFace(active, state, today)
                RebalanceStatus.NO_ADJUSTMENT -> noteFace(active)
                RebalanceStatus.COMPLETED, RebalanceStatus.ENDED_EARLY, RebalanceStatus.DECLINED -> null
            }
        }

        private fun offerFace(plan: RebalancePlan, window: List<RebalanceDayBar>?): DerivedFace {
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
                intensity = plan.intensity,
                partial = plan.partial,
                baseCalories = plan.baseCalories,
                weeklyBars = window?.toImmutableList() ?: persistentListOf(),
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
                    baseCalories = plan.baseCalories,
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
                baseCalories = plan.baseCalories,
            )
            return DerivedFace(uiState, RebalanceCopySlot.PROGRESS_LINE, facts)
        }

        private fun noteFace(plan: RebalancePlan): DerivedFace {
            val (slot, noteKind) = when (plan.status) {
                // A NO_ADJUSTMENT note is either the small-end reassurance note or the big-end resume
                // note; the two share the status and are told apart by the real surplus (spec §16), so
                // the note card can pick the right copy/tone.
                RebalanceStatus.NO_ADJUSTMENT ->
                    if (plan.surplusKcal < RebalanceDefaults.SMALL_SURPLUS_KCAL) {
                        RebalanceCopySlot.REASSURANCE to NoteKind.REASSURANCE
                    } else {
                        RebalanceCopySlot.NO_ADJUSTMENT to NoteKind.NO_ADJUSTMENT
                    }
                RebalanceStatus.COMPLETED -> RebalanceCopySlot.COMPLETION to NoteKind.COMPLETION
                else -> RebalanceCopySlot.GRACEFUL_END to NoteKind.GRACEFUL_END // ENDED_EARLY ("unrecoverable")
            }
            val facts = copyFacts(plan, dayX = 0, ofY = plan.lengthDays)
            val uiState = RebalanceCardUiState(
                face = RebalanceCardUiState.Face.NOTE,
                body = RebalanceCopyPromptBuilder.fallback(slot, facts),
                mode = plan.mode,
                noteKind = noteKind,
            )
            return DerivedFace(uiState, slot, facts)
        }

        private fun effectiveCalories(plan: RebalancePlan): Int =
            RebalancePlanMath.effectiveCalories(plan)

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
