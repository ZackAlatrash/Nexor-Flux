package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingCoordinatorTest {

    private class FakeCoach(initial: CoachState) : CoachCoordinator {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<CoachState> = _state.asStateFlow()
        var sent: String? = null
        override fun sendMessage(text: String) { sent = text }
        override fun clearHistory() {}
        override fun confirmPendingAction() {}
        override fun cancelPendingAction() {}
    }

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
}
