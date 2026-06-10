package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [AiInsightCoordinator] backed by an OpenAI-compatible cloud model.
 *
 * Cloud has no model download/verify lifecycle, so [requestDownload], [cancelDownload],
 * [deleteModel], and [setSelectedModel] are inert. [state] reflects readiness only:
 * [AiInsightState.ModelReady] when AI is enabled AND a [CloudConfig] is available,
 * otherwise [AiInsightState.Disabled].
 */
class CloudInsightCoordinator(
    private val aiEnabledFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private val _selectedModel = MutableStateFlow(ModelVariant.GEMMA_2B)
    override val selectedModel: StateFlow<ModelVariant> = _selectedModel.asStateFlow()

    private val promptBuilder = InsightPromptBuilder()
    private val capabilities = AiCapabilities.of(AiBackend.CLOUD)

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }
    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    private var lastGeneratedKey: String? = null

    init {
        // UNDISPATCHED: executes synchronously until the first real suspension, so
        // the initial emission from the enabled flow sets _state before init returns.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            aiEnabledFlow.collect { enabled ->
                _state.value =
                    if (enabled && configFlow.value != null) AiInsightState.ModelReady
                    else AiInsightState.Disabled
            }
        }
        // Also react when config disappears (user clears settings).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            configFlow
                .map { it == null }
                .distinctUntilChanged()
                .collect { configGone ->
                    if (configGone) _state.value = AiInsightState.Disabled
                }
        }
    }

    // Cloud has no on-device model lifecycle — these are intentionally inert.
    override fun setSelectedModel(variant: ModelVariant) { _selectedModel.value = variant }
    override fun requestDownload() {}
    override fun cancelDownload() {}
    override fun deleteModel() {}

    override fun onAiCardVisible(context: InsightContext) {
        if (context.result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = context.result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch(start = CoroutineStart.UNDISPATCHED) { streamInto(_state, promptBuilder.buildWeeklySummaryPrompt(context)) }
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
        if (_state.value != AiInsightState.ModelReady) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        val prompt = when (request) {
            is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context, rich = capabilities.richInsights)
            is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context, rich = capabilities.richInsights)
            is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context, rich = capabilities.richInsights)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { streamInto(flow, prompt) }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private suspend fun streamInto(flow: MutableStateFlow<AiInsightState>, prompt: String) {
        val config = configFlow.value ?: run {
            flow.value = AiInsightState.Error("Cloud AI not configured.")
            return
        }
        flow.value = AiInsightState.LoadingModel
        try {
            flow.value = AiInsightState.Generating("")
            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                client.streamCompletion(
                    config = config,
                    systemPrompt = SYSTEM_PROMPT,
                    userPrompt = prompt,
                ).collect { chunk ->
                    sb.append(chunk)
                    flow.value = AiInsightState.Generating(sb.toString())
                }
            }
            if (flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Ready(sb.toString().trim())
            }
        } catch (e: TimeoutCancellationException) {
            flow.value = AiInsightState.Error("Took too long — try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flow.value = AiInsightState.Error("Cloud request failed — check your settings.")
        }
    }

    private companion object {
        private const val GENERATION_TIMEOUT_MS = 60_000L
        private const val SYSTEM_PROMPT =
            "You are a precise, supportive body-recomposition coach. Answer only from the data you are given."
    }
}
