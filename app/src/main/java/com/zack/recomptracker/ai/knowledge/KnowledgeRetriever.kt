package com.zack.recomptracker.ai.knowledge

/** A chunk paired with its relevance score for a query. */
data class ScoredChunk(val chunk: KnowledgeChunk, val score: Double)

/** Ranks corpus chunks against a free-text query. */
interface KnowledgeRetriever {
    /** Top [k] chunks scoring above the relevance floor, highest score first. May be empty. */
    fun retrieve(query: String, k: Int): List<ScoredChunk>
}
