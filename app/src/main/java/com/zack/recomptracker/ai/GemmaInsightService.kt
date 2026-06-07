package com.zack.recomptracker.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the single LiteRT-LM [Engine] for this process.
 *
 * Thread-safety contract
 * ─────────────────────
 * • [initLock]      — prevents double-initialisation under concurrent callers.
 * • [inferenceLock] — mutual exclusion across ALL Engine operations (insight + coach).
 *   [generateExplanation] acquires it internally for its full streaming duration.
 *   [GemmaCoachCoordinator] acquires it via [withInferenceLock] for its entire
 *   multi-message turn so no other caller can interleave between sendMessage calls.
 *   [release] also acquires it so it waits for any in-flight inference before
 *   closing the engine (prevents use-after-close).
 *
 * Backend selection
 * ─────────────────
 * GPU is attempted first. If the device does not support it (missing OpenCL libs,
 * unsupported SoC, driver error) a single CPU retry is made. AndroidManifest.xml
 * must declare libOpenCL.so and libvndksupport.so as optional native libraries for
 * the GPU backend to load at all on Android 12+.
 *
 * Streaming
 * ─────────
 * [generateExplanation] uses [sendMessageAsync], which returns [Flow]<[com.google.ai.edge.litertlm.Message]>
 * where each emission is a delta chunk. Text is extracted from each partial [Message]
 * and forwarded to the caller so the UI updates token-by-token as the model generates.
 */
class GemmaInsightService(
    private val modelPath: String,
    private val cacheDir: String,
) {
    @Volatile private var engine: Engine? = null

    val isInitialized: Boolean get() = engine != null

    private val initLock = Mutex()

    /**
     * Exposed so [GemmaCoachCoordinator] can hold the lock for an entire multi-message
     * turn without releasing between [Conversation.sendMessage] calls.
     * Do not acquire this lock recursively.
     */
    val inferenceLock = Mutex()

    // ── Initialisation ─────────────────────────────────────────────────────────

    /**
     * Ensures the [Engine] is ready. Safe to call on every request — the hot path
     * (engine already initialised) returns immediately without acquiring [initLock]
     * thanks to the [@Volatile] fast check.
     */
    suspend fun ensureInitialized() {
        if (engine != null) return          // fast path — @Volatile guarantees visibility
        initLock.withLock {
            if (engine != null) return      // double-check inside lock
            engine = withContext(Dispatchers.IO) { buildEngine() }
        }
    }

    /**
     * Tries GPU first; falls back to CPU on any failure.
     * Must only be called from a background thread (Dispatchers.IO).
     */
    private fun buildEngine(): Engine {
        val gpuResult = runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = cacheDir,
                ),
            ).also { it.initialize() }
        }
        if (gpuResult.isSuccess) return gpuResult.getOrThrow()

        // GPU failed (missing OpenCL, unsupported SoC, driver error) — fall back to CPU.
        return Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cacheDir,
            ),
        ).also { it.initialize() }
    }

    // ── Inference ──────────────────────────────────────────────────────────────

    /**
     * Single-turn text generation with real token streaming via [sendMessageAsync].
     *
     * [sendMessageAsync] returns [Flow]<[com.google.ai.edge.litertlm.Message]> where
     * each emitted [Message] is a delta chunk. We extract the [Content.Text] pieces from
     * each and forward them as a [Flow]<[String]> so the coordinator updates the UI
     * token-by-token as the model generates — no fake word-by-word replay needed.
     *
     * The [inferenceLock] is held for the full streaming duration so no other caller can
     * use the Engine concurrently.
     *
     * Call [ensureInitialized] before collecting this flow.
     */
    fun generateExplanation(prompt: String): Flow<String> = channelFlow {
        val e = engine
            ?: error("GemmaInsightService not initialized — call ensureInitialized() first.")

        inferenceLock.withLock {
            withContext(Dispatchers.IO) {
                e.createConversation().use { conv ->
                    conv.sendMessageAsync(prompt).collect { partialMessage ->
                        // Each emission is a delta Message; extract its text tokens.
                        val chunkText = partialMessage.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        if (chunkText.isNotEmpty()) send(chunkText)
                    }
                }
            }
        }
        // channelFlow closes automatically when the producer block returns.
    }

    /**
     * Creates a [Conversation] for the multi-turn coach. The coach owns and manages
     * the conversation's lifetime; the caller is responsible for holding [inferenceLock]
     * (via [withInferenceLock]) across all [Conversation.sendMessage] calls in a turn.
     *
     * Call [ensureInitialized] before this.
     */
    fun createConversation(config: ConversationConfig): Conversation {
        val e = engine
            ?: error("GemmaInsightService not initialized — call ensureInitialized() first.")
        return e.createConversation(config)
    }

    /**
     * Convenience wrapper that lets [GemmaCoachCoordinator] hold [inferenceLock] for
     * an entire multi-message turn without exposing the [Mutex] directly.
     *
     * Wraps [block] in a lambda so the suspend call goes through the inline [withLock]
     * context correctly (Mutex.withLock takes () -> T, not suspend () -> T).
     */
    suspend fun <T> withInferenceLock(block: suspend () -> T): T =
        inferenceLock.withLock { block() }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Closes the [Engine]. Waits for any in-flight inference to finish first by
     * acquiring [inferenceLock], preventing use-after-close.
     */
    suspend fun release() = inferenceLock.withLock {
        engine?.close()
        engine = null
    }
}
