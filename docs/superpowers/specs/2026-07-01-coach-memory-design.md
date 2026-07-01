# Coach memory — user-visible, editable facts the coach knows

**Date:** 2026-07-01
**Status:** Approved (brainstorm)
**Branch:** `redesign/ai-coaching`
**Part of:** the 3-feature set (this = #2). Others: fix logging mistakes (#1, done), meal
suggestions (#3, will read this memory).

## Goal

Give the coach a **freeform memory** of user facts/preferences it doesn't get from the structured
profile — dietary restrictions, allergies, injuries, disliked lifts, equipment, context — that the
coach reads every conversation, can add to on request, and that the **user can see and edit** in a
dedicated screen.

## Scope

**In scope**
- `CoachMemoryStore`: a flat, capped list of memory entries (`{id, text, createdAt}`).
- Injected into the coach's **chat** system prompt (only when non-empty).
- Coach tools `remember(text)` (add) and `forget(text)` (remove best match) — **not confirmed**
  (low-stakes, fully reversible in the screen), per the user's choice.
- A **"Coach memory" screen**: list + add + edit + delete, reached from the AI Coach settings.

**Out of scope (MVP)**
- Categories/tags (flat list only).
- Proactive auto-capture (coach saves only when asked).
- Injecting memory into the weekly-briefing prompt (chat only for now; #3 will also read it).
- Confirmation dialogs on `remember`/`forget`.

## Existing code this builds on

- `UserProfilePreferencesStore` — the STRUCTURED profile (goal/sex/age/height/activity/gym sessions),
  already injected into the coach prompt by `CoachToolsAdapter.buildPrompt`. Memory is **separate**
  and must not duplicate these.
- `data/coach/CoachJourneyStore` (+ `CoachJourneySerialization`) — the DataStore pattern to mirror
  (interface + impl + `NoopCoachJourney`, injectable `DataStore<Preferences>`, JSON list under a key,
  rolling cap).
- `ai/CoachToolsAdapter` — builds `systemPromptSnapshot()`; already injects `journeyNarrative()` as a
  delimited block. Add the memory block the same way.
- `ai/CoachToolExecutor` — tool dispatch; `esc()`; word scorer (`scoredMealMatches`) to reuse for
  `forget` matching.
- `ai/CoachTools.kt` (`CLOUD_COACH_TOOL_SCHEMAS`, `COACH_WRITE_TOOLS`), `ai/CoachToolsAdapter`
  `COACH_PROMPT_GUIDELINES`.
- `ui/aicoach/AiCoachScreen` (+ nav in `AppNavGraph`, `Routes`) — the settings screen + nav pattern
  for adding an entry row → a new sub-screen. `ui/train/*` sub-screens show the ViewModel+screen+route
  wiring pattern.

## Data — `CoachMemoryStore` (`data/coach/`)

- `@Serializable data class CoachMemoryEntry(val id: String, val text: String, val createdAtIso: String)`.
- DataStore Preferences backed (mirror `CoachJourneyStore`): injectable `DataStore<Preferences>`
  primary ctor + `Context` secondary; JSON list under one typed key; `MAX_ENTRIES ≈ 50` (drop oldest
  on overflow). `id` generated from a stable counter or the createdAt + text hash (no `Math.random`/
  `Date.now` restriction here — this is app code, `Instant.now()` is fine via an injected clock/
  `DateProvider` for testability; entry `id` can be a monotonic string).
- Interface `CoachMemory` + `NoopCoachMemory` (inert). API:
  - `fun observe(): Flow<List<CoachMemoryEntry>>` (for the UI)
  - `suspend fun all(): List<CoachMemoryEntry>` (for prompt injection)
  - `suspend fun add(text: String): CoachMemoryEntry` (trimmed; ignores blank; dedupe exact-duplicate text)
  - `suspend fun update(id: String, text: String)`
  - `suspend fun delete(id: String)`
  - `suspend fun removeMatching(query: String): CoachMemoryEntry?` (best fuzzy match for `forget`; null if none)
- Lives in `data/coach` → the `AiCoachBoundaryTest` guard covers it (no `ai/local` imports).

## Coach side

**Prompt injection (`CoachToolsAdapter`):** read `coachMemory.all()`; if non-empty, add a block to
`systemPromptSnapshot()`:
```
=== WHAT I KNOW ABOUT YOU ===
- <entry text>
- <entry text>
=== END ===
```
Placed near the profile block. Omitted entirely (prompt byte-identical) when empty.

**Tools (`CoachToolExecutor`):**
- `remember(text)` → `coachMemory.add(text)` → `{"success":true,"remembered":"<text>"}` (or error if blank).
- `forget(text)` → `coachMemory.removeMatching(text)` → `{"success":true,"forgot":"<text>"}` or
  `{"error":"nothing in memory matching '<text>'"}`.
- Both added to `CLOUD_COACH_TOOL_SCHEMAS` (a new `MEMORY_TOOL_SCHEMAS` list appended), and **NOT**
  added to `COACH_WRITE_TOOLS` — they run without a confirmation dialog and the coach acknowledges in
  its reply.
- `toolStatusText`: `remember` → "Updating memory…", `forget` → "Updating memory…".

**Prompt guidance (`COACH_PROMPT_GUIDELINES`):** "You have a memory of facts about the user (diet,
injuries, preferences). Use `remember` when they ask you to remember something and `forget` when they
ask you to drop it. Respect these facts in your advice. Don't store the same fact twice."

## UI — "Coach memory" screen

- `ui/aicoach/CoachMemoryScreen.kt` + `CoachMemoryViewModel.kt`:
  - State: `entries: List<CoachMemoryEntry>` (from `coachMemory.observe()`), plus add/edit text state.
  - `add(text)`, `update(id, text)`, `delete(id)` → store; `clearInput()`.
  - Screen: `ScreenScaffold(withNavBarInset = false)` + `SubScreenHeader("Coach memory", onBack)`, a
    one-line explainer, an add field (`GlassInputField` + a `Liquid*Button`/add affordance), and the
    list — each entry a `NeutralCard`/row with its text, an edit action (inline field or a small
    editor), and a delete (trash) icon. Empty state: a short "The coach doesn't know anything about
    you yet — add a fact or tell it in chat."
  - Design-system compliant (`AppType`, `LocalAppColors`, glass components — no raw `fontSize`/hex).
- Entry point: a row in `AiCoachScreen` (under a section) → `onOpenCoachMemory()`.
- Nav: `Routes.CoachMemory` + a `composable(...)` in `AppNavGraph` that hosts `CoachMemoryScreen`
  (VM via the existing factory), with a back to the AI Coach screen.

## Wiring (`AppContainer`)

Construct one `CoachMemoryStore`; inject it into `CoachToolsAdapter` (prompt), `CoachToolExecutor`
(tools), and the `CoachMemoryViewModel` factory.

## Architecture / boundary

- Store in `data/coach` (pure of `ai/local`); tools in `ai/CoachToolExecutor`; injection in
  `ai/CoachToolsAdapter`; UI in `ui/aicoach`. `AiCoachBoundaryTest` stays green.
- Deterministic: the store holds text; the LLM only phrases. Memory text is user/coach authored,
  never invented numbers.

## Testing

- `CoachMemoryStore`: add (trim/dedupe/blank-ignored), update, delete, cap-drops-oldest,
  `removeMatching` best-match + null, DataStore round-trip (temp-dir DataStore per test, like
  `CoachJourneyStoreTest`).
- `CoachToolExecutor`: `remember` adds via the store; `forget` removes the matching entry; blank/no-match
  errors. Both reachable via dispatch.
- `CoachToolsAdapter`: memory block present when store non-empty, omitted (prompt unchanged) when empty.
- `CoachMemoryViewModel`: add/update/delete call the store; state reflects `observe()`.
- Screen: compiles; on-device visual + behavior verified by the user.
- `remember`/`forget` are NOT in `COACH_WRITE_TOOLS` (assert in a small test or the boundary/schemas test).

## Rollout

Subagent-driven TDD in the MAIN checkout on `redesign/ai-coaching` (worktree isolation branches from
the wrong base here — main tree + base sanity check). Build one screen and verify in the running app
(per the user's "verify each screen" preference). Review pass, then on-device verification: add/edit/
delete a fact in the screen; tell the coach "remember I'm vegetarian" and see it appear + be respected.
