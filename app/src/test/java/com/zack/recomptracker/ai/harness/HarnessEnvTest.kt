package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessEnvTest {

    @Test
    fun `parses keys and defaults judge model to model`() {
        val text = """
            # a comment
            INSIGHT_BASE_URL=https://api.x.com/v1
            INSIGHT_API_KEY=sk-abc

            INSIGHT_MODEL=model-a
        """.trimIndent()
        val env = HarnessEnv.parse(text)!!
        assertEquals("https://api.x.com/v1", env.baseUrl)
        assertEquals("sk-abc", env.apiKey)
        assertEquals("model-a", env.model)
        assertEquals("model-a", env.judgeModel)
    }

    @Test
    fun `explicit judge model overrides default`() {
        val text = """
            INSIGHT_BASE_URL=https://api.x.com/v1
            INSIGHT_API_KEY=sk-abc
            INSIGHT_MODEL=model-a
            INSIGHT_JUDGE_MODEL=judge-b
        """.trimIndent()
        assertEquals("judge-b", HarnessEnv.parse(text)!!.judgeModel)
    }

    @Test
    fun `missing required key yields null`() {
        val text = "INSIGHT_BASE_URL=https://x\nINSIGHT_MODEL=m"
        assertNull(HarnessEnv.parse(text))
    }

    @Test
    fun `quoted values are unwrapped`() {
        val text = """
            INSIGHT_BASE_URL="https://api.x.com/v1"
            INSIGHT_API_KEY='sk-abc'
            INSIGHT_MODEL=model-a
        """.trimIndent()
        val env = HarnessEnv.parse(text)!!
        assertEquals("https://api.x.com/v1", env.baseUrl)
        assertEquals("sk-abc", env.apiKey)
    }
}
