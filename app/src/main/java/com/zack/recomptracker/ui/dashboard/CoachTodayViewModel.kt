package com.zack.recomptracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.time.SystemDateProvider
import com.zack.recomptracker.data.coach.CoachExperiment
import com.zack.recomptracker.data.coach.CoachExperiments
import com.zack.recomptracker.data.coach.CoachInbox
import com.zack.recomptracker.data.coach.FakeCoachExperiments
import com.zack.recomptracker.domain.coach.CoachSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the "Today's Coaching" home slot. [signal] `null` means the slot renders nothing
 * (§9 "silent if nothing clears the bar"). [displayText] is what to show right now — seeded with the
 * engine's number-safe [CoachSignal.fallbackText] the instant a signal arrives, then swapped for the
 * cloud-phrased version once it returns. [phrasing] is `true` while the decoration call is in flight.
 */
data class CoachTodayUiState(
    val signal: CoachSignal? = null,
    val displayText: String = "",
    val phrasing: Boolean = false,
)

/**
 * Drives the single Today's Coaching slot. The deterministic engine already decided *what* to say and
 * computed every number; this ViewModel only *displays* the staged winner and *phrases* it. See
 * `docs/ai-redesign/08-technical-architecture.md` §1 (phrase-on-open, fallback), §9 (one item, silent
 * when empty), §10 (inbox as `Flow<CoachSignal?>`).
 *
 * Behaviour:
 *  - Collects [CoachInbox.current]. `null` → [CoachTodayUiState.signal] is `null` (slot hidden).
 *  - On a non-null signal: render [CoachSignal.fallbackText] immediately (never blank), then call
 *    [phrase] as a decoration and swap in the result. On failure the service already returns the
 *    fallback, so the text simply stays the fallback.
 *  - [onShown] pokes the digest ([onVisibleRefresh]) to refresh the winner ≤once/day.
 *  - [dismiss] clears the inbox (auto-dismiss on action/seen, §9).
 *
 * @param inbox the fakeable read/clear surface (`CoachInboxRepository` in production).
 * @param phrase suspend decoration turning a signal into 1–2 sentences; guaranteed to return the
 *   fallback on any failure. Injected as a plain lambda so tests need no cloud client.
 * @param onVisibleRefresh fired on [onShown] — `CoachDigestCoordinator::runIfDue` in production.
 */
class CoachTodayViewModel(
    private val inbox: CoachInbox,
    private val phrase: suspend (CoachSignal) -> String,
    private val onVisibleRefresh: () -> Unit,
    // Phase 5: defaults keep pre-Phase-5 callers/tests compiling; production wires the real store.
    private val experiments: CoachExperiments = FakeCoachExperiments(),
    private val dateProvider: DateProvider = SystemDateProvider(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachTodayUiState())
    val uiState: StateFlow<CoachTodayUiState> = _uiState.asStateFlow()

    /** The in-flight phrasing decoration, cancelled when a new signal (or `null`) supersedes it. */
    private var phrasingJob: Job? = null

    init {
        viewModelScope.launch {
            inbox.current.collect { signal -> onSignal(signal) }
        }
    }

    private fun onSignal(signal: CoachSignal?) {
        phrasingJob?.cancel()
        if (signal == null) {
            _uiState.value = CoachTodayUiState()
            return
        }
        // Render the engine's number-safe fallback immediately (never blank), then phrase.
        _uiState.value = CoachTodayUiState(
            signal = signal,
            displayText = signal.fallbackText,
            phrasing = true,
        )
        phrasingJob = viewModelScope.launch {
            val phrased = phrase(signal)
            // Only apply if this signal is still the one on screen (guards a late return).
            if (_uiState.value.signal == signal) {
                _uiState.value = _uiState.value.copy(displayText = phrased, phrasing = false)
            }
        }
    }

    /** Call when the slot becomes visible — refreshes the staged winner (once/day debounce inside). */
    fun onShown() = onVisibleRefresh()

    /** User dismissed the slot — clear the staged signal. */
    fun dismiss() {
        viewModelScope.launch { inbox.dismissCurrent() }
    }

    /**
     * Phase 5 "Track this": records the Cross-Signal Discovery card's hypothesis as the single active
     * experiment (overwriting any existing) and dismisses the slot (reusing the existing dismiss path).
     * The experiment seed lives in the signal's engine-computed facts (correlation id, tracked metric,
     * baseline) — no number is invented here. A no-op for a signal missing those facts.
     */
    fun onTrackExperiment(signal: CoachSignal) {
        val facts = signal.facts.values
        val correlationId = facts["correlationId"] ?: return
        val trackedMetric = facts["trackedMetric"] ?: return
        val baseline = facts["baselineValue"]?.toDoubleOrNull() ?: return
        viewModelScope.launch {
            experiments.start(
                CoachExperiment(
                    correlationId = correlationId,
                    hypothesis = signal.verdict,
                    trackedMetric = trackedMetric,
                    baselineValue = baseline,
                    startDateIso = dateProvider.today().toString(),
                ),
            )
            inbox.dismissCurrent()
        }
    }
}
