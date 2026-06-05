package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import kotlinx.coroutines.flow.StateFlow

interface AiInsightCoordinator {

    val state: StateFlow<AiInsightState>

    fun requestDownload()

    fun cancelDownload()

    fun deleteModel()

    fun onAiCardVisible(result: AdjustmentResult)

    fun retryGeneration(result: AdjustmentResult)
}
