package com.zack.recomptracker.ai.knowledge

/**
 * Builds a capped REFERENCE block from the top retrieval hits. Caps both the chunk count and the
 * total character budget (a proxy for the model's token budget) so the block never crowds out the
 * user's own data on a small-context model. Returns "" when nothing is relevant.
 */
class RetrievalKnowledgeInjector(
    private val retriever: KnowledgeRetriever,
    private val maxChunks: Int = 3,
    private val maxChars: Int = 2000,
) : KnowledgeInjector {

    override fun referenceBlock(query: String): String {
        val hits = retriever.retrieve(query, maxChunks)
        if (hits.isEmpty()) return ""

        val lines = mutableListOf<String>()
        var used = 0
        for ((index, scored) in hits.withIndex()) {
            val c = scored.chunk
            // Fixed decoration around the body: "[n] {title} — {body} (Source: {source})".
            val prefix = "[${index + 1}] ${c.title} — "
            val suffix = " (Source: ${c.source})"
            val overhead = prefix.length + suffix.length
            // The top chunk is always included (truncated to fit the budget, accounting for the
            // decoration and the "…" char); later chunks are dropped once the running total —
            // including the newline appendLine adds — would exceed the budget.
            val body = if (index == 0 && overhead + c.text.length > maxChars) {
                c.text.take((maxChars - overhead - 1).coerceAtLeast(0)).trimEnd() + "…"
            } else {
                c.text
            }
            val line = prefix + body + suffix
            if (index > 0 && used + line.length + 1 > maxChars) break
            lines.add(line)
            used += line.length + 1
        }

        return buildString {
            appendLine("=== REFERENCE KNOWLEDGE (use to inform your answer; cite the source) ===")
            lines.forEach { appendLine(it) }
            append("=== END REFERENCE ===")
        }
    }
}
