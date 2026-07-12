package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
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
        CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m"),
    )

    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        var lastToolSchemas: List<String> = emptyList()
        var lastMessages: List<ChatRequestMessage> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastToolSchemas = toolSchemasJson
            lastMessages = messages
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

    @Test
    fun `blank completion nudges once then answers instead of faking Done`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                // Model returns nothing usable (no tool call, blank text) — the failure mode that
                // used to surface as the bogus "Done." placeholder.
                ParsedChatResponse("", emptyList()),
                ParsedChatResponse("Logged 200g grilled chicken.", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope)
        advanceUntilIdle()
        coach.sendMessage("log 200g grilled chicken")
        advanceUntilIdle()
        val state = coach.state.value
        assertTrue(state is CoachState.Idle)
        val last = (state as CoachState.Idle).history.last().text
        assertEquals("Logged 200g grilled chicken.", last)
        // The nudge was actually sent on the retry.
        assertTrue(client.lastMessages.any { it.role == "user" && it.content?.contains("returned nothing") == true })
        scope.cancel()
    }

    @Test
    fun `repeated blank completions surface an error not a fake success`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", emptyList()),
                ParsedChatResponse("", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope)
        advanceUntilIdle()
        coach.sendMessage("log 200g grilled chicken")
        advanceUntilIdle()
        val state = coach.state.value
        assertTrue("expected an error, not a fabricated success", state is CoachState.Error)
        // Never invent a "Done." assistant message.
        assertTrue(coach.state.value.let { it !is CoachState.Idle })
        scope.cancel()
    }

    private class FixedInjector(private val block: String) : KnowledgeInjector {
        override fun referenceBlock(query: String): String = block
    }

    @Test
    fun `knowledge reference block is injected before the user message`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
        val block = "=== REFERENCE KNOWLEDGE ===\n[1] Protein — eat protein (Source: ISSN)\n=== END REFERENCE ==="
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope, FixedInjector(block))
        advanceUntilIdle()
        coach.sendMessage("how much protein?")
        advanceUntilIdle()
        val msgs = client.lastMessages
        val refIdx = msgs.indexOfFirst { it.role == "system" && it.content?.contains("REFERENCE KNOWLEDGE") == true }
        val userIdx = msgs.indexOfFirst { it.role == "user" && it.content == "how much protein?" }
        assertTrue("reference block present", refIdx >= 0)
        assertTrue("reference precedes user message", refIdx < userIdx)
        scope.cancel()
    }

    @Test
    fun `blank injector adds no reference message`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope, FixedInjector(""))
        advanceUntilIdle()
        coach.sendMessage("hi")
        advanceUntilIdle()
        val refCount = client.lastMessages.count { it.content?.contains("REFERENCE KNOWLEDGE") == true }
        assertEquals(0, refCount)
        scope.cancel()
    }

    @Test
    fun `prior turn reference block is dropped on the next turn`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(
            ArrayDeque(
                listOf(
                    ParsedChatResponse("First answer.", emptyList()),
                    ParsedChatResponse("Second answer.", emptyList()),
                ),
            ),
        )
        val block = "=== REFERENCE KNOWLEDGE ===\n[1] Protein — eat protein (Source: ISSN)\n=== END REFERENCE ==="
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope, FixedInjector(block))
        advanceUntilIdle()
        coach.sendMessage("first?")
        advanceUntilIdle()
        coach.sendMessage("second?")
        advanceUntilIdle()
        // After the 2nd turn, only ONE reference block should remain in the resent context — the
        // prior turn's block was dropped rather than accumulated.
        val refCount = client.lastMessages.count { it.content?.contains("REFERENCE KNOWLEDGE") == true }
        assertEquals(1, refCount)
        scope.cancel()
    }

    @Test
    fun `cloud coach sends the web-search tool when given the cloud tool list`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
        val coach = CloudCoachCoordinator(
            flowOf(true), config(), client, FakeExecutor(), scope,
            toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
        )
        advanceUntilIdle()
        coach.sendMessage("calories in a big mac?")
        advanceUntilIdle()
        assertTrue(client.lastToolSchemas.any { it.contains("\"search_web\"") })
        scope.cancel()
    }

    @Test
    fun `search_web runs without confirmation then answers`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val responses = ArrayDeque(
            listOf(
                ParsedChatResponse("", listOf(ParsedToolCall("c1", "search_web", mapOf("query" to "big mac calories")))),
                ParsedChatResponse("A Big Mac is ~563 kcal (source).", emptyList()),
            ),
        )
        val client = ScriptedClient(responses)
        val executor = FakeExecutor()
        val coach = CloudCoachCoordinator(
            flowOf(true), config(), client, executor, scope,
            toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
        )
        advanceUntilIdle()
        coach.sendMessage("calories in a big mac?")
        advanceUntilIdle()
        assertEquals(listOf("search_web"), executor.calls.map { it.first })
        assertTrue(coach.state.value is CoachState.Idle)
        scope.cancel()
    }
}
