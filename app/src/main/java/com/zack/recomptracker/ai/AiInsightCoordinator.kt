package com.zack.recomptracker.ai

import kotlinx.coroutines.flow.StateFlow

interface AiInsightCoordinator {
    val state: StateFlow<AiInsightState>
    fun requestDownload()
    fun cancelDownload()
    fun deleteModel()
    fun onAiCardVisible(context: InsightContext)
    fun retryGeneration(context: InsightContext)
}
