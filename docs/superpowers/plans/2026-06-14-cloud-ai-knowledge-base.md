# Cloud AI Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the cloud coach domain knowledge by having the *app* retrieve relevant curated notes per question and inject them into the prompt, plus a hard rule that forces food numbers to come from the food library.

**Architecture:** A new pure-Kotlin `ai/knowledge/` subsystem — a parsed `KnowledgeCorpus`, a `KnowledgeRetriever` interface with a keyword implementation, and a `KnowledgeInjector` that builds a capped REFERENCE block. `CloudCoachCoordinator` injects a fresh block per user turn (dropping the prior turn's block so context doesn't balloon). Content is a small **seed corpus** built from markdown notes by an offline Python script; real content authoring is a separate later session. Everything degrades to a no-op if the corpus asset is missing.

**Tech Stack:** Kotlin, kotlinx.serialization (already a dependency), JUnit4 + kotlinx-coroutines-test (existing test stack), Python 3 stdlib (dev-time ingestion script only — not app code).

**Spec:** `docs/superpowers/specs/2026-06-14-cloud-ai-knowledge-base-design.md`

**Branch:** `feat/cloud-ai-knowledge-base` (already checked out)

---

## File Structure

**New app code** (`app/src/main/java/com/zack/recomptracker/ai/knowledge/`):
- `KnowledgeCorpus.kt` — `KnowledgeChunk` data class + `KnowledgeCorpus` with `fromJson()` parse+validate.
- `KnowledgeRetriever.kt` — `ScoredChunk` + `KnowledgeRetriever` interface.
- `KeywordKnowledgeRetriever.kt` — pure keyword-scoring implementation.
- `KnowledgeInjector.kt` — `KnowledgeInjector` interface + `NoOpKnowledgeInjector`.
- `RetrievalKnowledgeInjector.kt` — production injector (retrieve → format → cap).

**Modified app code:**
- `ai/CloudCoachCoordinator.kt` — add `knowledgeInjector` param + per-turn injection.
- `ai/CoachToolsAdapter.kt` — extract guidelines to a constant + add the grounding rule.
- `core/AppContainer.kt` — build the injector from assets, pass it to the coordinator.

**New content + tooling (repo root):**
- `knowledge-sources/*.md` — seed distilled notes (also test fixtures for the ingestion script).
- `tools/ingest_knowledge.py` — markdown → `corpus.json` builder.
- `app/src/main/assets/knowledge/corpus.json` — generated, shipped artifact.

**New tests** (`app/src/test/java/com/zack/recomptracker/ai/knowledge/`):
- `KnowledgeCorpusTest.kt`, `KeywordKnowledgeRetrieverTest.kt`, `RetrievalKnowledgeInjectorTest.kt`, `KnowledgeCorpusIntegrityTest.kt`.
- Modify `ai/CloudCoachCoordinatorTest.kt` (injection wiring) and add `ai/CoachPromptGuidelinesTest.kt`.

---

## Task 1: KnowledgeCorpus + parsing/validation

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpus.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusTest.kt`:

```kotlin
package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnowledgeCorpusTest {

    private val validJson = """
        {"chunks":[
          {"id":"protein-1","title":"Protein target","tags":["protein"],"source":"ISSN 2017","text":"Aim 1.6-2.2 g/kg."},
          {"id":"sleep-1","title":"Sleep and recovery","tags":["sleep","recovery"],"source":"NIH","text":"7-9 hours supports recovery."}
        ]}
    """.trimIndent()

    @Test
    fun `valid json parses all chunks`() {
        val corpus = KnowledgeCorpus.fromJson(validJson)
        assertEquals(2, corpus.chunks.size)
        assertEquals("protein-1", corpus.chunks.first().id)
        assertEquals(listOf("sleep", "recovery"), corpus.chunks[1].tags)
    }

    @Test
    fun `blank required field throws`() {
        val bad = """{"chunks":[{"id":"x","title":"","tags":["t"],"source":"s","text":"body"}]}"""
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }

    @Test
    fun `empty tags throws`() {
        val bad = """{"chunks":[{"id":"x","title":"T","tags":[],"source":"s","text":"body"}]}"""
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }

    @Test
    fun `duplicate ids throw`() {
        val bad = """
            {"chunks":[
              {"id":"x","title":"A","tags":["t"],"source":"s","text":"a"},
              {"id":"x","title":"B","tags":["t"],"source":"s","text":"b"}
            ]}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { KnowledgeCorpus.fromJson(bad) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*KnowledgeCorpusTest"`
Expected: FAIL — `KnowledgeCorpus` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpus.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*KnowledgeCorpusTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpus.kt \
        app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusTest.kt
git commit -m "feat(knowledge): add KnowledgeCorpus with parse + validation"
```

---

## Task 2: KeywordKnowledgeRetriever

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeRetriever.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetriever.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetrieverTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetrieverTest.kt`:

```kotlin
package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordKnowledgeRetrieverTest {

    private val corpus = listOf(
        KnowledgeChunk("protein-1", "Daily protein target", listOf("protein", "muscle"),
            "ISSN", "Aim for 1.6 to 2.2 grams of protein per kilogram for muscle gain."),
        KnowledgeChunk("sleep-1", "Sleep and recovery", listOf("sleep", "recovery"),
            "NIH", "Seven to nine hours of sleep supports recovery and performance."),
        KnowledgeChunk("creatine-1", "Creatine basics", listOf("creatine", "supplement"),
            "ISSN", "Creatine monohydrate at five grams daily improves strength."),
    )

    private val retriever = KeywordKnowledgeRetriever(corpus)

    @Test
    fun `query ranks the most relevant chunk first`() {
        val hits = retriever.retrieve("how much protein for muscle?", 3)
        assertEquals("protein-1", hits.first().chunk.id)
    }

    @Test
    fun `tag match contributes to score`() {
        val hits = retriever.retrieve("creatine", 3)
        assertEquals("creatine-1", hits.first().chunk.id)
    }

    @Test
    fun `irrelevant query returns no hits above the floor`() {
        val hits = retriever.retrieve("what time does the gym open downtown", 3)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `empty query returns empty`() {
        assertTrue(retriever.retrieve("", 3).isEmpty())
    }

    @Test
    fun `k limits the number of results`() {
        val hits = retriever.retrieve("protein sleep creatine recovery", 1)
        assertEquals(1, hits.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*KeywordKnowledgeRetrieverTest"`
Expected: FAIL — `KeywordKnowledgeRetriever` / `ScoredChunk` unresolved.

- [ ] **Step 3a: Write the interface**

Create `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeRetriever.kt`:

```kotlin
package com.zack.recomptracker.ai.knowledge

/** A chunk paired with its relevance score for a query. */
data class ScoredChunk(val chunk: KnowledgeChunk, val score: Double)

/** Ranks corpus chunks against a free-text query. */
interface KnowledgeRetriever {
    /** Top [k] chunks scoring above the relevance floor, highest score first. May be empty. */
    fun retrieve(query: String, k: Int): List<ScoredChunk>
}
```

- [ ] **Step 3b: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetriever.kt`:

```kotlin
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
    private val minScore: Double = 1.0,
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
        )

        fun tokenize(s: String): List<String> =
            s.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 && it !in STOPWORDS }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*KeywordKnowledgeRetrieverTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeRetriever.kt \
        app/src/main/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetriever.kt \
        app/src/test/java/com/zack/recomptracker/ai/knowledge/KeywordKnowledgeRetrieverTest.kt
git commit -m "feat(knowledge): add keyword retriever with relevance floor"
```

---

## Task 3: RetrievalKnowledgeInjector

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeInjector.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjector.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjectorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjectorTest.kt`:

```kotlin
package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalKnowledgeInjectorTest {

    /** Returns a fixed list regardless of query, so injector formatting/capping is tested in isolation. */
    private class FakeRetriever(private val hits: List<ScoredChunk>) : KnowledgeRetriever {
        override fun retrieve(query: String, k: Int): List<ScoredChunk> = hits.take(k)
    }

    private fun chunk(id: String, title: String, text: String) =
        ScoredChunk(KnowledgeChunk(id, title, listOf("t"), "SRC-$id", text), 5.0)

    @Test
    fun `no hits produces empty block`() {
        val injector = RetrievalKnowledgeInjector(FakeRetriever(emptyList()))
        assertEquals("", injector.referenceBlock("anything"))
    }

    @Test
    fun `block contains title source and markers`() {
        val injector = RetrievalKnowledgeInjector(FakeRetriever(listOf(chunk("a", "Protein", "Eat protein."))))
        val block = injector.referenceBlock("protein")
        assertTrue(block.contains("REFERENCE KNOWLEDGE"))
        assertTrue(block.contains("END REFERENCE"))
        assertTrue(block.contains("Protein"))
        assertTrue(block.contains("Source: SRC-a"))
    }

    @Test
    fun `char budget drops later chunks`() {
        val big = "x".repeat(1500)
        val injector = RetrievalKnowledgeInjector(
            FakeRetriever(listOf(chunk("a", "First", big), chunk("b", "Second", big))),
            maxChunks = 3,
            maxChars = 2000,
        )
        val block = injector.referenceBlock("q")
        assertTrue(block.contains("First"))
        assertFalse(block.contains("Second"))
    }

    @Test
    fun `oversized single top chunk is truncated`() {
        val huge = "y".repeat(5000)
        val injector = RetrievalKnowledgeInjector(
            FakeRetriever(listOf(chunk("a", "Big", huge))),
            maxChars = 2000,
        )
        val block = injector.referenceBlock("q")
        assertTrue(block.contains("Big"))
        assertTrue(block.contains("…"))
        assertTrue(block.length < 2200)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*RetrievalKnowledgeInjectorTest"`
Expected: FAIL — `RetrievalKnowledgeInjector` / `KnowledgeInjector` unresolved.

- [ ] **Step 3a: Write the interface + no-op**

Create `app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeInjector.kt`:

```kotlin
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
```

- [ ] **Step 3b: Write the implementation**

Create `app/src/main/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjector.kt`:

```kotlin
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
            // The top chunk is always included (truncated if it alone exceeds the budget);
            // later chunks are dropped once the running total would exceed the budget.
            val body = if (index == 0 && c.text.length > maxChars) {
                c.text.take(maxChars).trimEnd() + "…"
            } else {
                c.text
            }
            val line = "[${index + 1}] ${c.title} — $body (Source: ${c.source})"
            if (index > 0 && used + line.length > maxChars) break
            lines.add(line)
            used += line.length
        }

        return buildString {
            appendLine("=== REFERENCE KNOWLEDGE (use to inform your answer; cite the source) ===")
            lines.forEach { appendLine(it) }
            append("=== END REFERENCE ===")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*RetrievalKnowledgeInjectorTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/knowledge/KnowledgeInjector.kt \
        app/src/main/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjector.kt \
        app/src/test/java/com/zack/recomptracker/ai/knowledge/RetrievalKnowledgeInjectorTest.kt
git commit -m "feat(knowledge): add capped reference-block injector"
```

---

## Task 4: Seed corpus + ingestion script + integrity test

**Files:**
- Create: `knowledge-sources/protein-intake.md`
- Create: `knowledge-sources/recovery-sleep.md`
- Create: `knowledge-sources/energy-balance.md`
- Create: `tools/ingest_knowledge.py`
- Generate: `app/src/main/assets/knowledge/corpus.json`
- Test: `app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusIntegrityTest.kt`

- [ ] **Step 1: Create the seed source notes**

Create `knowledge-sources/protein-intake.md`:

```markdown
---
source: ISSN Position Stand — Protein and Exercise (2017)
tags: [protein, muscle, recomposition]
---

## Daily protein target for muscle gain
For building or retaining muscle during body recomposition, aim for roughly 1.6 to 2.2 grams of protein per kilogram of bodyweight per day. Intakes in this range maximize muscle protein synthesis for most trainees; going higher offers little additional benefit for muscle.

## Protein distribution across the day
Spreading protein across three to four meals of about 0.4 grams per kilogram each is a practical way to support muscle protein synthesis throughout the day. Total daily protein matters most, but even distribution is a reasonable default.
```

Create `knowledge-sources/recovery-sleep.md`:

```markdown
---
source: NIH / Sleep Foundation guidance
tags: [sleep, recovery, fatigue]
---

## Sleep duration for recovery
Most adults need seven to nine hours of sleep per night. Consistently getting less impairs recovery, raises perceived effort in training, and can blunt muscle gain and fat loss. Prioritize sleep when recovery scores are low.

## Managing high soreness and low energy
Persistent soreness, low energy, and poor sleep together signal under-recovery. Reducing training volume for a few days, ensuring adequate protein and calories, and improving sleep usually restores readiness faster than pushing through.
```

Create `knowledge-sources/energy-balance.md`:

```markdown
---
source: Dietary Guidelines / energy balance basics
tags: [calories, deficit, surplus, fat-loss]
---

## Energy balance and body recomposition
Fat loss requires eating fewer calories than you burn; muscle gain is supported by adequate protein and roughly maintenance-or-slightly-above calories. A moderate deficit of about 300 to 500 calories per day supports fat loss while preserving muscle when protein is high and training is hard.

## Rate of weight change
A sustainable rate of fat loss is about 0.5 to 1 percent of bodyweight per week. Faster loss raises the risk of muscle loss and low energy. If weight is dropping much faster than this, a small calorie increase is usually warranted.
```

- [ ] **Step 2: Write the ingestion script**

Create `tools/ingest_knowledge.py`:

```python
#!/usr/bin/env python3
"""Build app/src/main/assets/knowledge/corpus.json from distilled markdown notes.

Each file in knowledge-sources/ has frontmatter (source, tags) between leading '---' lines and
one or more '## ' sections. Every non-empty section becomes one corpus chunk. Stdlib only;
re-runnable and deterministic (chunks sorted by id).

Usage: python3 tools/ingest_knowledge.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "knowledge-sources")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "knowledge", "corpus.json")


def slugify(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def parse_frontmatter(text):
    m = re.match(r"^---\n(.*?)\n---\n(.*)$", text, re.DOTALL)
    if not m:
        raise ValueError("missing frontmatter")
    meta_block, body = m.group(1), m.group(2)
    meta = {}
    for line in meta_block.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    source = meta.get("source", "").strip()
    raw_tags = meta.get("tags", "").strip().strip("[]")
    tags = [t.strip() for t in raw_tags.split(",") if t.strip()]
    return source, tags, body


def split_sections(body):
    sections = []
    title = None
    lines = []
    for line in body.splitlines():
        if line.startswith("## "):
            if title is not None:
                sections.append((title, "\n".join(lines).strip()))
            title = line[3:].strip()
            lines = []
        elif title is not None:
            lines.append(line)
    if title is not None:
        sections.append((title, "\n".join(lines).strip()))
    return sections


def main():
    if not os.path.isdir(SRC_DIR):
        print("no source dir: %s" % SRC_DIR, file=sys.stderr)
        sys.exit(1)
    chunks = []
    for fname in sorted(os.listdir(SRC_DIR)):
        if not fname.endswith(".md"):
            continue
        with open(os.path.join(SRC_DIR, fname), encoding="utf-8") as f:
            text = f.read()
        source, tags, body = parse_frontmatter(text)
        file_slug = slugify(fname[:-3])
        for i, (title, section_text) in enumerate(split_sections(body), start=1):
            if not section_text:
                continue
            chunks.append({
                "id": "%s-%d" % (file_slug, i),
                "title": title,
                "tags": tags,
                "source": source,
                "text": section_text,
            })
    chunks.sort(key=lambda c: c["id"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"chunks": chunks}, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote %d chunks to %s" % (len(chunks), OUT))


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Run the script to generate the corpus**

Run: `python3 tools/ingest_knowledge.py`
Expected: prints `wrote 6 chunks to .../app/src/main/assets/knowledge/corpus.json`, and the file exists.

Verify it parses as JSON and has 6 chunks:
Run: `python3 -c "import json;d=json.load(open('app/src/main/assets/knowledge/corpus.json'));print(len(d['chunks']), [c['id'] for c in d['chunks']])"`
Expected: `6 ['energy-balance-1', 'energy-balance-2', 'protein-intake-1', 'protein-intake-2', 'recovery-sleep-1', 'recovery-sleep-2']`

- [ ] **Step 4: Write the integrity test**

Create `app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusIntegrityTest.kt`:

```kotlin
package com.zack.recomptracker.ai.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the SHIPPED corpus.json and runs it through full validation. Gradle runs unit tests with
 * the module dir (app/) as the working directory, so the asset is read by relative path. This
 * catches a broken ingestion run (blank field, missing tags, duplicate id) before it ships.
 */
class KnowledgeCorpusIntegrityTest {

    @Test
    fun `shipped corpus parses and passes validation`() {
        val file = File("src/main/assets/knowledge/corpus.json")
        assertTrue("corpus.json missing at ${file.absolutePath}", file.exists())
        val corpus = KnowledgeCorpus.fromJson(file.readText())
        assertTrue("corpus is empty", corpus.chunks.isNotEmpty())
    }
}
```

- [ ] **Step 5: Run the integrity test**

Run: `./gradlew :app:testDebugUnitTest --tests "*KnowledgeCorpusIntegrityTest"`
Expected: PASS (1 test). (`fromJson` throwing on any invalid chunk means a green test == valid corpus.)

- [ ] **Step 6: Commit**

```bash
git add knowledge-sources tools/ingest_knowledge.py \
        app/src/main/assets/knowledge/corpus.json \
        app/src/test/java/com/zack/recomptracker/ai/knowledge/KnowledgeCorpusIntegrityTest.kt
git commit -m "feat(knowledge): add seed corpus, ingestion script, integrity test"
```

---

## Task 5: Inject knowledge into CloudCoachCoordinator

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt`

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt`, add the import at the top (with the other imports):

```kotlin
import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
```

Change `ScriptedClient` to also capture the messages of the last completion. Replace its body with:

```kotlin
    private class ScriptedClient(private val responses: ArrayDeque<ParsedChatResponse>) : OpenAiCompatClient() {
        var lastToolSchemas: List<String> = emptyList()
        var lastMessages: List<ChatRequestMessage> = emptyList()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            lastToolSchemas = toolSchemasJson
            lastMessages = messages
            return responses.removeFirst()
        }
    }
```

Add this fake and these two tests to the class:

```kotlin
    private class FixedInjector(private val block: String) : KnowledgeInjector {
        override fun referenceBlock(query: String): String = block
    }

    @Test
    fun `knowledge reference block is injected before the user message`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
        val block = "=== REFERENCE KNOWLEDGE ===\n[1] Protein — eat protein (Source: ISSN)\n=== END REFERENCE ==="
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope, FixedInjector(block))
        advanceUntilIdle()
        coach.sendMessage("how much protein?")
        advanceUntilIdle()
        val msgs = client.lastMessages
        val refIdx = msgs.indexOfFirst { it.role == "system" && it.content?.contains("REFERENCE KNOWLEDGE") == true }
        val userIdx = msgs.indexOfFirst { it.role == "user" && it.content == "how much protein?" }
        assertTrue("reference block present", refIdx >= 0)
        assertTrue("reference precedes user message", refIdx < userIdx)
        scope.cancel()
    }

    @Test
    fun `blank injector adds no reference message`() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val client = ScriptedClient(ArrayDeque(listOf(ParsedChatResponse("Answer.", emptyList()))))
        val coach = CloudCoachCoordinator(flowOf(true), config(), client, FakeExecutor(), scope, FixedInjector(""))
        advanceUntilIdle()
        coach.sendMessage("hi")
        advanceUntilIdle()
        val refCount = client.lastMessages.count { it.content?.contains("REFERENCE KNOWLEDGE") == true }
        assertEquals(0, refCount)
        scope.cancel()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorTest"`
Expected: FAIL — the 6-arg `CloudCoachCoordinator(...)` constructor does not exist yet.

- [ ] **Step 3: Implement injection in the coordinator**

In `CloudCoachCoordinator.kt`, add the import (with the other imports near the top):

```kotlin
import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
```

Add the constructor parameter as the last parameter (it has a default, so existing 5-arg call sites keep working). Change the constructor signature from:

```kotlin
class CloudCoachCoordinator(
    cloudReadyFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val tools: CoachReadTools,
    private val scope: CoroutineScope,
) : CoachCoordinator {
```

to:

```kotlin
class CloudCoachCoordinator(
    cloudReadyFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val tools: CoachReadTools,
    private val scope: CoroutineScope,
    private val knowledgeInjector: KnowledgeInjector = NoOpKnowledgeInjector,
) : CoachCoordinator {
```

Add a field next to the other private fields (after `private var systemSeeded = false`):

```kotlin
    // The previous turn's injected reference message, dropped before injecting the next turn's so
    // multi-turn context never accumulates reference blocks.
    private var lastReferenceMessage: ChatRequestMessage? = null
```

In `handleMessage`, replace this block:

```kotlin
                if (!systemSeeded) {
                    requestMessages.add(ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot()))
                    systemSeeded = true
                }
                requestMessages.add(ChatRequestMessage(role = "user", content = userText))
```

with:

```kotlin
                if (!systemSeeded) {
                    requestMessages.add(ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot()))
                    systemSeeded = true
                }
                // Refresh the per-turn knowledge block: drop the previous turn's block, inject a
                // fresh one for THIS question, positioned immediately before the user message.
                lastReferenceMessage?.let { requestMessages.remove(it) }
                val reference = knowledgeInjector.referenceBlock(userText)
                lastReferenceMessage = if (reference.isNotBlank()) {
                    ChatRequestMessage(role = "system", content = reference).also { requestMessages.add(it) }
                } else {
                    null
                }
                requestMessages.add(ChatRequestMessage(role = "user", content = userText))
```

Now reset the reference tracker everywhere `requestMessages` is reset. There are three spots inside `handleMessage` that do `requestMessages.clear()` + `systemSeeded = false` (the MAX_TOOL_ROUNDS branch, the `TimeoutCancellationException` catch, and the generic `Exception` catch). After the `systemSeeded = false` line in EACH of those three spots, add:

```kotlin
                        lastReferenceMessage = null
```

(Match the existing indentation of each spot — the MAX_TOOL_ROUNDS branch is more deeply indented than the two catch blocks.)

Finally, in `clearHistory()`, inside the `turnLock.withLock { ... }` block, after `systemSeeded = false`, add:

```kotlin
                lastReferenceMessage = null
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorTest"`
Expected: PASS — all original tests plus the 2 new ones (6 total).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt \
        app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorTest.kt
git commit -m "feat(knowledge): inject per-turn reference block into cloud coach"
```

---

## Task 6: Numbers-grounding rule in the coach prompt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptGuidelinesTest {

    @Test
    fun `guidelines force food lookups and forbid estimating`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("search_food_library"))
        assertTrue(COACH_PROMPT_GUIDELINES.lowercase().contains("never estimate"))
    }

    @Test
    fun `guidelines allow reference knowledge as a source`() {
        assertTrue(COACH_PROMPT_GUIDELINES.contains("REFERENCE KNOWLEDGE"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachPromptGuidelinesTest"`
Expected: FAIL — `COACH_PROMPT_GUIDELINES` unresolved.

- [ ] **Step 3: Extract guidelines to a constant and add the grounding rule**

In `CoachToolsAdapter.kt`, add this top-level constant just below the imports (above the class declaration):

```kotlin
/**
 * Static coach guidelines appended to every system prompt. Extracted as a constant so the
 * grounding rules are unit-testable without constructing the full adapter. The food-lookup rule
 * is the fix for the cloud model inventing macros; the REFERENCE KNOWLEDGE clause lets it use the
 * injected knowledge block as a legitimate source.
 */
internal const val COACH_PROMPT_GUIDELINES: String =
    "- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.\n" +
        "- For any food's calories or macros you do not already have, you MUST call search_food_library to get them — never estimate numbers from memory. If a food is not found, say so and ask the user.\n" +
        "- Use markdown when it improves clarity (short lists, bold key numbers). Answer only from logged data, tool results, and any REFERENCE KNOWLEDGE provided; never invent numbers.\n" +
        "- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.\n" +
        "- Stay on topic: nutrition, body composition, training, and recovery."
```

In `buildPrompt`, replace this tail of the function:

```kotlin
        appendLine("Guidelines:")
        appendLine("- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.")
        appendLine("- Use markdown when it improves clarity (short lists, bold key numbers). Answer only from logged data and tool results; never invent numbers.")
        appendLine("- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.")
        append("- Stay on topic: nutrition, body composition, training, and recovery.")
```

with:

```kotlin
        appendLine("Guidelines:")
        append(COACH_PROMPT_GUIDELINES)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachPromptGuidelinesTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt \
        app/src/test/java/com/zack/recomptracker/ai/CoachPromptGuidelinesTest.kt
git commit -m "feat(knowledge): force food-library lookups via coach grounding rule"
```

---

## Task 7: Wire the injector in AppContainer + full build

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add imports**

In `AppContainer.kt`, add these imports with the other `com.zack.recomptracker.ai.*` imports:

```kotlin
import com.zack.recomptracker.ai.knowledge.KeywordKnowledgeRetriever
import com.zack.recomptracker.ai.knowledge.KnowledgeCorpus
import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.ai.knowledge.RetrievalKnowledgeInjector
```

- [ ] **Step 2: Build the injector from assets**

In `AppContainer.kt`, immediately above the `private val cloudCoachCoordinator` declaration (around line 257), add:

```kotlin
    // Knowledge base: loaded once from assets. Degrades to a no-op if the corpus is missing or
    // invalid, so a bad ingestion run can never crash the app.
    private val knowledgeInjector: KnowledgeInjector = runCatching {
        val raw = context.applicationContext.assets
            .open("knowledge/corpus.json")
            .bufferedReader()
            .use { it.readText() }
        RetrievalKnowledgeInjector(KeywordKnowledgeRetriever(KnowledgeCorpus.fromJson(raw).chunks))
    }.getOrElse { NoOpKnowledgeInjector }
```

- [ ] **Step 3: Pass it to the coordinator**

In the `CloudCoachCoordinator(...)` constructor call, add the argument after `scope = appScope,`:

```kotlin
    private val cloudCoachCoordinator: CoachCoordinator = CloudCoachCoordinator(
        cloudReadyFlow = cloudReadyFlow,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        tools = CoachToolsAdapter(
            toolExecutor = coachToolExecutor,
            planRepository = planRepository,
            userProfileStore = userProfilePreferencesStore,
            dateProvider = dateProvider,
            handoffStore = coachHandoffStore,
        ),
        scope = appScope,
        knowledgeInjector = knowledgeInjector,
    )
```

- [ ] **Step 4: Type-check, full unit tests, and assemble**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass (existing suite + the new knowledge tests).

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — confirms `corpus.json` packs into the APK without issue.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(knowledge): wire knowledge injector into cloud coach DI"
```

---

## Definition of Done

- All seven tasks committed on `feat/cloud-ai-knowledge-base`.
- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` both green.
- The cloud coach injects a capped, source-cited reference block per question (verified by tests against the seed corpus), and the prompt forces food numbers through `search_food_library`.
- Swapping the seed corpus for the real corpus later = re-run `tools/ingest_knowledge.py`; no code changes.

## Out of scope (separate sessions)

- Real Tier-1/Tier-2 content authoring (gather + distill sources).
- Knowledge injection for insight cards, weekly briefing, recipe naming.
- Semantic/vector retrieval (`SemanticKnowledgeRetriever`) — slots behind `KnowledgeRetriever`.
- Food-DB (USDA) expansion.
