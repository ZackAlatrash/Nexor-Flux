package com.zack.recomptracker.ai

import android.util.Log
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.coach.CoachSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Stage 2 of the coaching pipeline: the cloud phrasing DECORATION step for one already-decided
 * [CoachSignal]. See `docs/ai-redesign/08-technical-architecture.md` §1 — "Phrase (cloud, optional
 * decoration): the LLM turns signal.verdict into 1-2 sentences; on timeout/offline/error the surface
 * renders signal.fallbackText — the pipeline is correct and complete without stage 2."
 *
 * Absolute rules this service upholds:
 *  - The LLM ONLY rephrases. It never computes a number or changes the decision; the prompt instructs
 *    it to use only the numbers provided and invent none (see [CoachPhrasingPromptBuilder]).
 *  - On ANY failure — no cloud config, timeout, network error, blank output — [phrase] returns
 *    [CoachSignal.fallbackText]. It never throws and is never "broken", just quieter.
 *
 * Boundary rule (invariant #7): this file and its prompt builder reference no `Gemma*`/`Routing*`/
 * `ModelVariant`/`AiBackend`/`LocalNameGenerator` class — cloud-only.
 *
 * @param client the shared OpenAI-compatible client (streaming, single-turn, no tools).
 * @param config a lambda/StateFlow accessor for the current [CloudConfig]; `null` means no cloud is
 *   configured, so phrasing is skipped and the fallback is returned. Read per call so a settings
 *   change takes effect immediately.
 */
class CoachPhrasingService(
    private val client: OpenAiCompatClient,
    private val config: () -> CloudConfig?,
) {

    /**
     * Rephrases [signal]'s verdict into 1-2 warm sentences via the cloud model, or returns
     * [CoachSignal.fallbackText] when no cloud is configured or on any timeout/error/blank result.
     * Never throws.
     */
    suspend fun phrase(signal: CoachSignal): String {
        val cloudConfig = config() ?: return signal.fallbackText
        return try {
            val raw = StringBuilder()
            withTimeout(TIMEOUT_MS) {
                client.streamCompletion(
                    config = cloudConfig,
                    systemPrompt = CoachPhrasingPromptBuilder.SYSTEM_PROMPT,
                    userPrompt = CoachPhrasingPromptBuilder.buildUserPrompt(signal),
                ).collect { chunk -> raw.append(chunk) }
            }
            val phrased = sanitize(raw.toString())
            phrased.ifBlank { signal.fallbackText }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Phrasing timed out for ${signal.kind}; using fallback.")
            signal.fallbackText
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Phrasing failed for ${signal.kind}; using fallback.", e)
            signal.fallbackText
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
        private const val TAG = "CoachPhrasingService"
        private const val TIMEOUT_MS = 15_000L
    }
}
