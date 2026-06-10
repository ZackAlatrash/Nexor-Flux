package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudInsightCoordinatorTest {

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
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> =
            flow { chunks.forEach { emit(it) } }
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m"),
    )

    @Test
    fun `state is ModelReady when enabled and configured`() = runTest {
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(emptyList()),
            scope = backgroundScope,
        )
        advanceUntilIdle()
        assertEquals(AiInsightState.ModelReady, coordinator.state.value)
    }

    @Test
    fun `onInsightVisible streams to Ready with the accumulated text`() = runTest {
        val coordinator = CloudInsightCoordinator(
            aiEnabledFlow = flowOf(true),
            configFlow = config(),
            client = FakeClient(listOf("Weight held ", "while waist fell.")),
            scope = backgroundScope,
        )
        advanceUntilIdle()
        coordinator.onInsightVisible(progressRequest())
        advanceUntilIdle()
        val state = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        assertTrue(state is AiInsightState.Ready)
        assertEquals("Weight held while waist fell.", (state as AiInsightState.Ready).text)
    }
}
