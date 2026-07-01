package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 3: the weekly briefing grounds its prose in the knowledge corpus, exactly like chat does. */
class WeeklyBriefingKnowledgeTest {

    private fun data() = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "Maintenance trend."),
        2550,
    )

    private val config = CloudConfig("https://x", "k", "m")

    private val validJson = """{"headline":"Recomp.","narrative":"Good week.",
        "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
        "strength":"Up.","recovery":"Good."},
        "action_rationale":"Hold.","watch_next":"Scale."}"""

    /** Captures the user prompt sent to the client so tests can assert on grounding content. */
    private class CapturingClient(private val text: String) : OpenAiCompatClient() {
        val userPrompts = mutableListOf<String>()
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> = flowOf()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            userPrompts += messages.first { it.role == "user" }.content.orEmpty()
            return ParsedChatResponse(text, emptyList())
        }
    }

    /** Returns a fixed reference block and records the query it was asked about. */
    private class FakeInjector(private val block: String) : KnowledgeInjector {
        val queries = mutableListOf<String>()
        override fun referenceBlock(query: String): String {
            queries += query
            return block
        }
    }

    @Test
    fun `reference block is prepended to the user prompt when the injector returns content`() = runTest {
        val block = "=== REFERENCE KNOWLEDGE ===\n[1] Recomp basics — protein drives it. (Source: X)\n=== END REFERENCE ==="
        val client = CapturingClient(validJson)
        val injector = FakeInjector(block)
        val gen = WeeklyBriefingGenerator(client, knowledgeInjector = injector)

        gen.generate(config, data())!!

        val sentPrompt = client.userPrompts.first()
        assertTrue("reference block should appear in the prompt", sentPrompt.contains(block))
        // Prepended: knowledge comes before the deterministic skeleton instructions.
        assertTrue(sentPrompt.indexOf(block) < sentPrompt.indexOf("Verdict:"))
    }

    @Test
    fun `query is built from the verdict label and signal labels`() = runTest {
        val injector = FakeInjector("=== REFERENCE ===\nx\n=== END ===")
        val gen = WeeklyBriefingGenerator(CapturingClient(validJson), knowledgeInjector = injector)
        val reviewData = data()

        gen.generate(config, reviewData)!!

        val query = injector.queries.first()
        assertTrue(query.contains("recomposition"))
        assertTrue(query.contains(reviewData.verdictLabel))
        reviewData.signals.forEach { assertTrue("query should mention ${it.label}", query.contains(it.label)) }
    }

    @Test
    fun `prompt is unchanged when the injector returns blank (NoOp behaviour)`() = runTest {
        val blankInjector = FakeInjector("")
        val withInjector = CapturingClient(validJson)
        WeeklyBriefingGenerator(withInjector, knowledgeInjector = blankInjector).generate(config, data())!!

        // A generator with the default (NoOp) injector must produce an identical prompt.
        val defaultClient = CapturingClient(validJson)
        WeeklyBriefingGenerator(defaultClient).generate(config, data())!!

        assertEquals(defaultClient.userPrompts.first(), withInjector.userPrompts.first())
        assertFalse(withInjector.userPrompts.first().contains("REFERENCE"))
    }

    @Test
    fun `deterministic verdict and numbers are unaffected by the reference block`() = runTest {
        val block = "=== REFERENCE KNOWLEDGE ===\n[1] noise — 9999 kcal. (Source: X)\n=== END REFERENCE ==="
        val gen = WeeklyBriefingGenerator(CapturingClient(validJson), knowledgeInjector = FakeInjector(block))

        val b = gen.generate(config, data())!!

        assertEquals("Hold calories", b.action.verdict)
        assertEquals("Recomp.", b.headline)
        assertEquals("Down.", b.signals.first { it.label == "Waist" }.interpretation)
    }
}
