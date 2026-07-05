package com.zack.recomptracker.ai

import android.util.Log
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Cloud phrasing DECORATION step for the Weekly Rebalance card — clones [CoachPhrasingService]'s
 * shape exactly. The deterministic engine ([domain.rebalance.RebalanceEngine], not depended on
 * here) has already decided every number in a [RebalanceCopyFacts]; this service ONLY rephrases
 * the already-computed fallback sentence for one [RebalanceCopySlot]. See
 * `docs/superpowers/specs/2026-07-05-weekly-rebalance-design.md` §8.
 *
 * Absolute rules this service upholds:
 *  - The LLM never computes a number or changes the decision; the prompt instructs it to use only
 *    the numbers provided and invent none (see [RebalanceCopyPromptBuilder]).
 *  - On ANY failure — no cloud config, timeout, network error, blank output — [copy] returns
 *    [RebalanceCopyPromptBuilder.fallback]. It never throws and is never "broken", just quieter.
 *  - Plain streaming completion — NO tool schemas, immune to the OpenRouter tool_calls regression
 *    tracked for the cloud coach (see `docs/ai-coach.md`).
 *
 * @param client the shared OpenAI-compatible client (streaming, single-turn, no tools).
 * @param config a lambda/StateFlow accessor for the current [CloudConfig]; `null` means no cloud is
 *   configured, so phrasing is skipped and the fallback is returned. Read per call so a settings
 *   change takes effect immediately.
 */
class RebalanceCopyService(
    private val client: OpenAiCompatClient,
    private val config: () -> CloudConfig?,
) {

    /**
     * Rephrases the fallback text for [slot]/[facts] into 1-2 warm sentences via the cloud model,
     * or returns [RebalanceCopyPromptBuilder.fallback] when no cloud is configured or on any
     * timeout/error/blank result. Never throws (cancellation is rethrown, per structured concurrency).
     */
    suspend fun copy(slot: RebalanceCopySlot, facts: RebalanceCopyFacts): String {
        val fallback = RebalanceCopyPromptBuilder.fallback(slot, facts)
        val cloudConfig = config() ?: return fallback
        return try {
            val raw = StringBuilder()
            withTimeout(TIMEOUT_MS) {
                client.streamCompletion(
                    config = cloudConfig,
                    systemPrompt = RebalanceCopyPromptBuilder.systemPrompt(),
                    userPrompt = RebalanceCopyPromptBuilder.userPrompt(slot, facts),
                ).collect { chunk -> raw.append(chunk) }
            }
            val phrased = sanitize(raw.toString())
            phrased.ifBlank { fallback }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Rebalance copy timed out for $slot; using fallback.")
            fallback
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Rebalance copy failed for $slot; using fallback.", e)
            fallback
        }
    }

    /** Strips markdown, collapses blank lines/newlines to spaces, and caps at 2 sentences. */
    private fun sanitize(text: String): String = text
        .trim()
        .replace(Regex("""[*_`#>]"""), "")
        .replace(Regex("""\s*\n+\s*"""), " ")
        .trim()
        .let { if (it.isBlank()) it else InsightPromptBuilder.limitToSentences(it, 2) }

    private companion object {
        private const val TAG = "RebalanceCopyService"
        private const val TIMEOUT_MS = 15_000L
    }
}
