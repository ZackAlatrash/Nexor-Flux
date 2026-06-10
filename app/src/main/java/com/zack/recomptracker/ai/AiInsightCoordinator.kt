package com.zack.recomptracker.ai

import kotlinx.coroutines.flow.StateFlow

interface AiInsightCoordinator {
    val state: StateFlow<AiInsightState>
    val selectedModel: StateFlow<ModelVariant>
    fun setSelectedModel(variant: ModelVariant)
    fun requestDownload()
    fun cancelDownload()
    fun deleteModel()
    fun onAiCardVisible(context: InsightContext)
    fun retryGeneration(context: InsightContext)
    /**
     * Per-kind generation state for the expanded insights. When the model is not yet
     * usable (downloading, missing, disabled), [onInsightVisible] sets the kind's flow to the
     * current model-lifecycle state and does NOT auto-recover when the model later becomes
     * ready — the UI must re-invoke [onInsightVisible] on lifecycle/state changes (the same
     * contract the weekly [onAiCardVisible] card already relies on), or call [retryInsight].
     */
    fun generationState(kind: InsightKind): StateFlow<AiInsightState>
    fun onInsightVisible(request: InsightRequest)
    fun retryInsight(request: InsightRequest)
}
