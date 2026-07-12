package com.zack.recomptracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatModelsTest {

    @Test
    fun `extractStreamDelta returns content from a data line`() {
        val line =
            """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", extractStreamDelta(line))
    }

    @Test
    fun `extractStreamDelta returns null for the DONE sentinel`() {
        assertNull(extractStreamDelta("data: [DONE]"))
    }

    @Test
    fun `extractStreamDelta returns null for blank or non-data lines`() {
        assertNull(extractStreamDelta(""))
        assertNull(extractStreamDelta(":keep-alive comment"))
        assertNull(extractStreamDelta("event: ping"))
    }

    @Test
    fun `extractStreamDelta returns null when delta has no content`() {
        assertNull(extractStreamDelta("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
    }

    @Test
    fun `extractStreamDelta returns null when delta content is JSON null`() {
        assertNull(extractStreamDelta("""data: {"choices":[{"delta":{"content":null}}]}"""))
    }

    @Test
    fun `parseChatResponse extracts plain text content`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"You are at 1420 kcal."}}]}"""
        val parsed = parseChatResponse(body)
        assertEquals("You are at 1420 kcal.", parsed.text)
        assertTrue(parsed.toolCalls.isEmpty())
    }

    @Test
    fun `parseChatResponse flattens array content-parts (P2-6)`() {
        // Some OpenAI-compatible routes return content as a content-parts array rather than a string;
        // reading it as a primitive used to throw and fail the whole turn.
        val body = """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"Hello "},{"type":"text","text":"world."}]}}]}"""
        val parsed = parseChatResponse(body)
        assertEquals("Hello world.", parsed.text)
        assertTrue(parsed.toolCalls.isEmpty())
    }

    @Test
    fun `parseChatResponse tolerates an empty content array`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":[]}}]}"""
        assertEquals("", parseChatResponse(body).text)
    }

    @Test
    fun `parseChatResponse extracts tool calls with arguments`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"call_1","type":"function",
            "function":{"name":"log_meal","arguments":"{\"name\":\"banana\",\"grams\":120}"}}]}}]}
        """.trimIndent()
        val parsed = parseChatResponse(body)
        assertEquals("", parsed.text)
        assertEquals(1, parsed.toolCalls.size)
        val call = parsed.toolCalls.first()
        assertEquals("call_1", call.id)
        assertEquals("log_meal", call.name)
        assertEquals("banana", call.arguments["name"])
        assertEquals("120", call.arguments["grams"])
    }

    @Test
    fun `buildChatRequestJson includes model, messages, tools and stream flag`() {
        val json = buildChatRequestJson(
            model = "openai/gpt-4o-mini",
            messages = listOf(ChatRequestMessage(role = "user", content = "hi")),
            toolSchemasJson = listOf("""{"name":"get_weekly_trends","description":"d","parameters":{"type":"object","properties":{}}}"""),
            stream = false,
        )
        assertTrue(json.contains("\"model\":\"openai/gpt-4o-mini\""))
        assertTrue(json.contains("\"stream\":false"))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("\"type\":\"function\""))
        assertTrue(json.contains("get_weekly_trends"))
    }
}
