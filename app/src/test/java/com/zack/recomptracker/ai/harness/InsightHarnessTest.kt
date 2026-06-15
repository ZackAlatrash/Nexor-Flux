package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightPromptBuilder
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live iteration harness for the cloud insight cards. NOT part of normal CI: it self-skips
 * unless `.env.test` (or INSIGHT_* env vars) is present. Run explicitly:
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"        (compact)
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*" -DinsightVerbose=true
 */
class InsightHarnessTest {

    private val verbose = System.getProperty("insightVerbose") == "true"

    @Test
    fun runInsightHarness() {
        val env = HarnessEnv.load()
        assumeTrue("No .env.test / INSIGHT_* env — skipping live harness.", env != null)
        env!!

        val client = OpenAiCompatClient()
        val genConfig = env.generationConfig()
        val judgeConfig = env.judgeConfig()
        val systemPrompt = "You are a precise, supportive body-recomposition coach. " +
            "Answer only from the data you are given."

        val passes = ArrayList<Boolean>()
        println("\n==================== INSIGHT HARNESS (model=${env.model}) ====================")

        for (scenario in InsightScenarios.ALL) {
            println("\n### ${scenario.name}")
            for ((label, dataPrompt) in scenario.cards()) {
                if (verbose) println("--- prompt [$label] ---\n$dataPrompt")

                val output = runBlocking {
                    val sb = StringBuilder()
                    client.streamCompletion(genConfig, systemPrompt, dataPrompt)
                        .collect { sb.append(it) }
                    sb.toString().trim()
                        .replace(Regex("""[*_`#>]"""), "")
                        .replace(Regex("""\n{2,}"""), " ")
                        .let { InsightPromptBuilder.limitToSentences(it, 2) }
                }

                val judgeRaw = runBlocking {
                    client.completion(
                        config = judgeConfig,
                        messages = listOf(
                            ChatRequestMessage(
                                role = "user",
                                content = InsightJudge.buildPrompt(label, dataPrompt, output),
                            ),
                        ),
                        toolSchemasJson = emptyList(),
                    ).text
                }
                val scores = InsightJudge.parse(judgeRaw)

                println("• $label: $output")
                if (scores != null) {
                    println("    ${scores.compactLine()}  | ${scores.notes}")
                    passes += scores.passes()
                } else {
                    println("    [judge parse failed] raw=${judgeRaw.take(120)}")
                    passes += false
                }
            }
        }

        val passed = passes.count { it }
        println("\n==================== SUMMARY: $passed/${passes.size} cards passed (>=4 all axes) ====================\n")
    }
}
