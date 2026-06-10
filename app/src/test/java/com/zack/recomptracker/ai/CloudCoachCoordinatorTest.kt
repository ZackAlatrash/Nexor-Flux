package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.data.remote.ParsedToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudCoachCoordinatorTest {

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m"),
    )

    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        var lastToolSchemas: List<String> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastToolSchemas = toolSchemasJson
            return responses.removeFirst()
        }
    }

    private class FakeExecutor : CoachReadTools {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun execute(name: String, args: Map<String, String>): String {
            calls += name to args
            return """{"ok":true}"""
        }
        override suspend fun systemPromptSnapshot(): String = "SYSTEM PROMPT"
    }

    @Test
    fun `read-only question answers from a single completion`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("You are at 1500 kcal.", emptyList()))))
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, scope)
        advanceUntilIdle()
        coach.sendMessage("how many calories today?")
        advanceUntilIdle()
        val state = coach.state.value
        assertTrue(state is CoachState.Idle)
        assertEquals("You are at 1500 kcal.", (state as CoachState.Idle).history.last().text)
        assertTrue(client.lastToolSchemas.isNotEmpty())
        scope.cancel()
    }

    @Test
    fun `read tool runs without confirmation then answers`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "get_weekly_trends", emptyMap()))),
                ParsedChatResponse("Adherence was 86%.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, scope)
        advanceUntilIdle()
        coach.sendMessage("how was my week?")
        advanceUntilIdle()
        assertEquals(listOf("get_weekly_trends"), executor.calls.map { it.first })
        assertTrue(coach.state.value is CoachState.Idle)
        scope.cancel()
    }

    @Test
    fun `write tool pauses for confirmation then executes on confirm`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80")))),
                ParsedChatResponse("Logged your weight.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, scope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.AwaitingConfirmation)
        coach.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(listOf("log_metric"), executor.calls.map { it.first })
        assertTrue(coach.state.value is CoachState.Idle)
        scope.cancel()
    }

    @Test
    fun `write tool cancellation skips execution`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80")))),
                ParsedChatResponse("Okay, I didn't log it.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, executor, scope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        coach.cancelPendingAction()
        advanceUntilIdle()
        assertTrue(executor.calls.isEmpty())
        assertTrue(coach.state.value is CoachState.Idle)
        scope.cancel()
    }
}
