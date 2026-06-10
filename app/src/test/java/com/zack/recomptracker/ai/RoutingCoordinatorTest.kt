package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingCoordinatorTest {

    private class FakeCoach(initial: CoachState) : CoachCoordinator {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<CoachState> = _state.asStateFlow()
        var sent: String? = null
        var confirmed = false
        var cancelled = false
        override fun sendMessage(text: String) { sent = text }
        override fun clearHistory() {}
        override fun confirmPendingAction() { confirmed = true }
        override fun cancelPendingAction() { cancelled = true }
    }

    private class FakeInsight : AiInsightCoordinator {
        private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
        override val state: StateFlow<AiInsightState> = _state.asStateFlow()

        private val _selectedModel = MutableStateFlow(ModelVariant.GEMMA_2B)
        override val selectedModel: StateFlow<ModelVariant> = _selectedModel.asStateFlow()

        private val kindStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
            InsightKind.entries.associateWith { MutableStateFlow(AiInsightState.ModelReady) }

        override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
            kindStates.getValue(kind)

        var selectedModelVariant: ModelVariant? = null
        var downloadRequested = false
        var downloadCancelled = false
        var modelDeleted = false

        override fun setSelectedModel(variant: ModelVariant) { selectedModelVariant = variant }
        override fun requestDownload() { downloadRequested = true }
        override fun cancelDownload() { downloadCancelled = true }
        override fun deleteModel() { modelDeleted = true }
        override fun onAiCardVisible(context: InsightContext) {}
        override fun retryGeneration(context: InsightContext) {}
        override fun onInsightVisible(request: InsightRequest) {}
        override fun retryInsight(request: InsightRequest) {}
    }

    // ── Coach tests ───────────────────────────────────────────────────────────

    @Test
    fun `coach router forwards to cloud when effective backend is CLOUD`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val local = FakeCoach(CoachState.Ready)
        val cloud = FakeCoach(CoachState.Idle(listOf(ChatMessage(Role.Assistant, "cloud"))))
        val backend = MutableStateFlow(AiBackend.CLOUD)
        val cloudConfigComplete = MutableStateFlow(true)
        val router = RoutingCoachCoordinator(local, cloud, backend, cloudConfigComplete, scope)
        advanceUntilIdle()
        router.sendMessage("hi")
        assertEquals("hi", cloud.sent)
        assertEquals(null, local.sent)
        scope.cancel()
    }

    @Test
    fun `coach router falls back to local when cloud config incomplete`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val local = FakeCoach(CoachState.Ready)
        val cloud = FakeCoach(CoachState.Unavailable)
        val backend = MutableStateFlow(AiBackend.CLOUD)
        val cloudConfigComplete = MutableStateFlow(false)
        val router = RoutingCoachCoordinator(local, cloud, backend, cloudConfigComplete, scope)
        advanceUntilIdle()
        router.sendMessage("hi")
        assertEquals("hi", local.sent)
        assertEquals(null, cloud.sent)
        scope.cancel()
    }

    // ── Insight router tests ──────────────────────────────────────────────────

    @Test
    fun `generationState returns the same cached instance on repeated calls`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val local = FakeInsight()
        val cloud = FakeInsight()
        val backend = MutableStateFlow(AiBackend.LOCAL)
        val cloudConfigComplete = MutableStateFlow(false)
        val router = RoutingInsightCoordinator(local, cloud, backend, cloudConfigComplete, scope)
        advanceUntilIdle()
        assertSame(
            router.generationState(InsightKind.PROGRESS_TREND),
            router.generationState(InsightKind.PROGRESS_TREND),
        )
        scope.cancel()
    }

    @Test
    fun `insight router routes lifecycle to local`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val local = FakeInsight()
        val cloud = FakeInsight()
        val backend = MutableStateFlow(AiBackend.LOCAL)
        val cloudConfigComplete = MutableStateFlow(false)
        val router = RoutingInsightCoordinator(local, cloud, backend, cloudConfigComplete, scope)
        advanceUntilIdle()
        router.setSelectedModel(ModelVariant.GEMMA_4B)
        router.requestDownload()
        assertEquals(ModelVariant.GEMMA_4B, local.selectedModelVariant)
        assertTrue(local.downloadRequested)
        assertEquals(null, cloud.selectedModelVariant)
        assertTrue(!cloud.downloadRequested)
        scope.cancel()
    }
}
