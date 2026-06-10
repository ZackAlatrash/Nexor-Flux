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
 * Forwards [AiInsightCoordinator] calls to the local (Gemma) or cloud delegate based on the
 * *effective* backend (CLOUD only when selected AND cloud config is complete).
 *
 * Model-lifecycle calls ([requestDownload], [cancelDownload], [deleteModel], [setSelectedModel])
 * always target the LOCAL delegate, since those manage the on-device Gemma model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingInsightCoordinator(
    private val local: AiInsightCoordinator,
    private val cloud: AiInsightCoordinator,
    backendFlow: Flow<AiBackend>,
    cloudConfigCompleteFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val effectiveBackend: StateFlow<AiBackend> =
        combine(backendFlow, cloudConfigCompleteFlow) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(scope, SharingStarted.Eagerly, AiBackend.LOCAL)

    private fun active(): AiInsightCoordinator =
        if (effectiveBackend.value == AiBackend.CLOUD) cloud else local

    override val state: StateFlow<AiInsightState> =
        effectiveBackend
            .flatMapLatest { backend -> if (backend == AiBackend.CLOUD) cloud.state else local.state }
            .stateIn(scope, SharingStarted.Eagerly, AiInsightState.Disabled)

    // Model-lifecycle operations always target local (on-device Gemma management).
    override val selectedModel: StateFlow<ModelVariant> get() = local.selectedModel
    override fun setSelectedModel(variant: ModelVariant) = local.setSelectedModel(variant)
    override fun requestDownload() = local.requestDownload()
    override fun cancelDownload() = local.cancelDownload()
    override fun deleteModel() = local.deleteModel()

    // Insight generation routes to whichever backend is currently effective.
    override fun onAiCardVisible(context: InsightContext) = active().onAiCardVisible(context)
    override fun retryGeneration(context: InsightContext) = active().retryGeneration(context)

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        effectiveBackend
            .flatMapLatest { backend ->
                if (backend == AiBackend.CLOUD) cloud.generationState(kind) else local.generationState(kind)
            }
            .stateIn(scope, SharingStarted.Eagerly, AiInsightState.ModelReady)

    override fun onInsightVisible(request: InsightRequest) = active().onInsightVisible(request)
    override fun retryInsight(request: InsightRequest) = active().retryInsight(request)
}
