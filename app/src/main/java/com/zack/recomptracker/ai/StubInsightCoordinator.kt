package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StubInsightCoordinator(
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }

    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                _state.value = if (enabled) AiInsightState.ModelReady else AiInsightState.Disabled
            }
        }
    }

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        insightStates.getValue(kind).asStateFlow()

    override fun onInsightVisible(request: InsightRequest) {
        if (!request.hasSufficientData) return
        val flow = insightStates.getValue(request.kind)
        if (!isModelUsable()) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        scope.launch {
            flow.value = AiInsightState.LoadingModel
            delay(50L)
            if (flow.value !is AiInsightState.LoadingModel) return@launch
            val text = stubInsightText(request)
            flow.value = AiInsightState.Generating(text)
            flow.value = AiInsightState.Ready(text)
        }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private fun isModelUsable(): Boolean = _state.value != AiInsightState.Disabled

    private fun stubInsightText(request: InsightRequest): String = when (request) {
        is InsightRequest.ProgressTrend -> "Your trends look stable this period."
        is InsightRequest.RecoveryReadiness -> "Your recovery looks on track today."
    }
}
