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
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"                       (Claude-judged: prints outputs only)
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*" -DinsightVerbose=true (also prints each prompt)
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*" -DinsightJudge=model  (use the LLM-judge model for scores)
 *
 * Judge mode: default "claude" emits clean numbered outputs for a human/Claude to score against
 * the doctrine — the same weak free model judging itself is too noisy to trust. Set
 * -DinsightJudge=model to bring back the automated LLM-judge scoring (best with a strong judge model).
 */
class InsightHarnessTest {

    private val verbose = System.getProperty("insightVerbose") == "true"
    private val useModelJudge = System.getProperty("insightJudge", "claude") == "model"

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
        val judgeLabel = if (useModelJudge) "model=${env.judgeModel}" else "Claude (manual)"
        println("\n==================== INSIGHT HARNESS (gen=${env.model} | judge=$judgeLabel) ====================")

        var n = 0
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

                n++
                println("[$n] $label: $output")

                if (useModelJudge) {
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
                    if (scores != null) {
                        println("    ${scores.compactLine()}  | ${scores.notes}")
                        passes += scores.passes()
                    } else {
                        println("    [judge parse failed] raw=${judgeRaw.take(120)}")
                        passes += false
                    }
                }
            }
        }

        if (useModelJudge) {
            val passed = passes.count { it }
            println("\n==================== SUMMARY: $passed/${passes.size} cards passed (>=4 all axes) ====================\n")
        } else {
            println("\n==================== $n outputs above — score against the doctrine ====================\n")
        }
    }
}
