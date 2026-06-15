package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertTrue
import org.junit.Test

class InsightScenariosTest {

    @Test
    fun `has at least eight named scenarios`() {
        assertTrue("expected >= 8 scenarios", InsightScenarios.ALL.size >= 8)
    }

    @Test
    fun `every scenario has a non-blank name and at least one card`() {
        InsightScenarios.ALL.forEach { s ->
            assertTrue("blank name", s.name.isNotBlank())
            assertTrue("scenario ${s.name} has no cards", s.cards().isNotEmpty())
        }
    }

    @Test
    fun `every card builds a non-empty prompt`() {
        InsightScenarios.ALL.forEach { s ->
            s.cards().forEach { (label, prompt) ->
                assertTrue("empty prompt for ${s.name}/$label", prompt.isNotBlank())
            }
        }
    }
}
