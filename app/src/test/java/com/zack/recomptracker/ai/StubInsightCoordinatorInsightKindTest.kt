package com.zack.recomptracker.ai

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
class StubInsightCoordinatorInsightKindTest {

    private val aiEnabledFlow = MutableStateFlow(false)

    private fun makeScope(parent: kotlin.coroutines.CoroutineContext) =
        CoroutineScope(parent + SupervisorJob())

    private fun sufficientProgress() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = -0.2,
            waistTrendCmPerWeek = -0.3,
            liftTrendKgPerWeek = 0.5,
            adherencePercent = 90.0,
            weightPointCount = 10,
            waistPointCount = 10,
        ),
    )

    private fun insufficientProgress() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = null,
            waistTrendCmPerWeek = null,
            liftTrendKgPerWeek = null,
            adherencePercent = null,
            weightPointCount = 1,
            waistPointCount = 0,
        ),
    )

    private fun ready(scope: CoroutineScope): StubInsightCoordinator {
        val c = StubInsightCoordinator(aiEnabledFlow, scope)
        aiEnabledFlow.value = true
        return c
    }

    @Test
    fun `insufficient data does not generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(insufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.generationState(InsightKind.PROGRESS_TREND).value)
    }

    @Test
    fun `sufficient data transitions to Ready`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(sufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
    }

    @Test
    fun `same key does not re-generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val req = sufficientProgress()
        c.onInsightVisible(req)
        advanceUntilIdle()
        val first = (c.generationState(InsightKind.PROGRESS_TREND).value as AiInsightState.Ready).text
        c.onInsightVisible(req)
        advanceUntilIdle()
        val second = (c.generationState(InsightKind.PROGRESS_TREND).value as AiInsightState.Ready).text
        cs.cancel()
        assertEquals(first, second)
    }

    @Test
    fun `retry re-runs for cached key`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val req = sufficientProgress()
        c.onInsightVisible(req)
        advanceUntilIdle()
        c.retryInsight(req)
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
    }

    @Test
    fun `kinds are independent`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(sufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
        assertEquals(AiInsightState.ModelReady, c.generationState(InsightKind.RECOVERY_READINESS).value)
    }
}
