package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StubInsightCoordinatorTest {

    private val aiEnabledFlow = MutableStateFlow(false)

    private fun makeCoordinatorScope(parentContext: kotlin.coroutines.CoroutineContext) =
        CoroutineScope(parentContext + SupervisorJob())

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
    fun `enabling toggle transitions to ModelReady`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeCoordinatorScope(coroutineContext)
        val c = StubInsightCoordinator(aiEnabledFlow, cs)
        advanceUntilIdle()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.state.value)
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
}
