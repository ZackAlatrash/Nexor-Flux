package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

/** Generates one gym-bro recipe name. */
interface RecipeNamer {
    /** Whether a name can currently be generated (AI enabled + model present / cloud configured). */
    val available: StateFlow<Boolean>

    /** Returns a sanitized name on success, or a failure on timeout / error / empty output. */
    suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String>
}

/**
 * OpenAI-compatible cloud recipe namer. Wraps the streamed completion in a timeout and sanitizes
 * the output. Cloud-only — the on-device path was removed in Phase 8.
 */
class CloudRecipeNamer(
    private val client: OpenAiCompatClient,
    private val configFlow: StateFlow<CloudConfig?>,
    override val available: StateFlow<Boolean>,
    private val promptBuilder: RecipeNamePromptBuilder = RecipeNamePromptBuilder(),
    private val timeoutMs: Long = 45_000L,
) : RecipeNamer {

    override suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String> {
        val config = configFlow.value
            ?: return Result.failure(IllegalStateException("Cloud AI not configured"))
        val userPrompt = promptBuilder.buildUserPrompt(ingredients, totals)
        return try {
            val raw = withTimeout(timeoutMs) {
                val sb = StringBuilder()
                client.streamCompletion(
                    config = config,
                    systemPrompt = RecipeNamePromptBuilder.SYSTEM_PROMPT,
                    userPrompt = userPrompt,
                ).collect { sb.append(it) }
                sb.toString()
            }
            val name = RecipeNamePromptBuilder.sanitize(raw)
            if (name.isBlank()) Result.failure(IllegalStateException("Empty name"))
            else Result.success(name)
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Always-unavailable namer for tests/previews. */
class StubRecipeNamer(
    override val available: StateFlow<Boolean> = MutableStateFlow(false),
    private val name: String = "Anabolic Oats",
) : RecipeNamer {
    override suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String> = Result.success(name)
}
