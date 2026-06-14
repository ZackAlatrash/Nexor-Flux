package com.zack.recomptracker.ai.knowledge

import org.junit.Test
import java.io.File

/**
 * Retrieval probe — a DEV TOOL, not an assertion (it always passes). Loads the shipped corpus and
 * runs each prompt in `app/src/test/resources/retrieval-probe-questions.txt` through the real
 * keyword retriever, then writes a readable top-3 report to `build/retrieval-probe.txt`.
 *
 *   Run:   ./gradlew :app:testDebugUnitTest --tests "*RetrievalProbe"
 *   Read:  app/build/retrieval-probe.txt
 *
 * Edit the questions file and re-run to probe new prompts — no app-code recompile needed. Use this
 * to verify retrieval offline (free, no API) before testing the live coach with the golden prompts
 * in docs/knowledge-golden-prompts.md.
 */
class RetrievalProbeTest {

    @Test
    fun `probe retrieval for the question list`() {
        val corpus = KnowledgeCorpus.fromJson(File("src/main/assets/knowledge/corpus.json").readText())
        val retriever = KeywordKnowledgeRetriever(corpus.chunks)

        val questions = javaClass.getResourceAsStream("/retrieval-probe-questions.txt")
            ?.bufferedReader()?.readLines()
            ?: error("retrieval-probe-questions.txt not found on the test classpath")

        val sb = StringBuilder()
        sb.appendLine("Retrieval probe — corpus: ${corpus.chunks.size} chunks (top-3 per prompt)\n")
        for (raw in questions) {
            val line = raw.trim()
            when {
                line.isEmpty() -> sb.appendLine()
                line.startsWith("#") -> sb.appendLine(line)
                else -> {
                    val hits = retriever.retrieve(line, 3)
                    sb.appendLine("Q: $line")
                    if (hits.isEmpty()) {
                        sb.appendLine("   (no hits — nothing injected)")
                    } else {
                        hits.forEachIndexed { i, h ->
                            sb.appendLine("   [${i + 1}] ${"%5.1f".format(h.score)}  ${h.chunk.id}")
                            sb.appendLine("          ${h.chunk.title}  ·  ${h.chunk.source}")
                        }
                    }
                    sb.appendLine()
                }
            }
        }

        val out = File("build/retrieval-probe.txt")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        println(sb.toString())
        println("Report written to ${out.absolutePath}")
    }
}
