package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
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

    private val _selectedModel = MutableStateFlow(ModelVariant.GEMMA_2B)
    override val selectedModel: StateFlow<ModelVariant> = _selectedModel.asStateFlow()

    override fun setSelectedModel(variant: ModelVariant) { _selectedModel.value = variant }

    private var lastGeneratedKey: String? = null

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }

    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    _state.value = AiInsightState.ModelMissing
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing && _state.value != AiInsightState.DownloadFailed) return
        scope.launch {
            for (i in 1..5) { _state.value = AiInsightState.Downloading(i / 5f); delay(60L) }
            _state.value = AiInsightState.ModelReady
        }
    }

    override fun cancelDownload() { _state.value = AiInsightState.ModelMissing }

    override fun deleteModel() { lastGeneratedKey = null; _state.value = AiInsightState.ModelMissing }

    override fun onAiCardVisible(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = context.result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(context) }
    }

    override fun retryGeneration(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady && _state.value !is AiInsightState.Error) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(context)
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

    private fun isModelUsable(): Boolean = when (_state.value) {
        AiInsightState.Disabled,
        AiInsightState.ModelMissing,
        is AiInsightState.Downloading,
        AiInsightState.DownloadFailed,
        AiInsightState.ModelVerifying -> false
        else -> true
    }

    private fun stubInsightText(request: InsightRequest): String = when (request) {
        is InsightRequest.ProgressTrend -> "Your trends look stable this period."
        is InsightRequest.RecoveryReadiness -> "Your recovery looks on track today."
        is InsightRequest.RestOfDay -> "You're tracking well for the day."
    }

    private suspend fun generate(context: InsightContext) {
        _state.value = AiInsightState.LoadingModel
        delay(300L)
        if (_state.value !is AiInsightState.LoadingModel) return
        val explanation = buildExplanation(context)
        val words = explanation.split(" ")
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(word)
            _state.value = AiInsightState.Generating(sb.toString())
            delay(60L)
        }
        if (_state.value is AiInsightState.Generating) {
            _state.value = AiInsightState.Ready(sb.toString())
        }
    }

    private fun buildExplanation(context: InsightContext): String =
        when (context.result.reasonCodes.firstOrNull()) {
            "INSUFFICIENT_DATA" ->
                "You need at least 14 logged days before a verdict can be made. " +
                "Keep logging consistently to unlock your first calorie recommendation."
            "LOW_ADHERENCE" ->
                "Logging consistency has been below the threshold this period. " +
                "Improve tracking accuracy before changing your calorie target."
            "EARLY_SCALE_JUMP" ->
                "Your weight jumped in the first week, which often reflects water or glycogen shifts. " +
                "Holding calories while monitoring waist gives a clearer picture."
            "LOSING_WITH_POOR_RECOVERY" ->
                "Weight is trending down while performance or recovery is suffering. " +
                "Adding calories will support muscle maintenance and training quality."
            "GAINING_WITH_WAIST_INCREASE" ->
                "Both weight and waist are trending upward, pointing to fat accumulation. " +
                "A small calorie reduction will help redirect the trend without a harsh cut."
            "MAINTENANCE_TREND" ->
                "Weight, waist, and performance are all stable this period. " +
                "Your current intake is working — no adjustment is needed this week."
            "WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP" ->
                "Weight is rising but waist is stable and performance is improving. " +
                "This pattern points to lean mass gains, not fat accumulation."
            "NO_CLEAR_CHANGE_SIGNAL" ->
                "No strong signal emerged this week to justify a calorie change. " +
                "Staying at current intake gives you another review period of data."
            else -> context.result.summary
        }
}
