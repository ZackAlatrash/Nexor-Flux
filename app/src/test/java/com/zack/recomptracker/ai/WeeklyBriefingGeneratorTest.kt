package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.coach.CoachJourney
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

    private val config = CloudConfig("https://x", { "k" }, "m")

    private class FakeClient(private val text: String) : OpenAiCompatClient() {
        var calls = 0
        var lastUserPrompt: String? = null
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> = flowOf()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            calls++
            lastUserPrompt = messages.lastOrNull { it.role == "user" }?.content
            return ParsedChatResponse(text, emptyList())
        }
    }

    private class FakeJourney(private val narrative: String) : CoachJourney {
        override suspend fun recordFiredSignal(
            signal: com.zack.recomptracker.domain.coach.CoachSignal,
            weekSignature: String,
        ) = Unit
        override suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String) = Unit
        override suspend fun journeyNarrative(): String = narrative
    }

    private val validJson = """{"headline":"Recomp.","narrative":"Good week.",
        "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
        "strength":"Up.","recovery":"Good."},
        "action_rationale":"Hold.","watch_next":"Scale."}"""

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

    @Test
    fun `includes the journey narrative block when the store returns non-blank`() = runTest {
        val client = FakeClient(validJson)
        val gen = WeeklyBriefingGenerator(client, journey = FakeJourney("3 weeks ago your bench stalled; it's moving again."))
        gen.generate(config, data())
        val prompt = client.lastUserPrompt!!
        assertTrue("journey block header present", prompt.contains("YOUR JOURNEY SO FAR"))
        assertTrue("journey narrative present", prompt.contains("3 weeks ago your bench stalled"))
    }

    @Test
    fun `omits the journey block when the store narrative is blank`() = runTest {
        val client = FakeClient(validJson)
        val gen = WeeklyBriefingGenerator(client, journey = FakeJourney(""))
        gen.generate(config, data())
        assertTrue("no journey header when blank", !client.lastUserPrompt!!.contains("YOUR JOURNEY SO FAR"))
    }

    // ── 2C: supporting coach note is colour only; never overrides verdict/numbers ──

    @Test
    fun `supporting coach note reaches the prompt as supporting colour`() = runTest {
        val client = FakeClient(validJson)
        val gen = WeeklyBriefingGenerator(client)
        gen.generate(config, data(), WeeklyCoachNote("Steps fell this week.", "avg 6.2k vs 9.1k"))
        val prompt = client.lastUserPrompt!!
        assertTrue("note statement present", prompt.contains("Steps fell this week."))
        assertTrue("note framed as supporting colour", prompt.contains("SUPPORTING colour only"))
    }

    @Test
    fun `verdict and calorie change are unchanged by presence or absence of the coach note`() = runTest {
        val withoutNote = WeeklyBriefingGenerator(FakeClient(validJson)).generate(config, data())!!
        val withNote = WeeklyBriefingGenerator(FakeClient(validJson))
            .generate(config, data(), WeeklyCoachNote("Steps fell.", "6.2k vs 9.1k"))!!
        // The authoritative verdict + apply target come from WeeklyReviewData, never the note.
        assertEquals(withoutNote.action.verdict, withNote.action.verdict)
        assertEquals(withoutNote.action.applyTargetCalories, withNote.action.applyTargetCalories)
        assertEquals("Hold calories", withNote.action.verdict)
    }
}
