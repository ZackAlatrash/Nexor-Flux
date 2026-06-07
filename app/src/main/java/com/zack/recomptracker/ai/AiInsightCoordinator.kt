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
}
