package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import com.zack.recomptracker.domain.coach.CoachSurface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachPhrasingServiceTest {

    private fun signal(): CoachSignal = CoachSignal(
        kind = SignalKind.RECOMP_WIN,
        tier = SignalTier.P1,
        category = SignalCategory.BODY,
        severity = 40,
        facts = SignalFacts(
            values = mapOf(
                "weightTrendKgPerWk" to "-0.02",
                "waistTrendCmPerWk" to "-0.35",
            ),
        ),
        verdict = "Hold your calories — this is textbook recomposition.",
        rationale = SignalRationale(
            primaryCauseByDomain = mapOf("body" to "Waist falling while weight holds steady"),
            behaviorToOutcome = "Consistent protein kept lean mass while fat dropped",
            confidence = Confidence.HIGH,
        ),
        dedupKey = "recomp-win-2026W27",
        surface = CoachSurface.TODAY,
        fallbackText = "Weight is flat but your waist is down 0.35 cm/wk — keep going.",
    )

    private fun config() = CloudConfig(baseUrl = "https://x", apiKey = "k", model = "m")

    /** Emits fixed chunks, or throws, per constructor. Captures the last prompts sent. */
    private class FakeClient(
        private val chunks: List<String> = emptyList(),
        private val throwError: Boolean = false,
    ) : OpenAiCompatClient() {
        var lastSystemPrompt: String? = null
        var lastUserPrompt: String? = null

        override fun streamCompletion(
            config: CloudConfig,
            systemPrompt: String,
            userPrompt: String,
        ): Flow<String> = flow {
            lastSystemPrompt = systemPrompt
            lastUserPrompt = userPrompt
            if (throwError) error("network down")
            chunks.forEach { emit(it) }
        }

        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse = ParsedChatResponse("", emptyList())
    }

    @Test
    fun `returns sanitized LLM text when the client succeeds and config is present`() = runTest {
        val client = FakeClient(chunks = listOf("**Nice** work — ", "hold steady.\n\nYou've got this."))
        val service = CoachPhrasingService(client) { config() }

        val result = service.phrase(signal())

        // Markdown stripped, blank lines collapsed, at most 2 sentences.
        assertEquals("Nice work — hold steady. You've got this.", result)
    }

    @Test
    fun `returns fallbackText when config is null`() = runTest {
        val client = FakeClient(chunks = listOf("phrased text"))
        val service = CoachPhrasingService(client) { null }
        val s = signal()

        assertEquals(s.fallbackText, service.phrase(s))
    }

    @Test
    fun `returns fallbackText when the client throws`() = runTest {
        val client = FakeClient(throwError = true)
        val service = CoachPhrasingService(client) { config() }
        val s = signal()

        assertEquals(s.fallbackText, service.phrase(s))
    }

    @Test
    fun `returns fallbackText when the LLM output is blank`() = runTest {
        val client = FakeClient(chunks = listOf("   ", "\n"))
        val service = CoachPhrasingService(client) { config() }
        val s = signal()

        assertEquals(s.fallbackText, service.phrase(s))
    }

    @Test
    fun `built prompt contains the verdict, every fact value, and a no-invented-numbers instruction`() = runTest {
        val client = FakeClient(chunks = listOf("ok"))
        val service = CoachPhrasingService(client) { config() }
        val s = signal()

        service.phrase(s)

        val user = client.lastUserPrompt.orEmpty()
        val system = client.lastSystemPrompt.orEmpty()

        assertTrue("prompt must carry the verdict", user.contains(s.verdict))
        s.facts.values.values.forEach { value ->
            assertTrue("prompt must carry fact value $value", user.contains(value))
        }
        // The core guardrail: forbid inventing numbers, expressed somewhere in the prompt.
        val combined = (system + "\n" + user).lowercase()
        assertTrue(
            "prompt must forbid introducing numbers not provided",
            combined.contains("only the numbers") ||
                combined.contains("never introduce a number") ||
                combined.contains("do not") && combined.contains("number"),
        )
    }
}
