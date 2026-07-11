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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review P2-1: a confirmed write can persist to Room and then the *next* completion (the one that
 * produces the closing text) can time out or throw. When that happens the turn must NOT surface a
 * bare "try again" — that implies nothing was saved and invites the user to re-issue the command,
 * double-logging. The error must acknowledge the already-persisted write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudCoachCoordinatorWriteAckTest {

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m"),
    )

    /** Serves scripted responses; when the deque is empty, completion() throws (models a failing call). */
    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = responses.removeFirst()
    }

    /** Executor whose writes return the production success marker so committed writes are detectable. */
    private class SuccessExecutor : CoachReadTools {
        val calls = mutableListOf<String>()
        override suspend fun execute(name: String, args: Map<String, String>): String {
            calls += name
            return """{"success":true,"metric":"weight_kg","value":80}"""
        }
        override suspend fun systemPromptSnapshot(): String = "SYSTEM PROMPT"
    }

    @Test
    fun `persisted write then a failing final completion acknowledges the save`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        // Only the tool-call response is scripted; the follow-up completion throws (empty deque).
        val responses = ArrayDeque(
            listOf(ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80"))))),
        )
        val coach = CloudCoachCoordinator(flowOf(true), config(), ScriptedClient(responses), SuccessExecutor(), scope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        coach.confirmPendingAction()
        advanceUntilIdle()

        val state = coach.state.value
        assertTrue("expected an error after the failing final completion", state is CoachState.Error)
        val msg = (state as CoachState.Error).message
        // The user must be told their data was saved, not a bare "try again".
        assertTrue("error should acknowledge the save, was: $msg", msg.contains("saved", ignoreCase = true))
        assertFalse("error must not be the bare generic retry message, was: $msg", msg == "Something went wrong — try again.")
        scope.cancel()
    }

    @Test
    fun `cancelled write then a failing completion does NOT claim a save`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80"))))),
        )
        val executor = SuccessExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), ScriptedClient(responses), executor, scope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        coach.cancelPendingAction() // user declines → nothing persisted
        advanceUntilIdle()

        val state = coach.state.value
        assertTrue("expected an error after the failing completion", state is CoachState.Error)
        val msg = (state as CoachState.Error).message
        assertTrue("nothing was executed", executor.calls.isEmpty())
        assertFalse("must not claim a save when the write was cancelled, was: $msg", msg.contains("saved", ignoreCase = true))
        scope.cancel()
    }

    @Test
    fun `committed-write flag resets between turns`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        // Turn 1: write confirmed + a clean closing reply (turn succeeds).
        // Turn 2: a read-only question whose completion throws (empty deque) — no write this turn.
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "log_metric", mapOf("metric" to "weight_kg", "value" to "80")))),
                ParsedChatResponse("Logged your weight.", emptyList()),
            ),
        )
        val coach = CloudCoachCoordinator(flowOf(true), config(), ScriptedClient(responses), SuccessExecutor(), scope)
        advanceUntilIdle()
        coach.sendMessage("log my weight 80")
        advanceUntilIdle()
        coach.confirmPendingAction()
        advanceUntilIdle()
        assertTrue("turn 1 should succeed", coach.state.value is CoachState.Idle)

        coach.sendMessage("how many calories today?") // turn 2 completion throws
        advanceUntilIdle()
        val state = coach.state.value
        assertTrue("turn 2 should error", state is CoachState.Error)
        val msg = (state as CoachState.Error).message
        assertFalse("turn 2 (no write) must not claim a save, was: $msg", msg.contains("saved", ignoreCase = true))
        scope.cancel()
    }
}
