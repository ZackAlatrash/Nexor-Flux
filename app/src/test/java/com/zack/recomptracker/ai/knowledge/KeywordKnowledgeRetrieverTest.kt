package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordKnowledgeRetrieverTest {

    private val corpus = listOf(
        KnowledgeChunk("protein-1", "Daily protein target", listOf("protein", "muscle"),
            "ISSN", "Aim for 1.6 to 2.2 grams of protein per kilogram for muscle gain."),
        KnowledgeChunk("sleep-1", "Sleep and recovery", listOf("sleep", "recovery"),
            "NIH", "Seven to nine hours of sleep supports recovery and performance."),
        KnowledgeChunk("creatine-1", "Creatine basics", listOf("creatine", "supplement"),
            "ISSN", "Creatine monohydrate at five grams daily improves strength."),
    )

    private val retriever = KeywordKnowledgeRetriever(corpus)

    @Test
    fun `query ranks the most relevant chunk first`() {
        val hits = retriever.retrieve("how much protein for muscle?", 3)
        assertEquals("protein-1", hits.first().chunk.id)
    }

    @Test
    fun `tag match contributes to score`() {
        val hits = retriever.retrieve("creatine", 3)
        assertEquals("creatine-1", hits.first().chunk.id)
    }

    @Test
    fun `irrelevant query returns no hits above the floor`() {
        val hits = retriever.retrieve("what time does the gym open downtown", 3)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `empty query returns empty`() {
        assertTrue(retriever.retrieve("", 3).isEmpty())
    }

    @Test
    fun `k limits the number of results`() {
        val hits = retriever.retrieve("protein sleep creatine recovery", 1)
        assertEquals(1, hits.size)
    }

    @Test
    fun `plural query term matches singular content via stemming`() {
        // "proteins" should stem to "protein" and match the protein chunk.
        val hits = retriever.retrieve("how many grams of proteins?", 3)
        assertEquals("protein-1", hits.first().chunk.id)
    }

    @Test
    fun `verb form in query matches root word via stemming`() {
        val corpus = listOf(
            KnowledgeChunk("train-1", "Train hard", listOf("train"), "S", "Train with enough volume."),
        )
        val hits = KeywordKnowledgeRetriever(corpus).retrieve("how should I be training?", 3)
        assertEquals("train-1", hits.first().chunk.id)
    }

    @Test
    fun `plural tag matches singular query via stemming`() {
        val corpus = listOf(
            KnowledgeChunk("meal-1", "Eating schedule", listOf("meals"), "S", "Plan your day."),
        )
        val hits = KeywordKnowledgeRetriever(corpus).retrieve("how many meal per day?", 3)
        assertEquals("meal-1", hits.first().chunk.id)
    }
}
