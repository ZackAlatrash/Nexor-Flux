package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * [AiInsightCoordinator] backed by an OpenAI-compatible cloud model.
 *
 * [state] reflects readiness only: [AiInsightState.ModelReady] when AI is enabled AND a
 * [CloudConfig] is available, otherwise [AiInsightState.Disabled].
 */
class CloudInsightCoordinator(
    private val aiEnabledFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val scope: CoroutineScope,
    // Grounds the insight prompts in the shared knowledge corpus the same way the coach + briefing
    // do: a REFERENCE block is prepended to the prompt. Defaults to a no-op so tests and the missing
    // -corpus path produce unchanged prompts.
    private val knowledgeInjector: KnowledgeInjector = NoOpKnowledgeInjector,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private val promptBuilder = InsightPromptBuilder()

    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }
    private val lastInsightKeys = java.util.concurrent.ConcurrentHashMap<InsightKind, String>()

    // Caches the latest enabled emission so the configFlow collector can re-derive state
    // when config appears or disappears while the enabled flag is unchanged.
    @Volatile private var lastKnownEnabled: Boolean = false

    init {
        // React to AI-enabled toggle; configFlow.value is readable directly (it's a StateFlow).
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                lastKnownEnabled = enabled
                _state.value =
                    if (enabled && configFlow.value != null) AiInsightState.ModelReady
                    else AiInsightState.Disabled
            }
        }
        // React when config appears or disappears (e.g. user saves / clears cloud settings).
        // Uses lastKnownEnabled so state is re-derived correctly in both directions.
        scope.launch {
            configFlow.collect { config ->
                _state.value =
                    if (lastKnownEnabled && config != null) AiInsightState.ModelReady
                    else AiInsightState.Disabled
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
        val basePrompt = when (request) {
            is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context)
            is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context)
        }
        val prompt = withKnowledge(request.kind, basePrompt)
        scope.launch { streamInto(flow, prompt) }
    }

    /**
     * Prepends a knowledge REFERENCE block (retrieved for the insight kind's theme) to [basePrompt],
     * mirroring the coach/briefing grounding. When the injector finds nothing it returns "" and the
     * prompt is unchanged.
     */
    private fun withKnowledge(kind: InsightKind, basePrompt: String): String {
        val query = when (kind) {
            InsightKind.PROGRESS_TREND -> "recomposition trend weight waist lifts training frequency"
            InsightKind.RECOVERY_READINESS -> "recovery sleep soreness readiness training"
        }
        val reference = knowledgeInjector.referenceBlock(query)
        return if (reference.isBlank()) basePrompt else "$reference\n\n$basePrompt"
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private fun isModelUsable(): Boolean = _state.value != AiInsightState.Disabled

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
                val finalText = sb.toString()
                    .trim()
                    .replace(Regex("""[*_`#>]"""), "")
                    .replace(Regex("""\n{2,}"""), " ")
                    .let { InsightPromptBuilder.limitToSentences(it, 2) }
                flow.value = AiInsightState.Ready(finalText)
            }
        } catch (e: TimeoutCancellationException) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Took too long — try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Cloud request failed — check your settings.")
            }
        }
    }

    private companion object {
        // 60 s to allow for network latency.
        private const val GENERATION_TIMEOUT_MS = 60_000L
        private const val SYSTEM_PROMPT =
            "You are a precise, supportive body-recomposition coach. Answer only from the data you are given."
    }
}
