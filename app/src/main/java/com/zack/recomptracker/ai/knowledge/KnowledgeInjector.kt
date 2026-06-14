package com.zack.recomptracker.ai.knowledge

/** Produces the reference-knowledge block injected into the coach prompt for a user message. */
interface KnowledgeInjector {
    /** Formatted REFERENCE block for [query], or "" when nothing relevant is found. */
    fun referenceBlock(query: String): String
}

/** Used when no corpus is available (e.g. the asset is missing). Always returns "". */
object NoOpKnowledgeInjector : KnowledgeInjector {
    override fun referenceBlock(query: String): String = ""
}
