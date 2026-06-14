package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalKnowledgeInjectorTest {

    /** Returns a fixed list regardless of query, so injector formatting/capping is tested in isolation. */
    private class FakeRetriever(private val hits: List<ScoredChunk>) : KnowledgeRetriever {
        override fun retrieve(query: String, k: Int): List<ScoredChunk> = hits.take(k)
    }

    private fun chunk(id: String, title: String, text: String) =
        ScoredChunk(KnowledgeChunk(id, title, listOf("t"), "SRC-$id", text), 5.0)

    @Test
    fun `no hits produces empty block`() {
        val injector = RetrievalKnowledgeInjector(FakeRetriever(emptyList()))
        assertEquals("", injector.referenceBlock("anything"))
    }

    @Test
    fun `block contains title source and markers`() {
        val injector = RetrievalKnowledgeInjector(FakeRetriever(listOf(chunk("a", "Protein", "Eat protein."))))
        val block = injector.referenceBlock("protein")
        assertTrue(block.contains("REFERENCE KNOWLEDGE"))
        assertTrue(block.contains("END REFERENCE"))
        assertTrue(block.contains("Protein"))
        assertTrue(block.contains("Source: SRC-a"))
    }

    @Test
    fun `char budget drops later chunks`() {
        val big = "x".repeat(1500)
        val injector = RetrievalKnowledgeInjector(
            FakeRetriever(listOf(chunk("a", "First", big), chunk("b", "Second", big))),
            maxChunks = 3,
            maxChars = 2000,
        )
        val block = injector.referenceBlock("q")
        assertTrue(block.contains("First"))
        assertFalse(block.contains("Second"))
    }

    @Test
    fun `oversized single top chunk is truncated`() {
        val huge = "y".repeat(5000)
        val injector = RetrievalKnowledgeInjector(
            FakeRetriever(listOf(chunk("a", "Big", huge))),
            maxChars = 2000,
        )
        val block = injector.referenceBlock("q")
        assertTrue(block.contains("Big"))
        assertTrue(block.contains("…"))
        assertTrue(block.length < 2200)
    }
}
