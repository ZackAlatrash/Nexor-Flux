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

/** Backend-agnostic single-shot text generator. Implementations throw on failure. */
fun interface NameGenerator {
    suspend fun generate(systemPrompt: String, userPrompt: String): String
}

/** On-device Gemma generator. Shares the engine's inferenceLock via [GemmaInsightService]. */
@Deprecated(
    "Legacy on-device (Gemma/LiteRT) AI path. Isolated in Phase 0; removed in Phase 6. Do not build " +
        "the new AI coach on it — see docs/ai-redesign/08-technical-architecture.md §5.",
)
class LocalNameGenerator(
    private val serviceHolder: GemmaServiceHolder,
    private val variant: () -> ModelVariant,
) : NameGenerator {
    override suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val modelPath = serviceHolder.modelFileFor(variant()).absolutePath
        val service = serviceHolder.getOrCreateService(modelPath)
        service.ensureInitialized()
        val sb = StringBuilder()
        service.generateExplanation("$systemPrompt\n\n$userPrompt").collect { sb.append(it) }
        return sb.toString()
    }
}

/** OpenAI-compatible cloud generator. */
class CloudNameGenerator(
    private val client: OpenAiCompatClient,
    private val configFlow: StateFlow<CloudConfig?>,
) : NameGenerator {
    override suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val config = configFlow.value ?: error("Cloud AI not configured")
        val sb = StringBuilder()
        client.streamCompletion(config = config, systemPrompt = systemPrompt, userPrompt = userPrompt)
            .collect { sb.append(it) }
        return sb.toString()
    }
}

/**
 * Picks the local or cloud generator by [effectiveBackend] (same rule as
 * [RoutingInsightCoordinator]), wraps the call in a timeout, and sanitizes the output.
 */
@Deprecated(
    "Legacy local/cloud router — part of the on-device AI path. Isolated in Phase 0; removed in " +
        "Phase 6 when CloudNameGenerator is used directly. See docs/ai-redesign/08-technical-architecture.md §5.",
)
class RoutingRecipeNamer(
    private val local: NameGenerator,
    private val cloud: NameGenerator,
    private val effectiveBackend: StateFlow<AiBackend>,
    override val available: StateFlow<Boolean>,
    private val promptBuilder: RecipeNamePromptBuilder = RecipeNamePromptBuilder(),
    private val timeoutMs: Long = 45_000L,
) : RecipeNamer {

    override suspend fun generate(
        ingredients: List<RecipeIngredientEntity>,
        totals: MacroTotals,
    ): Result<String> {
        val generator = if (effectiveBackend.value == AiBackend.CLOUD) cloud else local
        val userPrompt = promptBuilder.buildUserPrompt(ingredients, totals)
        return try {
            val raw = withTimeout(timeoutMs) {
                generator.generate(RecipeNamePromptBuilder.SYSTEM_PROMPT, userPrompt)
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
