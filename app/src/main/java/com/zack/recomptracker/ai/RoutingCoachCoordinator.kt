package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Forwards [CoachCoordinator] calls to either the local (Gemma) or cloud delegate based on the
 * *effective* backend: CLOUD only when the preference is CLOUD and cloud config is complete;
 * otherwise LOCAL.
 */
@Deprecated(
    "Legacy local/cloud router — part of the on-device AI path. Isolated in Phase 0; removed in " +
        "Phase 6 when the cloud coordinators are used directly. See docs/ai-redesign/08-technical-architecture.md §5.",
)
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingCoachCoordinator(
    private val local: CoachCoordinator,
    private val cloud: CoachCoordinator,
    backendFlow: Flow<AiBackend>,
    cloudConfigCompleteFlow: Flow<Boolean>,
    scope: CoroutineScope,
) : CoachCoordinator {

    private val effectiveBackend: StateFlow<AiBackend> =
        combine(backendFlow, cloudConfigCompleteFlow) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(scope, SharingStarted.Eagerly, AiBackend.LOCAL)

    private fun activeCoordinator(): CoachCoordinator =
        if (effectiveBackend.value == AiBackend.CLOUD) cloud else local

    override val state: StateFlow<CoachState> =
        effectiveBackend
            .flatMapLatest { backend -> if (backend == AiBackend.CLOUD) cloud.state else local.state }
            .stateIn(scope, SharingStarted.Eagerly, CoachState.Unavailable)

    @Volatile private var lastRoutedCoach: CoachCoordinator? = null

    override fun sendMessage(text: String) {
        val target = activeCoordinator()
        lastRoutedCoach = target
        target.sendMessage(text)
    }

    override fun clearHistory() = activeCoordinator().clearHistory()
    override fun confirmPendingAction() = (lastRoutedCoach ?: activeCoordinator()).confirmPendingAction()
    override fun cancelPendingAction() = (lastRoutedCoach ?: activeCoordinator()).cancelPendingAction()
}
