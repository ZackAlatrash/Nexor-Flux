package com.zack.recomptracker.ai.knowledge

/**
 * Pure-Kotlin keyword retriever: scores each chunk by weighted term overlap with the query
 * (title and tag matches weigh more than body matches). No Android or ML dependencies, so the
 * ranking is fully unit-testable. A future SemanticKnowledgeRetriever can replace it behind
 * [KnowledgeRetriever] with no other code changes.
 *
 * [minScore] is the relevance floor: chunks scoring below it are dropped so the injector emits
 * nothing rather than padding a small model's context with weak matches.
 */
class KeywordKnowledgeRetriever(
    chunks: List<KnowledgeChunk>,
    // Floor of 2.0 means a query term must hit a tag (2.0) or title (3.0), or two body words, to
    // count — a single stray body-word match (1.0) no longer leaks an off-topic chunk in.
    private val minScore: Double = 2.0,
) : KnowledgeRetriever {

    private data class Indexed(
        val chunk: KnowledgeChunk,
        val titleTokens: List<String>,
        val tagTokens: Set<String>,
        val bodyTokens: List<String>,
    )

    private val indexed: List<Indexed> = chunks.map { c ->
        Indexed(
            chunk = c,
            titleTokens = tokenize(c.title),
            tagTokens = c.tags.flatMap { tokenize(it) }.toSet(),
            bodyTokens = tokenize(c.text),
        )
    }

    override fun retrieve(query: String, k: Int): List<ScoredChunk> {
        val terms = tokenize(query).toSet()
        if (terms.isEmpty() || k <= 0) return emptyList()
        return indexed
            .map { ScoredChunk(it.chunk, score(it, terms)) }
            .filter { it.score >= minScore }
            .sortedWith(compareByDescending<ScoredChunk> { it.score }.thenBy { it.chunk.id })
            .take(k)
    }

    private fun score(doc: Indexed, terms: Set<String>): Double {
        var total = 0.0
        for (t in terms) {
            total += doc.titleTokens.count { it == t } * TITLE_WEIGHT
            if (t in doc.tagTokens) total += TAG_WEIGHT
            total += doc.bodyTokens.count { it == t } * BODY_WEIGHT
        }
        return total
    }

    private companion object {
        const val TITLE_WEIGHT = 3.0
        const val TAG_WEIGHT = 2.0
        const val BODY_WEIGHT = 1.0
        val STOPWORDS = setOf(
            "the", "a", "an", "is", "are", "of", "to", "and", "or", "in", "on", "for",
            "my", "i", "how", "what", "do", "does", "should", "can", "with", "at", "be",
            "it", "this", "that", "me", "you", "your", "much", "time",
            "get", "make", "keep", "need",
        )

        fun tokenize(s: String): List<String> =
            s.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 && it !in STOPWORDS }
                .map { stem(it) }

        /**
         * Light suffix stemmer so common word variants match (proteins→protein, training→train,
         * meals→meal). Applied identically to query and corpus tokens, so the transform only needs
         * to be self-consistent, not linguistically perfect. It handles inflectional endings only;
         * irregular synonym pairs (lose/loss, sore/soreness) are still covered by tags.
         */
        fun stem(t: String): String = when {
            t.length > 5 && t.endsWith("ing") -> t.dropLast(3)
            t.length > 4 && t.endsWith("ed") -> t.dropLast(2)
            t.length > 3 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
            else -> t
        }
    }
}
