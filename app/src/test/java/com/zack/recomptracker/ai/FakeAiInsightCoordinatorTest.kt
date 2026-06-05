package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAiInsightCoordinatorTest {

    private val aiEnabledFlow = MutableStateFlow(false)

    /**
     * Creates a coordinator scope that shares the test dispatcher (so advanceUntilIdle works)
     * but has its own SupervisorJob (so cancelling it doesn't affect the test scope, avoiding
     * UncompletedCoroutinesError from the long-lived collect loop).
     */
    private fun makeCoordinatorScope(parentContext: kotlin.coroutines.CoroutineContext) =
        CoroutineScope(parentContext + SupervisorJob())

    private fun holdResult() = AdjustmentResult(
        verdict = AdjustmentVerdict.HOLD,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("MAINTENANCE_TREND"),
        summary = "Stable.",
    )

    private fun waitResult() = AdjustmentResult(
        verdict = AdjustmentVerdict.WAIT_FOR_DATA,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("INSUFFICIENT_DATA"),
        summary = "Wait.",
    )

    @Test
    fun `initial state is Disabled when toggle is off`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.Disabled, c.state.value)
    }

    @Test
    fun `enabling toggle transitions to ModelMissing`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelMissing, c.state.value)
    }

    @Test
    fun `disabling toggle returns to Disabled from any state`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        aiEnabledFlow.value = false
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.Disabled, c.state.value)
    }

    @Test
    fun `requestDownload transitions through Downloading to ModelReady`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.state.value)
    }

    @Test
    fun `onAiCardVisible with WAIT_FOR_DATA does not trigger generation`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(waitResult())
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.state.value)
    }

    @Test
    fun `onAiCardVisible with real verdict transitions to Ready`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        cs.cancel()
        assertTrue("Expected Ready state", c.state.value is AiInsightState.Ready)
    }

    @Test
    fun `onAiCardVisible with same key does not re-generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        val firstText = (c.state.value as AiInsightState.Ready).text
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        val secondText = (c.state.value as AiInsightState.Ready).text
        cs.cancel()
        assertEquals(firstText, secondText)
        assertTrue(c.state.value is AiInsightState.Ready)
    }

    @Test
    fun `retryGeneration re-runs even for cached key`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = FakeAiInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val result = holdResult()
        c.onAiCardVisible(result)
        advanceUntilIdle()
        c.retryGeneration(result)
        advanceUntilIdle()
        cs.cancel()
        assertTrue("Expected Ready after retry", c.state.value is AiInsightState.Ready)
    }
}
