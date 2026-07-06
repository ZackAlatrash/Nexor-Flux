package com.zack.recomptracker.ai

import kotlinx.coroutines.flow.StateFlow

interface AiInsightCoordinator {
    val state: StateFlow<AiInsightState>
    /**
     * Per-kind generation state for the expanded insights. When AI is not usable (disabled),
     * [onInsightVisible] sets the kind's flow to the current state and does NOT auto-recover when AI
     * later becomes ready — the UI must re-invoke [onInsightVisible] on state changes, or call
     * [retryInsight].
     */
    fun generationState(kind: InsightKind): StateFlow<AiInsightState>
    fun onInsightVisible(request: InsightRequest)
    fun retryInsight(request: InsightRequest)
}
