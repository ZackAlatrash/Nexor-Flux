package com.zack.recomptracker.ai

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
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBriefingGeneratorTest {

    private fun data() = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "Maintenance trend."),
        2550,
    )

    private val config = CloudConfig("https://x", "k", "m")

    private class FakeClient(private val text: String) : OpenAiCompatClient() {
        var calls = 0
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> = flowOf()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            calls++
            return ParsedChatResponse(text, emptyList())
        }
    }

    @Test
    fun `merges model prose onto deterministic skeleton`() = runTest {
        val json = """{"headline":"Recomp.","narrative":"Good week.",
            "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
            "strength":"Up.","recovery":"Good."},
            "action_rationale":"Hold.","watch_next":"Scale."}"""
        val gen = WeeklyBriefingGenerator(FakeClient(json))
        val b = gen.generate(config, data())!!
        assertEquals("Recomp.", b.headline)
        assertEquals("Hold calories", b.action.verdict)
        assertEquals("Down.", b.signals.first { it.label == "Waist" }.interpretation)
    }

    @Test
    fun `falls back to engine summary after parse failures`() = runTest {
        val client = FakeClient("totally not json")
        val gen = WeeklyBriefingGenerator(client)
        val b = gen.generate(config, data())!!
        assertEquals(2, client.calls) // one retry
        assertTrue(b.narrative.contains("Maintenance trend."))
        assertEquals("Hold calories", b.action.verdict)
    }
}
