package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
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
class StubInsightCoordinatorTest {

    private val aiEnabledFlow = MutableStateFlow(false)

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

    private fun baseInput() = AdjustmentInput(
        daysLogged = 14,
        adherencePercent = 91.0,
        weeksSincePhaseStart = 3,
        weightTrendKgPerWeek = 0.0,
        waistTrendCmPerWeek = 0.0,
        performanceTrend = PerformanceTrend.STABLE,
        recoveryTrend = RecoveryTrend.OK,
    )

    private fun holdContext() = InsightContext(
        result = holdResult(),
        input = baseInput(),
        targetCalories = 2550,
        targetProteinG = 165,
    )

    private fun waitContext() = InsightContext(
        result = waitResult(),
        input = baseInput(),
        targetCalories = 2550,
        targetProteinG = 165,
    )

    @Test
    fun `initial state is Disabled when toggle is off`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.Disabled, c.state.value)
    }

    @Test
    fun `enabling toggle transitions to ModelMissing`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
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
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
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
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
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
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(waitContext())
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.state.value)
    }

    @Test
    fun `onAiCardVisible with real verdict transitions to Ready`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(holdContext())
        advanceUntilIdle()
        cs.cancel()
        assertTrue("Expected Ready state", c.state.value is AiInsightState.Ready)
    }

    @Test
    fun `onAiCardVisible with same key does not re-generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val ctx = holdContext()
        c.onAiCardVisible(ctx)
        advanceUntilIdle()
        val firstText = (c.state.value as AiInsightState.Ready).text
        c.onAiCardVisible(ctx)
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
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val ctx = holdContext()
        c.onAiCardVisible(ctx)
        advanceUntilIdle()
        c.retryGeneration(ctx)
        advanceUntilIdle()
        cs.cancel()
        assertTrue("Expected Ready after retry", c.state.value is AiInsightState.Ready)
    }
}
