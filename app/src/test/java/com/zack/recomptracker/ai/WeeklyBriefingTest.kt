package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyBriefingTest {

    @Test
    fun `parses a clean narration object`() {
        val json = """
            {"headline":"Recomp — hold calories.","narrative":"Solid week.",
             "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
             "strength":"Up.","recovery":"Good."},
             "action_rationale":"No change needed.","watch_next":"Watch the scale."}
        """.trimIndent()
        val n = parseBriefingNarration(json)!!
        assertEquals("Recomp — hold calories.", n.headline)
        assertEquals("Down.", n.interpretations["waist"])
        assertEquals("Watch the scale.", n.watchNext)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val json = "```json\n{\"headline\":\"H\",\"narrative\":\"N\",\"interpretations\":{}," +
            "\"action_rationale\":\"A\",\"watch_next\":\"W\"}\n```"
        val n = parseBriefingNarration(json)!!
        assertEquals("H", n.headline)
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(parseBriefingNarration("not json at all"))
    }
}
