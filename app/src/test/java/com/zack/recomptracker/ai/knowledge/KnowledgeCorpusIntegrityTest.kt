package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the SHIPPED corpus.json and runs it through full validation. Gradle runs unit tests with
 * the module dir (app/) as the working directory, so the asset is read by relative path. This
 * catches a broken ingestion run (blank field, missing tags, duplicate id) before it ships.
 */
class KnowledgeCorpusIntegrityTest {

    @Test
    fun `shipped corpus parses and passes validation`() {
        val file = File("src/main/assets/knowledge/corpus.json")
        assertTrue("corpus.json missing at ${file.absolutePath}", file.exists())
        val corpus = KnowledgeCorpus.fromJson(file.readText())
        assertTrue("corpus is empty", corpus.chunks.isNotEmpty())
    }
}
