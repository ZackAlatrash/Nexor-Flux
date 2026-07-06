package com.zack.recomptracker.ui.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.rebalance.RebalanceCoordinator
import com.zack.recomptracker.data.rebalance.RebalanceDebugScenario
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the developer-only [DeveloperScreen]. Purely a thin driver: [apply] fires the coordinator's
 * additive [RebalanceCoordinator.debugApply] (fire-and-forget), and [currentPhase] exposes a
 * human-readable label of what the rebalance card is currently showing — derived from the same
 * `store.state` + `coordinator.endedNotice` the `RebalanceViewModel` reads — so the tester can confirm a
 * scenario landed without leaving the screen.
 *
 * `internal` because the constructor holds internal collaborators; single-module, so still visible to
 * the `AppContainer` factory branch and the `viewModel<DeveloperViewModel>()` nav call site.
 */
internal class DeveloperViewModel(
    private val coordinator: RebalanceCoordinator,
    store: RebalanceStore,
) : ViewModel() {

    /** Human label of the rebalance card's current phase, for the on-screen readout. */
    val currentPhase: StateFlow<String> =
        combine(store.state, coordinator.endedNotice) { state, ended ->
            phaseLabel(state, ended)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Normal (no forced state)")

    fun apply(scenario: RebalanceDebugScenario) {
        viewModelScope.launch { coordinator.debugApply(scenario) }
    }

    private companion object {
        fun phaseLabel(state: RebalanceState, ended: RebalancePlan?): String {
            if (ended != null) {
                return when (ended.status) {
                    RebalanceStatus.COMPLETED -> "Completed note"
                    RebalanceStatus.ENDED_EARLY -> "Ended-early note"
                    else -> "Ended note"
                }
            }
            val active = state.active ?: return "Normal (no forced state)"
            return when (active.status) {
                RebalanceStatus.OFFERED -> "Offer (${active.mode})"
                RebalanceStatus.ACTIVE -> "Progress (${active.lengthDays}-day plan)"
                RebalanceStatus.NO_ADJUSTMENT -> "No-adjustment note"
                RebalanceStatus.COMPLETED,
                RebalanceStatus.ENDED_EARLY,
                RebalanceStatus.DECLINED -> "Normal (no forced state)"
            }
        }
    }
}
