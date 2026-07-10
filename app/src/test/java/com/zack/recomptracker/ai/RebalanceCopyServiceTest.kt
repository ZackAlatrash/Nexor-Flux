package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Test-first coverage for [RebalanceCopyPromptBuilder] (pure fallback templates, spec §8 verbatim)
 * and [RebalanceCopyService] (clones `CoachPhrasingService`'s null-config / timeout / error / blank
 * / cancellation shape). See `docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` §8.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RebalanceCopyServiceTest {

    // ---------------------------------------------------------------------------------------
    // RebalanceCopyPromptBuilder.fallback — pure template tests
    // ---------------------------------------------------------------------------------------

    private fun facts(
        lengthDays: Int = 3,
        dailyCalorieReduction: Int = 250,
        extraDailySteps: Int = 0,
        effectiveCalories: Int = 1950,
        dayX: Int = 2,
        ofY: Int = 3,
        surplusKcal: Int = 900,
        recoveredKcal: Int = 675,
        partial: Boolean = false,
    ) = RebalanceCopyFacts(
        lengthDays = lengthDays,
        dailyCalorieReduction = dailyCalorieReduction,
        extraDailySteps = extraDailySteps,
        effectiveCalories = effectiveCalories,
        dayX = dayX,
        ofY = ofY,
        surplusKcal = surplusKcal,
        recoveredKcal = recoveredKcal,
        partial = partial,
    )

    @Test
    fun `OFFER_HEADLINE fallback matches spec verbatim`() {
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_HEADLINE, facts())
        assertEquals("Your weekly goal is still within reach.", result)
    }

    @Test
    fun `OFFER_BODY fallback without steps matches spec verbatim and omits stepsClause`() {
        val f = facts(lengthDays = 3, dailyCalorieReduction = 250, extraDailySteps = 0, surplusKcal = 600)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, f)
        assertEquals(
            "A slightly high stretch nudged your week up. Here's a gentle 3-day way back — " +
                "about 250 kcal less a day.",
            result,
        )
        assertFalse("must not mention steps when extraDailySteps == 0", result.contains("steps"))
    }

    @Test
    fun `OFFER_BODY fallback with steps includes stepsClause per spec`() {
        val f = facts(lengthDays = 4, dailyCalorieReduction = 200, extraDailySteps = 1500, surplusKcal = 600)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, f)
        assertEquals(
            "A slightly high stretch nudged your week up. Here's a gentle 4-day way back — " +
                "about 200 kcal less a day and +1500 steps.",
            result,
        )
    }

    @Test
    fun `OFFER_BODY stepsClause present iff extraDailySteps greater than zero`() {
        val withSteps = RebalanceCopyPromptBuilder.fallback(
            RebalanceCopySlot.OFFER_BODY,
            facts(dailyCalorieReduction = 150, extraDailySteps = 500, surplusKcal = 600),
        )
        val withoutSteps = RebalanceCopyPromptBuilder.fallback(
            RebalanceCopySlot.OFFER_BODY,
            facts(dailyCalorieReduction = 150, extraDailySteps = 0, surplusKcal = 600),
        )
        assertTrue(withSteps.contains("and +500 steps"))
        assertFalse(withoutSteps.contains("and +"))
        assertFalse(withoutSteps.contains("steps"))
    }

    @Test
    fun `OFFER_BODY for a MOVE_MORE plan with zero calorie reduction leads with steps and drops kcal phrase`() {
        val f = facts(lengthDays = 3, dailyCalorieReduction = 0, extraDailySteps = 2000, surplusKcal = 600)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, f)

        assertEquals(
            "A slightly high stretch nudged your week up. Here's a gentle 3-day way back — " +
                "about +2000 steps a day.",
            result,
        )
        assertFalse("must not say '0 kcal less' when R == 0", result.contains("kcal less"))
        assertFalse("must not mention 0 kcal at all", result.contains("0 kcal"))
        assertTrue("must lead with the steps figure", result.contains("+2000 steps"))
    }

    @Test
    fun `OFFER_BODY intro scales with surplus size — under 700 is a slightly high stretch`() {
        val f = facts(surplusKcal = 699)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, f)
        assertTrue(result.startsWith("A slightly high stretch nudged your week up."))
    }

    @Test
    fun `OFFER_BODY intro scales with surplus size — 700 up to 1400 is a couple of heavier days`() {
        val atBoundary = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts(surplusKcal = 700))
        val midRange = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts(surplusKcal = 1000))
        val justUnder1400 = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts(surplusKcal = 1399))
        assertTrue(atBoundary.startsWith("A couple of heavier days nudged your week up."))
        assertTrue(midRange.startsWith("A couple of heavier days nudged your week up."))
        assertTrue(justUnder1400.startsWith("A couple of heavier days nudged your week up."))
    }

    @Test
    fun `OFFER_BODY intro scales with surplus size — 1400 and above is a big few days`() {
        val atBoundary = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts(surplusKcal = 1400))
        val huge = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, facts(surplusKcal = 3000))
        assertTrue(atBoundary.startsWith("A big few days pushed your week up."))
        assertTrue(huge.startsWith("A big few days pushed your week up."))
    }

    @Test
    fun `OFFER_PARTIAL_LINE fallback matches spec verbatim`() {
        val f = facts(surplusKcal = 3000, recoveredKcal = 2100)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_PARTIAL_LINE, f)
        assertEquals(
            "Recovers about 2100 of 3000 — a big one, but this keeps you moving the right way.",
            result,
        )
    }

    @Test
    fun `PROGRESS_LINE fallback matches spec verbatim`() {
        val f = facts(dayX = 2, ofY = 3, effectiveCalories = 1950)
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.PROGRESS_LINE, f)
        assertEquals("You're 2 of 3 days in — today's target is 1950 kcal.", result)
    }

    @Test
    fun `GRACEFUL_END fallback matches spec verbatim`() {
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.GRACEFUL_END, facts())
        assertEquals(
            "Let's ease off the rebalance — this week had a lot in it, and that's completely fine. " +
                "Back to your normal plan tomorrow.",
            result,
        )
    }

    @Test
    fun `COMPLETION fallback matches spec verbatim`() {
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.COMPLETION, facts())
        assertEquals(
            "Rebalance complete — nicely done. Your weekly average is back on track.",
            result,
        )
    }

    @Test
    fun `NO_ADJUSTMENT fallback (the resume note) matches spec verbatim`() {
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.NO_ADJUSTMENT, facts())
        assertEquals(
            "One rough patch won't derail you — you can't sensibly claw all of it back without it " +
                "feeling like a punishment. Just resume your normal plan tomorrow.",
            result,
        )
    }

    @Test
    fun `REASSURANCE fallback matches spec verbatim`() {
        val result = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.REASSURANCE, facts())
        assertEquals(
            "You're still on track — your weekly average is still near target. Nothing to do.",
            result,
        )
    }

    @Test
    fun `systemPrompt forbids inventing numbers and failure blame language`() {
        val prompt = RebalanceCopyPromptBuilder.systemPrompt().lowercase()
        assertTrue("must restrict to given numbers", prompt.contains("only the numbers"))
        assertTrue("must forbid implying failure", prompt.contains("failed") || prompt.contains("fail"))
    }

    @Test
    fun `userPrompt includes the fallback text and every fact value`() {
        val f = facts(
            lengthDays = 3, dailyCalorieReduction = 250, extraDailySteps = 1500, effectiveCalories = 1950,
            dayX = 2, ofY = 3, surplusKcal = 900, recoveredKcal = 675, partial = true,
        )
        val prompt = RebalanceCopyPromptBuilder.userPrompt(RebalanceCopySlot.OFFER_BODY, f)
        val fallback = RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_BODY, f)

        assertTrue("must include the fallback text to rephrase", prompt.contains(fallback))
        assertTrue(prompt.contains("3"))
        assertTrue(prompt.contains("250"))
        assertTrue(prompt.contains("1500"))
        assertTrue("must include surplusKcal in the allow-list", prompt.contains("900"))
        assertTrue("must include recoveredKcal in the allow-list", prompt.contains("675"))
        assertTrue("must include partial in the allow-list", prompt.contains("true"))
    }

    // ---------------------------------------------------------------------------------------
    // RebalanceCopyService — fake client by subclassing OpenAiCompatClient (it is `open`)
    // ---------------------------------------------------------------------------------------

    private fun config() = CloudConfig(baseUrl = "https://x", apiKey = { "k" }, model = "m")

    /** Emits fixed chunks, throws, or hangs-then-cancels, per constructor. Tracks whether it was invoked. */
    private class FakeClient(
        private val chunks: List<String> = emptyList(),
        private val throwError: Boolean = false,
        private val throwCancellation: Boolean = false,
    ) : OpenAiCompatClient() {
        var wasCalled: Boolean = false
            private set

        override fun streamCompletion(
            config: CloudConfig,
            systemPrompt: String,
            userPrompt: String,
        ): Flow<String> = flow {
            wasCalled = true
            if (throwCancellation) throw CancellationException("cancelled")
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
    fun `copy returns sanitized stream text when the client succeeds and config is present`() = runTest {
        val client = FakeClient(chunks = listOf("**Nice** work — ", "hold steady.\n\nYou've got this."))
        val service = RebalanceCopyService(client) { config() }

        val result = service.copy(RebalanceCopySlot.PROGRESS_LINE, facts())

        assertEquals("Nice work — hold steady. You've got this.", result)
    }

    @Test
    fun `copy returns fallback verbatim and never calls the client when config is null`() = runTest {
        val client = FakeClient(chunks = listOf("phrased text"))
        val service = RebalanceCopyService(client) { null }
        val f = facts()

        val result = service.copy(RebalanceCopySlot.OFFER_HEADLINE, f)

        assertEquals(RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.OFFER_HEADLINE, f), result)
        assertFalse("client must never be invoked when config is null", client.wasCalled)
    }

    @Test
    fun `copy returns fallback when the stream yields only blank chunks`() = runTest {
        val client = FakeClient(chunks = listOf("   ", "\n"))
        val service = RebalanceCopyService(client) { config() }
        val f = facts()

        val result = service.copy(RebalanceCopySlot.COMPLETION, f)

        assertEquals(RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.COMPLETION, f), result)
    }

    @Test
    fun `copy returns fallback when the client flow throws a RuntimeException`() = runTest {
        val client = FakeClient(throwError = true)
        val service = RebalanceCopyService(client) { config() }
        val f = facts()

        val result = service.copy(RebalanceCopySlot.GRACEFUL_END, f)

        assertEquals(RebalanceCopyPromptBuilder.fallback(RebalanceCopySlot.GRACEFUL_END, f), result)
    }

    @Test
    fun `copy propagates CancellationException from the client flow instead of falling back`() = runTest {
        val client = FakeClient(throwCancellation = true)
        val service = RebalanceCopyService(client) { config() }

        try {
            service.copy(RebalanceCopySlot.NO_ADJUSTMENT, facts())
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected — cancellation must never be swallowed into a fallback
        }
    }
}
