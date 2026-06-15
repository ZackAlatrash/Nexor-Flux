package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightJudgeTest {

    @Test
    fun `judge prompt embeds the data, the output, and the rubric axes`() {
        val prompt = InsightJudge.buildPrompt(
            cardLabel = "Weekly Summary",
            dataPrompt = "Weight: -0.30 kg/wk",
            output = "Down 0.30/wk — hold calories.",
        )
        assertTrue("data echoed", "Weight: -0.30 kg/wk" in prompt)
        assertTrue("output echoed", "hold calories" in prompt)
        assertTrue("accuracy axis", prompt.contains("accuracy", ignoreCase = true))
        assertTrue("shouldFire axis", prompt.contains("shouldFire"))
        assertTrue("asks for strict JSON", prompt.contains("JSON"))
    }

    @Test
    fun `parses a clean JSON score object`() {
        val json = """
            {"accuracy":5,"actionability":4,"proactivity":4,"tone":5,"brevity":5,
             "shouldFire":true,"notes":"good"}
        """.trimIndent()
        val s = InsightJudge.parse(json)!!
        assertEquals(5, s.accuracy)
        assertEquals(4, s.actionability)
        assertEquals(true, s.shouldFire)
        assertEquals("good", s.notes)
    }

    @Test
    fun `parses JSON wrapped in markdown fences and prose`() {
        val raw = "Here you go:\n```json\n{\"accuracy\":3,\"actionability\":3," +
            "\"proactivity\":3,\"tone\":3,\"brevity\":3,\"shouldFire\":false,\"notes\":\"meh\"}\n```"
        val s = InsightJudge.parse(raw)!!
        assertEquals(3, s.accuracy)
        assertEquals(false, s.shouldFire)
    }

    @Test
    fun `returns null on unparseable text`() {
        assertNull(InsightJudge.parse("the model refused"))
    }

    @Test
    fun `passes is true only when every axis is at least 4`() {
        val good = JudgeScores(5, 4, 4, 4, 4, true, "")
        val bad = JudgeScores(5, 3, 5, 5, 5, true, "")
        assertTrue(good.passes())
        assertTrue(!bad.passes())
    }
}
