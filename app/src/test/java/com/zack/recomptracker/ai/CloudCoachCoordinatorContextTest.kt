package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.FakeDateProvider
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Review P2-4: the coach request context must (a) survive an error without wiping the model's
 * memory of prior turns — only the failed turn is reverted — and (b) be bounded so a long
 * conversation can't grow without limit into a provider 400.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudCoachCoordinatorContextTest {

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m"),
    )

    /** A null entry in the script means "throw" (models a network/provider failure on that turn). */
    private class FlakyClient(private val script: ArrayDeque<ParsedChatResponse?>) : OpenAiCompatClient() {
        var lastMessages: List<ChatRequestMessage> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastMessages = messages
            return script.removeFirst() ?: throw IllegalStateException("HTTP 500: boom")
        }
    }

    private class FakeExecutor : CoachReadTools {
        override suspend fun execute(name: String, args: Map<String, String>): String = """{"ok":true}"""
        override suspend fun systemPromptSnapshot(): String = "SYSTEM PROMPT"
    }

    private fun contentsOf(msgs: List<ChatRequestMessage>): List<String> = msgs.mapNotNull { it.content }

    @Test
    fun `an error reverts only the failed turn and keeps prior turns in context`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        // Turn 1 succeeds; turn 2's completion throws; turn 3 succeeds.
        val client = FlakyClient(
            ArrayDeque(
                listOf(
                    ParsedChatResponse("Reply one.", emptyList()),
                    null, // turn 2 fails
                    ParsedChatResponse("Reply three.", emptyList()),
                ),
            ),
        )
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope)
        advanceUntilIdle()

        coach.sendMessage("first")
        advanceUntilIdle()
        coach.sendMessage("second") // errors
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.Error)

        coach.sendMessage("third")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.Idle)

        val sent = contentsOf(client.lastMessages)
        // Prior successful turn survives the error; the failed "second" turn was reverted.
        assertTrue("turn-1 user preserved, was: $sent", sent.contains("first"))
        assertTrue("turn-1 reply preserved, was: $sent", sent.contains("Reply one."))
        assertTrue("current turn present, was: $sent", sent.contains("third"))
        assertFalse("failed turn must be reverted, was: $sent", sent.contains("second"))
        scope.cancel()
    }

    @Test
    fun `a long conversation trims the oldest turns to bound the context`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        // Each turn's user message is large + uniquely marked; enough turns overflow the char budget.
        val replies = (1..6).map { ParsedChatResponse("ok$it", emptyList()) }
        val client = FlakyClient(ArrayDeque(replies.toList<ParsedChatResponse?>()))
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope)
        advanceUntilIdle()

        repeat(6) { i ->
            coach.sendMessage("MARK${i + 1} " + "x".repeat(6000))
            advanceUntilIdle()
        }

        val sent = client.lastMessages.mapNotNull { it.content }.joinToString("\n")
        // The oldest turn was dropped by the trim; the most recent turn is retained.
        assertFalse("oldest turn should be trimmed, present unexpectedly", sent.contains("MARK1 "))
        assertTrue("most recent turn must be retained", sent.contains("MARK6 "))
        scope.cancel()
    }

    @Test
    fun `a failure while rebuilding the snapshot recovers with a fresh seed next turn`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val date = FakeDateProvider(LocalDate.of(2026, 7, 11))
        // systemPromptSnapshot throws exactly once, when the test arms it for the midnight reseed.
        val exec = object : CoachReadTools {
            var failNextSnapshot = false
            override suspend fun execute(name: String, args: Map<String, String>): String = """{"ok":true}"""
            override suspend fun systemPromptSnapshot(): String {
                if (failNextSnapshot) {
                    failNextSnapshot = false
                    throw IllegalStateException("prefs read failed")
                }
                return "SYSTEM PROMPT"
            }
        }
        val client = FlakyClient(
            ArrayDeque(
                listOf<ParsedChatResponse?>(
                    ParsedChatResponse("Reply one.", emptyList()), // turn 1
                    ParsedChatResponse("Reply three.", emptyList()), // turn 3 (turn 2 fails before any completion)
                ),
            ),
        )
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, exec, scope, dateProvider = date)
        advanceUntilIdle()

        coach.sendMessage("first")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.Idle)

        date.advanceTo(LocalDate.of(2026, 7, 12)) // day rolled over → turn 2 will attempt a reseed
        exec.failNextSnapshot = true // and that reseed's snapshot build throws
        coach.sendMessage("second")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.Error)

        // The broken state (systemSeeded=true but no system message) must self-heal: turn 3 reseeds,
        // so the sent context still starts with a system message rather than a user message.
        coach.sendMessage("third")
        advanceUntilIdle()
        assertTrue(coach.state.value is CoachState.Idle)
        assertEquals("system", client.lastMessages.first().role)
        assertTrue(client.lastMessages.any { it.role == "user" && it.content == "third" })
        scope.cancel()
    }
}
