package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review P2-2: two defects in CloudInsightCoordinator's per-kind generation.
 *  1. Concurrent generations for the same kind were never cancelled, so a slower STALE generation
 *     could finish last and overwrite a newer result.
 *  2. An empty SSE stream became Ready("") (blank card) and the dedup key stayed set, so the card
 *     was stuck blank until the underlying data moved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudInsightCoordinatorRaceBlankTest {

    private fun makeScope(ctx: kotlin.coroutines.CoroutineContext) = CoroutineScope(ctx + SupervisorJob())

    private fun progressRequest(weightTrend: Double) = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = -0.3,
            liftTrendKgPerWeek = 0.5,
            adherencePercent = 90.0,
            weightPointCount = 5,
            waistPointCount = 5,
        ),
    )

    /** Serves one Flow per streamCompletion call, in order. */
    private class SequencedClient(private val streams: ArrayDeque<Flow<String>>) : OpenAiCompatClient() {
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> =
            streams.removeFirst()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m"),
    )

    @Test
    fun `a newer generation cancels the stale one so stale text cannot win`() = runTest {
        val cs = makeScope(coroutineContext)
        // gen1 is SLOW (would finish last at t=10s with "STALE."); gen2 is immediate with "FRESH.".
        val slow: Flow<String> = flow { delay(10_000); emit("STALE.") }
        val fast: Flow<String> = flow { emit("FRESH.") }
        val client = SequencedClient(ArrayDeque(listOf(slow, fast)))
        val coordinator = CloudInsightCoordinator(flowOf(true), config(), client, cs)
        advanceUntilIdle()

        coordinator.onInsightVisible(progressRequest(0.0)) // key K1 → launches gen1 (slow)
        runCurrent() // let gen1 start and park in its 10s delay (having consumed the slow stream)
        coordinator.onInsightVisible(progressRequest(1.0)) // key K2 → must cancel gen1, launch gen2
        advanceUntilIdle()

        val state = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        cs.cancel()
        assertTrue("expected the fresh result, got: $state", state is AiInsightState.Ready)
        assertEquals("FRESH.", (state as AiInsightState.Ready).text)
    }

    @Test
    fun `an empty stream becomes Error and drops the dedup key so it can regenerate`() = runTest {
        val cs = makeScope(coroutineContext)
        // 1st generation: empty stream (no chunks). 2nd: real text on the SAME request/key.
        val empty: Flow<String> = flow { }
        val real: Flow<String> = flow { emit("Recomp on track.") }
        val client = SequencedClient(ArrayDeque(listOf(empty, real)))
        val coordinator = CloudInsightCoordinator(flowOf(true), config(), client, cs)
        advanceUntilIdle()

        val req = progressRequest(0.0)
        coordinator.onInsightVisible(req)
        advanceUntilIdle()
        val afterBlank = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        assertTrue("blank stream must be an Error, not Ready(\"\"), got: $afterBlank", afterBlank is AiInsightState.Error)

        // Same key again: because the blank generation dropped the key, this must regenerate.
        coordinator.onInsightVisible(req)
        advanceUntilIdle()
        val afterRetry = coordinator.generationState(InsightKind.PROGRESS_TREND).value
        cs.cancel()
        assertTrue("re-visibility should regenerate after a blank, got: $afterRetry", afterRetry is AiInsightState.Ready)
        assertEquals("Recomp on track.", (afterRetry as AiInsightState.Ready).text)
    }
}
