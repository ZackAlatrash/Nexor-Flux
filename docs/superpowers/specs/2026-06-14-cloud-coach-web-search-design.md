# Cloud Coach Web Search — Design

**Date:** 2026-06-14
**Branch:** `feat/cloud-coach-web-search` (proposed)
**Status:** Approved design, pending implementation plan

## Problem

The cloud coach answers from two sources: today's data snapshot and a **local, bundled**
knowledge base (`assets/knowledge/corpus.json`, keyword retrieval — see the
[cloud-ai-knowledge-base design](2026-06-14-cloud-ai-knowledge-base-design.md)). When a question
falls outside both — e.g. *"how many calories in a Big Mac?"* for a food not in the library, or a
current/long-tail fact the corpus never covered — the model has nothing to ground on and either
declines or hallucinates.

The goal is to give the **cloud coach** a way to **reach the live web** when its built-in
knowledge can't answer, so it can fetch the fact and reply with a cited source.

## Scope decision

Three options were weighed (food-only nutrition DB / food + general web / general web only).
**Chosen: a single general-purpose web-search tool** the model uses for any question its
knowledge can't answer, including food macros. The user accepted the known trade-off: a general
web search is weaker than a structured nutrition database for exact numeric macros. Two design
choices offset that:

1. The provider returns a **synthesized answer + cleaned page content**, not raw links, so the
   model reads real text instead of guessing from titles.
2. The tool returns **source URLs** and the prompt requires the model to **cite them**, keeping
   answers checkable.

A dedicated nutrition-DB tool remains a possible future addition behind the same interface; it is
**out of scope** for this work.

## Provider: Tavily

`search_web` is backed by the **Tavily Search API**, chosen because it is built for LLM agents:
a single endpoint returns an optional synthesized `answer` plus cleaned, extracted content with
source URLs — minimizing both the model's interpretation work and hallucination risk.

- **Cost:** Tavily's forever-free tier is **1,000 credits/month, no credit card** (basic search =
  1 credit). Far beyond a single user's coach usage. Credits do not roll over (irrelevant here).
- **Swappable:** Tavily sits behind a `WebSearchProvider` interface, the same pattern as
  `KnowledgeRetriever` → `KeywordKnowledgeRetriever`. A different search API is a drop-in later.

## Decisive constraints

- **Cloud-only.** The on-device Gemma 2B is poor at tool calls and the local backend's purpose is
  offline/private operation. The web tool is exposed to the **cloud coach only**; the local coach's
  tool list is untouched.
- **Model is mid-size, not weak.** The public cloud endpoint runs a user-configured 20–30B model.
  Unlike the 2B local model, this class does OpenAI-style function calling reliably, so — departing
  from the knowledge-base design's "app does the work, never trust the model to call a tool" rule —
  here **the model decides when to search.** One added tool schema (~150 tokens) and a capped
  result are negligible against a 20–30B model's 32k+ context.
- **Read-only, no confirmation.** `search_web` reads external data and writes nothing to the user's
  log, so it does **not** go through the write-tool confirmation flow. The model calls it mid-turn
  and the existing loop feeds the result back automatically.
- **Optional / graceful degradation.** If no Tavily key is set or the device is offline, the tool
  returns a structured error and the model tells the user it couldn't look it up. The coach remains
  fully functional without web search; it is never required for the backend to be "ready."

## Approach: model decides when to search

The tool is always present in the cloud coach's tool list. One guideline line in the coach system
prompt instructs: *"If the answer isn't in your reference knowledge or today's snapshot, call
`search_web`, then answer from the result and cite the source URL."* No fallback orchestration,
no detection of empty retrieval — the model's own tool judgment drives it. (Strict
"web only after local retrieval returns nothing" was considered and rejected: more orchestration
code, and it removes the model's ability to fact-check a stale corpus entry.)

## Architecture

A small, self-contained subsystem mirroring `ai/knowledge/`:

```
WebSearchProvider    interface: search(query: String): WebSearchResult
   └─ TavilyWebSearchProvider   v1: OkHttp call to Tavily, parses answer + results.
                                Same swappable-interface pattern as KnowledgeRetriever.

WebSearchResult      { answer: String?, results: List<WebResult> }
WebResult            { title, url, content }   ← content already cleaned/extracted by Tavily

search_web tool      New SchemaTool entry, added to the CLOUD coach's tool list only.
                     Dispatched through CoachToolExecutor like every other tool.
```

- **Result shape returned to the model** (capped JSON, mirroring the knowledge injector's caps —
  ~3 results / ~2000 chars total so the prompt stays lean):
  ```json
  { "answer": "A Big Mac is ~563 kcal...", "results": [ {"title": "...", "url": "...", "content": "..."} ] }
  ```
- **Error shape** (no key / offline / API failure): `{ "error": "web search unavailable" }`.
- **Tool schema:** `search_web(query: String)` — single required string arg. Description steers it
  toward factual lookups (food facts, nutrition, supplements, studies).

### Cloud-only tool wiring

The shared `COACH_TOOL_SCHEMAS` (in `GemmaCoachCoordinator.kt`) stays the base list used by the
local coach. The **cloud coordinator** is given `base + search_web`. `CoachToolExecutor` gains a
new `WebSearchProvider` dependency and a `search_web` branch; if the provider is absent/unconfigured
the branch returns the structured error rather than throwing.

## Data flow (coach chat, per message)

```
User sends message
   → existing knowledge injection runs (unchanged)
   → model receives message + reference block + tool schemas (incl. search_web)
   → model judges it can't answer from snapshot/knowledge
   → model emits a search_web tool call  ──► CoachToolExecutor
                                              ──► TavilyWebSearchProvider.search(query)
                                              ──► OkHttp → Tavily → capped JSON
   → result fed back into the loop (existing CloudCoachCoordinator tool loop)
   → model answers in 1–3 sentences, citing the source URL
```

No change to the tool loop, confirmation flow, routing, or the knowledge subsystem. The web result
rides the same per-turn mechanics as a knowledge block — fresh each turn, not glued into history.

## Key / settings

- **Storage:** add a `web_search_api_key` entry to `SecureKeyStore` (AES256-GCM, alongside the
  existing single `cloud_api_key`).
- **Settings UI:** one new field to paste the Tavily key, reusing the existing glass component
  library (no new composables). Lives near the existing cloud config fields (base URL / model / key).
- **Readiness:** the web key is **independent** of `cloudConfigComplete`. The coach is "ready" with
  or without it; web search simply activates when a key is present.

## Networking

- Reuses the OkHttp stack pattern from `OpenAiCompatClient`. The Tavily call gets its own modest
  timeout (~10s connect / ~15s read) — a search must not stall the 180s turn budget.
- Tavily request: `POST https://api.tavily.com/search` with `{ api_key, query, include_answer:true,
  max_results:3 }` (exact params finalized at planning time against current Tavily docs).

## Testing strategy

| Unit | How it's tested |
|---|---|
| `TavilyWebSearchProvider` | Parsing: given a recorded Tavily JSON body, assert `answer` + results extracted and capped correctly; malformed/empty body → empty result. |
| `WebSearchProvider` (fake) | Inject a fake provider into `CoachToolExecutor`; assert `search_web` returns the capped JSON contract, and the no-provider path returns `{"error":...}`. |
| `CoachToolExecutor` | `search_web` dispatch + error branch (unconfigured provider). |
| Cloud coordinator tool loop | With a stub web tool, a `search_web` call is executed and its result re-fed; model "answer" turn ends the loop. Confirm the tool is **not** in the write-confirmation path. |
| Tool list wiring | Cloud coach list includes `search_web`; local Gemma list does not. |

## Key integration points (existing code)

- `ai/CoachToolExecutor.kt` — new `WebSearchProvider` dependency + `search_web` dispatch branch.
- `ai/GemmaCoachCoordinator.kt` — base `COACH_TOOL_SCHEMAS` stays local-only; cloud list = base + web.
- `ai/CloudCoachCoordinator.kt` — receives the extended tool list (no loop changes).
- `ai/CoachToolsAdapter.kt` (`buildPrompt()` / guidelines) — one guideline line for when to search + cite.
- `data/remote/` — new `TavilyWebSearchProvider` (OkHttp), parallel to `OpenAiCompatClient`.
- `data/preferences/SecureKeyStore.kt` — `web_search_api_key`.
- `ui/` settings screen — Tavily key field (glass components).
- `core/AppContainer.kt` — DI: build the provider, inject into `CoachToolExecutor`, pass extended
  tool list to the cloud coordinator.

## Out of scope

- Any change to the **local Gemma** backend or its tool list.
- A dedicated **nutrition-database** tool (USDA / Open Food Facts) — deferred behind the same
  `WebSearchProvider`-style interface.
- **Caching** fetched foods into the food library for reuse/logging — a nice follow-on, not v1.
- Web search for **insight cards** — coach chat only.
- Multi-result page fetching / scraping beyond what Tavily returns.
