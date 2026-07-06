package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudInsightCoordinatorTest {

    private fun makeScope(ctx: kotlin.coroutines.CoroutineContext) =
        CoroutineScope(ctx + SupervisorJob())

    private fun progressRequest() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = 0.0,
            waistTrendCmPerWeek = -0.3,
            liftTrendKgPerWeek = 0.5,
            adherencePercent = 90.0,
            weightPointCount = 5,
            waistPointCount = 5,
        ),
    )

    private class FakeClient(private val chunks: List<String>) : OpenAiCompatClient() {
        /** Captures the last user prompt so tests can assert on knowledge grounding. */
        @Volatile var lastUserPrompt: String? = null
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> {
            lastUserPrompt = userPrompt
            return flow { chunks.forEach { emit(it) } }
        }
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    /** Prepends a fixed REFERENCE block for any non-blank query. */
    private class FakeInjector(private val block: String) : KnowledgeInjector {
        override fun referenceBlock(query: String): String = block
    }

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m"),
    )

    @Test
    fun `state is ModelReady when enabled and configured`() = runTest {
        val cs = makeScope(coroutineContext)
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(emptyList()),
            scope = cs,
        )
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, coordinator.state.value)
    }

    @Test
    fun `onInsightVisible streams to Ready with the accumulated text`() = runTest {
        val cs = makeScope(coroutineContext)
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(listOf("Weight held ", "while waist fell.")),
            scope = cs,
        )
        advanceUntilIdle()
        coordinator.onInsightVisible(progressRequest())
        advanceUntilIdle()
        val state = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        cs.cancel()
        assertTrue(state is AiInsightState.Ready)
        assertEquals("Weight held while waist fell.", (state as AiInsightState.Ready).text)
    }

    @Test
    fun `produces output with a NoOp injector and does not prepend a reference block`() = runTest {
        val cs = makeScope(coroutineContext)
        val client = FakeClient(listOf("Recomp on track."))
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = client,
            scope = cs,
            knowledgeInjector = NoOpKnowledgeInjector,
        )
        advanceUntilIdle()
        coordinator.onInsightVisible(progressRequest())
        advanceUntilIdle()
        val state = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        val prompt = client.lastUserPrompt
        cs.cancel()
        assertTrue(state is AiInsightState.Ready)
        assertEquals("Recomp on track.", (state as AiInsightState.Ready).text)
        assertTrue(prompt != null && "REFERENCE KNOWLEDGE" !in prompt!!)
    }

    @Test
    fun `a matching injector prepends a reference block to the prompt`() = runTest {
        val cs = makeScope(coroutineContext)
        val client = FakeClient(listOf("ok"))
        val reference = "=== REFERENCE KNOWLEDGE ===\n[1] Recomp — body recomposition basics (Source: KB)\n=== END REFERENCE ==="
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = client,
            scope = cs,
            knowledgeInjector = FakeInjector(reference),
        )
        advanceUntilIdle()
        coordinator.onInsightVisible(progressRequest())
        advanceUntilIdle()
        val prompt = client.lastUserPrompt
        cs.cancel()
        assertTrue(prompt != null)
        assertTrue(prompt!!.startsWith(reference))
        assertTrue("REFERENCE KNOWLEDGE" in prompt)
        // The base insight prompt still follows the reference block.
        assertTrue("Recomp Progress Verdict" in prompt)
    }
}
