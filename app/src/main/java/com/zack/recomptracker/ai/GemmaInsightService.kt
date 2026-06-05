package com.zack.recomptracker.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaInsightService(
    private val modelPath: String,
    private val cacheDir: String,
) {
    private var engine: Engine? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = cacheDir,
        )
        val e = Engine(config)
        e.initialize()
        engine = e
    }

    suspend fun generateExplanation(prompt: String): String = withContext(Dispatchers.IO) {
        val e = engine ?: error("GemmaInsightService not initialized — call initialize() first.")
        e.createConversation().use { conversation ->
            val response = conversation.sendMessage(prompt)
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        }
    }

    fun createConversation(config: ConversationConfig): Conversation {
        val e = engine ?: error("GemmaInsightService not initialized — call initialize() first.")
        return e.createConversation(config)
    }

    fun release() {
        engine?.close()
        engine = null
    }
}
