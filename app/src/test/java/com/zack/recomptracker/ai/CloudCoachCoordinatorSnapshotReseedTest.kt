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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Review P2-3: the coach system snapshot (Today's date + totals) is seeded once per conversation.
 * If the calendar day rolls over mid-conversation it must be reseeded, or the coach answers about
 * "today" using yesterday's date and totals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudCoachCoordinatorSnapshotReseedTest {

    private fun config() = MutableStateFlow<CloudConfig?>(
        CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m"),
    )

    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        var lastMessages: List<ChatRequestMessage> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastMessages = messages
            return responses.removeFirst()
        }
    }

    /** Stamps each system-prompt build with an incrementing counter so reseeds are observable. */
    private class CountingExecutor : CoachReadTools {
        var snapshotCount = 0
        override suspend fun execute(name: String, args: Map<String, String>): String = """{"ok":true}"""
        override suspend fun systemPromptSnapshot(): String = "SYSTEM_SNAPSHOT_#${++snapshotCount}"
    }

    @Test
    fun `crossing midnight mid-conversation reseeds the system snapshot`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val date = FakeDateProvider(LocalDate.of(2026, 7, 11))
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Day one.", emptyList()), ParsedChatResponse("Day two.", emptyList()))))
        val exec = CountingExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, exec, scope, dateProvider = date)
        advanceUntilIdle()

        coach.sendMessage("hi")
        advanceUntilIdle()
        assertEquals("SYSTEM_SNAPSHOT_#1", client.lastMessages.first().content)

        date.advanceTo(LocalDate.of(2026, 7, 12)) // midnight rolled over
        coach.sendMessage("what are my totals today?")
        advanceUntilIdle()

        // The system message (index 0) must now be the freshly rebuilt snapshot, and there must be
        // exactly ONE snapshot message (rebuilt IN PLACE, not appended).
        val systemSnapshots = client.lastMessages.filter { it.content?.startsWith("SYSTEM_SNAPSHOT_#") == true }
        assertEquals("SYSTEM_SNAPSHOT_#2", client.lastMessages.first().content)
        assertEquals(1, systemSnapshots.size)
        // The rebuild must PRESERVE the prior conversation, not wipe it: turn-1's user + assistant
        // messages and turn-2's user message all remain alongside the single rebuilt snapshot.
        assertEquals(listOf("system", "user", "assistant", "user"), client.lastMessages.map { it.role })
        assertTrue(client.lastMessages.any { it.role == "user" && it.content == "hi" })
        assertTrue(client.lastMessages.any { it.role == "assistant" && it.content == "Day one." })
        scope.cancel()
    }

    @Test
    fun `same day across turns does not reseed the snapshot`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val date = FakeDateProvider(LocalDate.of(2026, 7, 11))
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("A.", emptyList()), ParsedChatResponse("B.", emptyList()))))
        val exec = CountingExecutor()
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, exec, scope, dateProvider = date)
        advanceUntilIdle()

        coach.sendMessage("first")
        advanceUntilIdle()
        coach.sendMessage("second") // same day
        advanceUntilIdle()

        // Still the original snapshot; systemPromptSnapshot() was built exactly once.
        assertEquals("SYSTEM_SNAPSHOT_#1", client.lastMessages.first().content)
        assertEquals(1, exec.snapshotCount)
        assertTrue(coach.state.value is CoachState.Idle)
        scope.cancel()
    }
}
