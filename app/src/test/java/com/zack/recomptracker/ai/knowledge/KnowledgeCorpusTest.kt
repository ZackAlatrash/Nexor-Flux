package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnowledgeCorpusTest {

    private val validJson = """
        {"chunks":[
          {"id":"protein-1","title":"Protein target","tags":["protein"],"source":"ISSN 2017","text":"Aim 1.6-2.2 g/kg."},
          {"id":"sleep-1","title":"Sleep and recovery","tags":["sleep","recovery"],"source":"NIH","text":"7-9 hours supports recovery."}
        ]}
    """.trimIndent()

    @Test
    fun `valid json parses all chunks`() {
        val corpus = KnowledgeCorpus.fromJson(validJson)
        assertEquals(2, corpus.chunks.size)
        assertEquals("protein-1", corpus.chunks.first().id)
        assertEquals(listOf("sleep", "recovery"), corpus.chunks[1].tags)
    }

    @Test
    fun `blank required field throws`() {
        val bad = """{"chunks":[{"id":"x","title":"","tags":["t"],"source":"s","text":"body"}]}"""
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }

    @Test
    fun `empty tags throws`() {
        val bad = """{"chunks":[{"id":"x","title":"T","tags":[],"source":"s","text":"body"}]}"""
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }

    @Test
    fun `duplicate ids throw`() {
        val bad = """
            {"chunks":[
              {"id":"x","title":"A","tags":["t"],"source":"s","text":"a"},
              {"id":"x","title":"B","tags":["t"],"source":"s","text":"b"}
            ]}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }
}
