# Cloud AI Knowledge Base — Design

**Date:** 2026-06-14
**Branch:** `feat/cloud-ai-knowledge-base`
**Status:** Approved design, pending implementation plan

## Problem

The app has two AI backends (on-device Gemma, and a user-configured OpenAI-compatible
cloud endpoint). The user can switch between them. This work targets **the cloud backend only**.

The cloud model serves two features: **insight cards** (single-turn) and **coach chat**
(multi-turn with 6 tools). Today its answers fall short in three ways:

1. **Generic / textbook** — correct but shallow, not tailored in depth.
2. **Wrong / made-up numbers** — hallucinated macros and calorie counts.
3. **Shallow on training/diet** — genuine knowledge gaps; vague or low-confidence answers.

The goal is to make the cloud model *specialised* — knowing far more about foods, training,
and dieting — and accurate with numbers.

## Decisive constraint: the target model is small

For public release the cloud endpoint will run a **small 20–30B free-tier model**
(e.g. `openai/gpt-oss-20b:free`). This class of model shares the limitations already
documented for the local Gemma 2B: tight context window, unreliable multi-tool iteration,
and real knowledge gaps. **Every design decision assumes the model is weak**: the app must
do the work, never relying on the model to be clever (e.g. to decide to call a knowledge tool).

Fine-tuning is **off the table** — the endpoint is a user-configured, swappable model the
app does not own.

## Approach chosen

Three levers were considered:

- **A. Curated knowledge packs + hard grounding** *(chosen for v1)* — ship a curated corpus
  in the APK; the *app* retrieves the relevant slice with keyword matching and injects it into
  the prompt. Separately, a strict prompt rule forces food numbers to come from the food library.
- **B. On-device semantic RAG** *(future upgrade)* — same idea, semantic retrieval (embeddings +
  vector store). Heaviest build; deferred. Lives behind the same interface as A, so it is a
  drop-in replacement, not a rewrite.
- **C. Pure prompt engineering** *(rejected)* — no retrieval; cannot cover breadth, and a small
  model's context is already crowded by the data snapshot, tools, and profile.

**Core principle: the app does retrieval, not the model.** On every coach question the app
silently finds the relevant knowledge and hands it to the model already in the prompt — the
same pattern the local Gemma coach already uses for its pre-fetched data snapshot.

**Insight that drives the design:** corpus *size* is nearly irrelevant (text is tiny in an APK).
The binding constraint is the model's context window, so only a small, highly-relevant slice can
be injected per question. Therefore **retrieval quality matters far more than corpus size.**

## Content source

**Guiding principle: ship distilled notes, not verbatim text.** Shipping verbatim chunks of
copyrighted material inside the APK is both legally risky (redistribution) and lower quality
(messy PDF extraction noise hurts a small model). Instead, the corpus is composed of original,
condensed, **cited** notes — e.g. *"ISSN concludes 1.4–2.0 g/kg protein supports muscle gain
[ISSN 2017]."* Trusted books are *what we distill from*, not *what we ship*. The sole exception
is openly-licensed / public-domain sources, where verbatim text is legally fine.

**Sourcing tiers (best → fallback):**

1. **Open-access authoritative** *(v1 starter corpus)* — ISSN position stands (open-access,
   written as evidence summaries: protein, nutrient timing, creatine, caffeine, meal frequency,
   diet & body composition), US Dietary Guidelines / Dietary Reference Intakes (public domain),
   USDA FoodData Central (public domain; also the source for any later food-DB expansion).
   Legally shippable, high signal, requires no input from the user to begin.
2. **User's trusted books/articles, distilled** — user supplies PDFs in `knowledge-sources/`;
   they are read and condensed into original cited notes (never shipped verbatim).
3. **Synthesis from established consensus** — gap-filler only, for topics no clean source covers;
   drafted then user-verified.

**v1 decision: build the starter corpus from Tier 1 now; layer in Tier-2 distilled notes from the
user's own books afterward.** This delivers real content immediately without blocking on the user
gathering material. The user remains the final accuracy/trust gate on all chunks.

This requires an offline **ingestion step** that converts sources into the shipped corpus.

## Architecture

A new self-contained subsystem, `ai/knowledge/`, with three pieces behind clean interfaces:

```
KnowledgeCorpus      The shipped content. Pre-chunked entries in assets/knowledge/corpus.json,
                     each: { id, title, tags, source (provenance), text }.

KnowledgeRetriever   interface: retrieve(query, k) -> List<KnowledgeChunk>
   ├─ KeywordKnowledgeRetriever   v1: pure-Kotlin term scoring, no Android deps, unit-testable
   └─ SemanticKnowledgeRetriever  future: embeddings + vector store, same interface

KnowledgeInjector    Hooks into the existing prompt builders. Retrieves top-k for the current
                     question, appends a capped "=== REFERENCE ===" block with source citations.
                     Injects nothing if nothing scores above the relevance floor.
```

Plus an **offline ingestion step** (dev-time only, not shipped): source PDFs/articles →
extract text → chunk by section → tag + record provenance → emit `assets/knowledge/corpus.json`.

### Two distinct fixes, cleanly separable

- **"Wrong numbers"** → a *grounding rule* + reliable `search_food_library` (lightweight prompt
  change in `CoachToolsAdapter.buildPrompt()`, no new infrastructure).
- **"Generic / shallow"** → the *knowledge injection* subsystem above.

## Data flow & token budgeting

**Coach chat — per message:**

```
User sends message
   → KnowledgeInjector.retrieve(message text, k=3)
   → KeywordRetriever scores every chunk; keeps those above a relevance floor
   → top chunks (capped at ~800 tokens total) formatted as a REFERENCE block
   → block attached to THIS turn only, then model answers
```

Budget discipline is the critical rule for a small model. The context already holds the
system prompt + 6 tool schemas + today's data snapshot + profile + chat history. Therefore:

- The knowledge block is **hard-capped** (≈800 tokens / top 2–3 chunks).
- It is **not permanently glued into history** — each turn gets a fresh block for the *current*
  question; older blocks are dropped from what is resent, so multi-turn conversations don't
  balloon past the window.
- If nothing clears the **relevance floor**, **inject nothing** — an irrelevant reference block
  actively hurts a small model. Silence beats noise.

Each injected chunk carries its **source line**, so the model can cite
(*"protein around 1.6–2.2 g/kg ([Source])"*) — fixing shallowness and building user trust at once.

**Insight cards:** retrieval keyed off the insight *type* (e.g. RecoveryReadiness → recovery/sleep
chunks), injected into `InsightPromptBuilder` output. Same cap, same floor.

## Feature scope

| Feature | Inject knowledge? | Rationale |
|---|---|---|
| **Coach chat** | Yes — primary target | Free-form Q&A is where "generic/shallow" hurts most. |
| **Insight cards** | Yes — lighter, type-keyed | Optional; may ship in a fast-follow to keep v1 tight. |
| **Weekly briefing / recipe naming** | No | They narrate the user's own data or name a dish; external knowledge adds nothing and burns context. |

**v1 = knowledge injection for coach chat + the grounding rule.**
Insights-injection and food-DB expansion are explicitly staged as fast-follows.

### Numbers-grounding fix (lightweight)

"Wrong macros" is a *discipline* problem, not a knowledge problem. Fix it in the prompt:

- Hard rule in `CoachToolsAdapter.buildPrompt()`: *"For any food's calories or macros, you MUST
  use `search_food_library`. Never estimate numbers from memory. If a food isn't found, say so
  and ask the user."*
- Rides on existing infrastructure — no new components. Its only dependency is decent food-library
  coverage.
- Expanding the food DB (e.g. a USDA subset) is a possible follow-on if lookups miss too often;
  **out of scope for v1**.

## Testing strategy

| Unit | How it's tested |
|---|---|
| `KeywordKnowledgeRetriever` | Pure Kotlin, no Android deps → unit tests. Given a fixed corpus + query, assert expected chunks rank top; below-floor queries return empty. Heaviest coverage — this is the quality lever. |
| `KnowledgeInjector` | Formatting + token cap (oversized chunk set is trimmed) + "nothing relevant → empty block" path. |
| Prompt builders | REFERENCE block present when chunks exist, absent when they don't; grounding-rule text present. |
| Corpus integrity | Load `corpus.json`; validate every entry has id/title/source/text and non-empty tags — catches a broken ingestion run before it ships. |

## Ingestion workflow (dev-time, never on-device)

```
knowledge-sources/          ← distilled notes (.md) + openly-licensed raw sources
                              (default: git-tracked; gitignore any copyright-restricted raw PDF)
   ├─ issn-protein.md       ← Tier 1: distilled cited notes from open-access position stands
   ├─ dietary-guidelines.md ← Tier 1
   └─ (user book notes).md  ← Tier 2: added later, distilled from trusted books
        │  ingestion script (standalone, run manually when sources change)
        ▼
   chunk by section (~150–400 tokens) → tag → attach provenance
        │
        ▼
app/src/main/assets/knowledge/corpus.json   ← shipped artifact
```

Because the corpus is authored as clean distilled markdown notes (not raw PDF extraction),
the ingestion script's job is primarily chunking + tagging + provenance, not heavy text cleanup.
Raw PDF text extraction is only needed for the rare openly-licensed source we ship closer to verbatim.

- **Chunking by section/heading**, ~150–400 tokens each — small enough to inject 2–3, large
  enough to be self-contained.
- **Provenance mandatory** per chunk (model can cite; sources can be traced/removed later).
- Script is **re-runnable and deterministic** — add a source, re-run, commit the new `corpus.json`.
- Script implementation language chosen at planning time (small Kotlin/JVM or Python tool —
  whichever keeps the toolchain simplest). It is dev-tooling, not app code.

## Key integration points (existing code)

- `ai/CloudCoachCoordinator.kt` — multi-turn cloud coach; where the per-message REFERENCE block
  is attached.
- `ai/CoachToolsAdapter.kt` (`buildPrompt()`) — coach system prompt; where the grounding rule lands.
- `ai/CloudInsightCoordinator.kt` / `ai/InsightPromptBuilder.kt` — insight injection (fast-follow).
- `data/remote/OpenAiCompatClient.kt` — cloud HTTP path (unchanged; consumes the enriched prompt).
- `core/AppContainer.kt` — DI wiring for the new `ai/knowledge/` components.

## Out of scope

- Any change to the local Gemma backend.
- Semantic / vector retrieval (Option B) — deferred behind the `KnowledgeRetriever` interface.
- Food-database expansion (USDA subset) — fast-follow only if enforcement proves insufficient.
- Knowledge injection for weekly briefing and recipe naming.
- Fine-tuning (not possible — endpoint is a user-owned, swappable model).
