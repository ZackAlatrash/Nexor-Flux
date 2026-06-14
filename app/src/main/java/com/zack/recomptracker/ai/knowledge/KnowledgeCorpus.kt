package com.zack.recomptracker.ai.knowledge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One retrievable unit of domain knowledge, shipped in assets/knowledge/corpus.json. */
data class KnowledgeChunk(
    val id: String,
    val title: String,
    val tags: List<String>,
    val source: String,
    val text: String,
)

/** In-memory corpus of [KnowledgeChunk]s, parsed and validated from the shipped JSON. */
class KnowledgeCorpus(val chunks: List<KnowledgeChunk>) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class ChunkDto(
            val id: String,
            val title: String,
            val tags: List<String> = emptyList(),
            val source: String,
            val text: String,
        )

        @Serializable
        private data class CorpusDto(val chunks: List<ChunkDto> = emptyList())

        /**
         * Parse and validate the corpus JSON. Throws [IllegalArgumentException] if any chunk is
         * missing a required field, has no tags, or if ids are not unique — catching a broken
         * ingestion run before it ships.
         */
        fun fromJson(raw: String): KnowledgeCorpus {
            val dto = json.decodeFromString(CorpusDto.serializer(), raw)
            val chunks = dto.chunks.map { c ->
                require(c.id.isNotBlank()) { "chunk has blank id" }
                require(c.title.isNotBlank()) { "chunk ${c.id} has blank title" }
                require(c.source.isNotBlank()) { "chunk ${c.id} has blank source" }
                require(c.text.isNotBlank()) { "chunk ${c.id} has blank text" }
                require(c.tags.isNotEmpty()) { "chunk ${c.id} has no tags" }
                KnowledgeChunk(c.id, c.title, c.tags, c.source, c.text)
            }
            val ids = chunks.map { it.id }
            require(ids.toSet().size == ids.size) { "corpus has duplicate chunk ids" }
            return KnowledgeCorpus(chunks)
        }
    }
}
