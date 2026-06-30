# 06 — AI / Coaching / Insights

Focused improvement plan for the **AI / Coaching / Insights** section. This area is substantially
complete and well-architected (dual local/cloud backends, a routing layer, a tool-calling coach, a
shipped knowledge corpus, an insight-card pipeline, and a cloud weekly-briefing flow). The work
below is about **finishing dangling threads and raising value**, not re-architecting.

> All findings below were verified against the code on the `develop` branch. Several items from the
> original audit brief turned out to be **stale or incorrect** and are corrected inline — read
> §1 before acting on anything.

---

## 1. Current state & problems

### What is actually wired (audit corrections)

- **Weekly Briefing IS wired and shipping — the "never invoked" finding is wrong.**
  `WeeklyBriefingGenerator.generate()` (`ai/WeeklyBriefingGenerator.kt:17`) is invoked through the
  full chain: `DashboardScreen.kt:144` renders `WeeklyBriefingOverlay`, driven by
  `WeeklyReviewViewModel.open()` (`ui/review/WeeklyReviewViewModel.kt:68-91`), which calls
  `config.briefingFor(...) { config.generate(data) }`. `AppContainer.kt:247,520-521` binds
  `generate` → `WeeklyBriefingGenerator` and `briefingFor` → `WeeklyBriefingRepository`
  (`data/repository/WeeklyBriefingRepository.kt`). It is fully tested
  (`WeeklyBriefingGeneratorTest`, `WeeklyBriefingPromptBuilderTest`, `WeeklyBriefingTest`,
  `WeeklyBriefingRepositoryTest`, `WeeklyReviewViewModelTest`).
  **The real issue:** it is **cloud-only**. With no cloud key the overlay shows the `Upsell` state
  (`WeeklyBriefingOverlay.kt:82`, `WeeklyReviewViewModel.kt:71`) — `WeeklyBriefingGenerator` takes
  only an `OpenAiCompatClient` (`AppContainer.kt:247`) and there is no on-device fallback path.
  There is **no orphaned `generateBriefing()`** method anywhere (`grep` returns nothing); the
  `ai/WeeklyBriefing.kt` + overlay are two ends of the *same* feature, not two different things.

- **Knowledge corpus IS shipped and partially wired — "no bundled data source / no-ops" is wrong.**
  `app/src/main/assets/knowledge/corpus.json` exists (116 KB) and is loaded at
  `AppContainer.kt:339-347` into `RetrievalKnowledgeInjector(KeywordKnowledgeRetriever(...))`,
  degrading to `NoOpKnowledgeInjector` only on parse failure. It is covered by
  `KnowledgeCorpusIntegrityTest`, `RetrievalProbeTest`, etc.
  **The real gap:** the injector is passed **only to the cloud coach**
  (`AppContainer.kt:362` → `CloudCoachCoordinator`, which injects a per-turn REFERENCE block at
  `CloudCoachCoordinator.kt:116-119`). The **on-device `GemmaCoachCoordinator` never receives a
  `knowledgeInjector`** — its constructor (`AppContainer.kt:234-242`) has no such parameter, and
  `grep knowledge GemmaCoachCoordinator.kt` returns nothing. So knowledge injection is a **cloud-only
  feature**; the local coach answers domain questions from the 2B model's parametric memory alone.

- **`ModelVariant` is NOT hardcoded to 2B-only.** `ai/ModelVariant.kt` defines both `GEMMA_2B` and
  `GEMMA_4B`, with a `ModelVariantSelector` UI (`ui/aicoach/AiCoachComponents.kt:179-190`,
  iterating `ModelVariant.entries`) and DataStore persistence (`AppPreferences.kt:131-135,182-183`).
  **The real limitation:** both entries are *Gemma* LiteRT-LM files. The enum's shape (fixed
  `fileName`/`downloadUrl`/`sha256`/`expectedBytes`) assumes a single on-device LiteRT-LM family;
  there is no abstraction for a different on-device runtime or a non-Gemma model.

### Confirmed problems

- **Cloud-only insight cards have no UI affordance.** `InsightKind` has 7 entries
  (`ai/InsightRequest.kt:3-10`). The local Gemma coordinator hides 4 of them via `CLOUD_ONLY_KINDS`
  = {WEEKLY_PATTERN, TARGET_CHANGE, NOISE_DEFUSER, CROSS_METRIC} (`GemmaInsightCoordinator.kt:396-400`),
  showing only 3 locally (PROGRESS_TREND, RECOVERY_READINESS, REST_OF_DAY). The cloud coordinator
  supports all 7 (`CloudInsightCoordinator.kt:40`). On the local backend the richer cards simply
  **vanish with no explanation** — no badge, no "available on cloud AI" hint (verified: no
  cloud/CLOUD markers in `DashboardScreen.kt`, `AiInsightCard.kt`, `GeneratedInsightCard.kt`).

- **Web-search key setup is functional but buried.** `cloudHasWebSearchKey`
  (`AiCoachViewModel.kt:37,89`) and a Tavily key field with save/clear/test
  (`AiCoachScreen.kt:249-302`) exist and work — better than the audit's "barely surfaced" implied.
  But the whole block lives **inside the cloud-settings expander**, only relevant once a cloud chat
  key is saved, and `search_web` is also a *local-coach* tool (`GemmaCoachCoordinator.kt:60`,
  `CoachToolExecutor.kt:33,285`). Local-only users who could benefit from web lookups never see
  where to add the key.

- **The coach is food/metric-centric; it cannot see training.** `CoachToolExecutor`
  (`ai/CoachToolExecutor.kt:27-33`) exposes `get_today_summary`, `get_weekly_trends`,
  `search_food_library`, `log_meal`, `log_metric`, `update_calorie_target`, `search_web` — **no
  workout / lift / session tool.** The app has a rich workout layer (`WorkoutRepository`,
  `WorkoutSessionRepository`, `PerformanceDao`, `domain/workout/ExerciseStatsCalculator`,
  `WorkoutProgressAnalyzer`) that the AI is blind to. A "body **recomposition**" coach that cannot
  discuss lifts is a real value gap.

- **Echo-phrase detection is a fixed string list.** `containsEchoPhrase()`
  (`GemmaCoachCoordinator.kt:376-386`) hard-codes 8 English substrings ("i need to call", "i'll
  call", …). It is brittle to phrasing the 2B model invents and is monolingual.

- **CoachScreen (the live chat screen) violates the design system.** `ui/coach/CoachScreen.kt` —
  the screen actually wired into nav (`AppNavGraph.kt:235`) — uses a Material `Surface(...)` as a
  chat bubble (`CoachScreen.kt:578`) and two hardcoded `fontSize = 14.sp` Texts
  (`CoachScreen.kt:396,604`), against the rule in `docs/design-system.md` (no raw `Surface`-as-card,
  never a bare `fontSize`).

---

## 2. UX improvements

1. **Surface cloud-only insight cards instead of silently hiding them.** When the effective backend
   is LOCAL and a `CLOUD_ONLY_KINDS` card would otherwise appear, render a compact "locked" placeholder
   ("Pattern, target & recovery insights run on cloud AI — enable it in AI settings") with a tap that
   routes to `AiCoachScreen`. Today these cards just disappear (`GemmaInsightCoordinator.kt:396-400`),
   so the user has no idea richer analysis exists. Keep it to **one** rolled-up placeholder, not four.

2. **Promote web-search key setup out of the cloud-only expander.** Because `search_web` works on the
   *local* coach too (`GemmaCoachCoordinator.kt:60`), move/duplicate the Tavily field
   (`AiCoachScreen.kt:249-302`) so a local-AI user can reach it without first saving a cloud chat
   key. A one-line "Let the coach look things up online" toggle near the coach section is enough.

3. **Weekly Briefing: keep cloud-only, but make the gate honest.** The `Upsell` copy
   (`WeeklyBriefingOverlay.kt:180-184`) already explains "runs on cloud AI." That's fine. The UX
   improvement is **not** to fake an on-device briefing (see §9) but to make the dashboard entry
   point for it discoverable when cloud is *off* — e.g. the weekly-review badge
   (`WeeklyReviewViewModel.kt:57-60`) is currently gated on `cloud == true`, so local users never
   even see the entry. Show a muted entry that opens the upsell, so the feature is *discoverable*
   rather than invisible.

---

## 3. UI improvements

1. **Bring `CoachScreen` into design-system conformance.** Replace the `Surface(...)` chat bubble
   (`CoachScreen.kt:578`) with the glass card family (a `TintedCard` for the AI bubble per the
   "AI sections use the tinted card" memory note; a `NeutralCard`/frosted bubble for the user turn),
   and replace the two `fontSize = 14.sp` Texts (`CoachScreen.kt:396,604`) with an `AppType` token
   (`body` is 13sp — closest role match; use `AppType.body` and drop the bare size). Audit the
   `accent.tintedSurface`-filled `OutlinedTextField`/chip usages (`CoachScreen.kt:246-248,336-337,
   477-499`) against `GlassInputField`/`GlassSegmentedToggle` and swap where a clean replacement
   exists.

2. **`AiCoachScreen` settings clarity.** The cloud settings block packs chat key, web-search key,
   model variant, and test-connection into one long expander (`AiCoachScreen.kt:230-303`). Group it
   into labelled sub-sections via `SectionLabel` ("Cloud chat", "Web search", "On-device model") so
   the relationship between the keys is legible. The web-search help text already uses `AppType.label`
   correctly (`AiCoachScreen.kt:253`) — match that discipline across the block.

3. **`WeeklyBriefingOverlay` glyph affordances.** The refresh control is a raw "↺" `Text` with
   `fontSize = 15.sp` (`WeeklyBriefingOverlay.kt:157-162`); signal arrows are "↑/↓/→" glyphs
   (`:316-319`). Per the design system, *control* glyphs should be Material icons. The arrows are
   borderline-content (a direction indicator) and can stay; the refresh **tap target** should become
   an `Icon`. This overlay is intentionally backdrop-free and Dialog-hosted, so its bespoke buttons
   are acceptable — note it, don't rebuild it.

---

## 4. Data / model improvements

1. **Wire the knowledge corpus into the on-device coach.** This is the highest-leverage data fix.
   Add a `knowledgeInjector: KnowledgeInjector` parameter to `GemmaCoachCoordinator` (mirroring
   `CloudCoachCoordinator.kt:51`), pass the already-loaded `knowledgeInjector` from
   `AppContainer.kt:234-242`, and inject a per-turn REFERENCE block the same way the cloud coach does
   (`CloudCoachCoordinator.kt:116-119`) — **but** keep the block tight (`maxChunks=2`, `maxChars≈1200`
   for 2B vs the cloud `maxChars=2000`, `RetrievalKnowledgeInjector.kt:10-12`) so it never crowds the
   user's snapshot on the small-context model. The corpus, retriever, and injector all already exist
   and are tested; only the local wiring is missing.

2. **Generalise `ModelVariant` toward a model *registry*.** Today it is a closed enum of two Gemma
   files (`ModelVariant.kt`). Without over-building, factor the fields that are runtime-specific
   (file name, URL, hashes, size) behind a small `OnDeviceModelSpec` so adding a future LiteRT-LM
   model — or a different on-device family — does not require touching every `when(variant)` site
   (`AppPreferences.kt:133-135`, `GemmaInsightCoordinator.kt`, `GemmaServiceHolder.kt:23`). Defer the
   actual second family until there's a concrete model to ship (see §8/§9).

3. **Persist the web-search key location is already correct** (`SecureKeyStore.kt:69`,
   `KEY_WEB_SEARCH`) — no change needed; noted so it isn't "fixed" by mistake.

---

## 5. AI opportunities (the core)

1. **Wire-or-kill Weekly Briefing → decision: KEEP, it's already wired (cloud-only).** Do **not**
   remove `ai/WeeklyBriefing.kt` / `WeeklyBriefingGenerator` / `WeeklyBriefingPromptBuilder` — they
   are live and tested. The value-add is to make the cloud-gated feature *discoverable* on local-only
   installs (§2.3) and, optionally, to enrich the briefing input (it already feeds the deterministic
   `WeeklyReviewData` skeleton and merges AI prose onto it — a safe pattern, `WeeklyBriefingGenerator.kt:41-63`).

2. **Ship the knowledge corpus end-to-end (cloud + local).** The corpus is populated and wired to
   cloud; closing the local gap (§4.1) is the single biggest "raise value within the 2B limits" move
   because it adds *grounding* without adding a tool iteration — the REFERENCE block is prepended, not
   fetched (`CloudCoachCoordinator.kt:116-119`). This respects the docs/ai-coach.md guardrail
   ("never add a tool for static data") while still improving answer quality.

3. **Add a workout-aware coach read tool — carefully, within the 5-iteration cap.** Add **one**
   read tool, `get_training_summary` (last session + 7-day volume/PR deltas), backed by
   `WorkoutSessionRepository` + `ExerciseStatsCalculator`/`WorkoutProgressAnalyzer`. Follow the
   docs/ai-coach.md doctrine exactly:
   - It must be a **read** tool only (no `log_set` — logging mid-chat risks the write-confirmation
     flow and a tool-iteration blowout on 2B).
   - Pattern it on `get_weekly_trends` (returns a compact JSON the model answers from in 1–3
     sentences). Add a single, date-free rule to `buildSystemPrompt`
     (`GemmaCoachCoordinator.kt:388`) so it doesn't collide with the today/yesterday rules (the 2B
     rule-ambiguity note in docs/ai-coach.md).
   - Do **not** pre-fetch training into the system-prompt snapshot — it isn't the most-common read,
     and the snapshot already carries today's nutrition. Cross-reference the workout-section plan
     before finalising the JSON shape.

4. **Richer insights without more model load.** The `CROSS_METRIC` / `NOISE_DEFUSER` cards already
   exist for cloud (`InsightRequest.kt:48-55`). A cheap win is to feed the local
   `RECOVERY_READINESS` / `PROGRESS_TREND` prompts a *deterministic* training signal (e.g. "trained
   4×, volume +8%") computed by the domain layer and injected as plain text — grounding the verdict
   without a tool call or a new model.

---

## 6. Quick wins

- **Wire `knowledgeInjector` into `GemmaCoachCoordinator`** (§4.1). Small, additive, high value;
  the injector instance already exists in `AppContainer`.
- **Add the cloud-only insight placeholder card** (§2.1) — one composable, routes to AI settings.
- **CoachScreen design-system fixes** (§3.1): swap the `Surface` bubble + two `fontSize = 14.sp`
  for tokens/components.
- **Promote the Tavily web-search field** so local-AI users can find it (§2.2).
- **Make the weekly-review entry discoverable when cloud is off** (drop the `cloud` gate on the
  dashboard *entry*, keep it on *generation*) (§2.3, `WeeklyReviewViewModel.kt:57-60`).

## 7. Medium improvements

- **`get_training_summary` read tool** (§5.3) — needs a repository read path, a JSON shape, a system-
  prompt rule, and `CoachToolExecutorTest` coverage. Coordinate with the workout-section plan.
- **Group `AiCoachScreen` cloud settings into labelled sub-sections** (§3.2).
- **Make echo-phrase detection configurable / data-driven** (§8 below) at least to a `companion`
  constant list so it's editable in one place and unit-testable, even before any smarter approach.
- **Tighten the local knowledge REFERENCE budget** for 2B (`maxChunks=2`, lower `maxChars`) so the
  new injection doesn't degrade the snapshot-based answers.

## 8. Bigger refactors

- **Knowledge pipeline as a first-class, shared dependency.** Today the corpus is loaded once in
  `AppContainer` and handed to cloud only. Refactor so a single `KnowledgeInjector` is the canonical
  dependency injected into *both* coaches and (optionally) the insight prompt builders, with the
  per-backend char budgets as constructor params. Consider moving the synchronous asset load off the
  main thread (the existing `TODO` at `AppContainer.kt:337-338` flags this for corpora > ~50 chunks;
  the shipped corpus is already larger).
- **Multi-model support.** Generalise `ModelVariant` into a registry/spec (§4.2) and decouple
  `GemmaInsightService`/`GemmaServiceHolder` from the assumption of one Gemma LiteRT-LM file, so a
  second on-device model (or a non-Gemma runtime) can be added without rippling `when(variant)`
  branches. Only do this when a concrete second model is chosen.
- **Echo / empty-response recovery → a small pluggable "response sanitiser."** Replace the inline
  `containsEchoPhrase()` string list (`GemmaCoachCoordinator.kt:376-386`) and the
  nudge/empty-text handling with a single testable component, so the 2B-specific behavioural patches
  documented in docs/ai-coach.md live in one place.

## 9. What to avoid for now

- **Do NOT build an on-device Weekly Briefing path.** The briefing merges AI prose onto a
  deterministic skeleton and is explicitly a cloud feature (`WeeklyBriefingGenerator.kt:8-10`); a 2B
  on-device JSON-narration step risks malformed JSON and the empty-response failure mode. Keep it
  cloud-gated; only improve *discoverability* (§2.3).
- **Do NOT add tools for static/session-invariant data.** Per docs/ai-coach.md: plan targets and
  user profile are already in the system prompt; today's snapshot is pre-fetched. Don't add a
  `get_profile`/`get_plan` tool. The knowledge REFERENCE block is *injected text*, not a tool — keep
  it that way.
- **Do NOT exceed the 2B tool-iteration cap.** `MAX_TOOL_ITERATIONS = 5`. Adding `get_training_summary`
  is fine as **one** read tool; do **not** also add `log_set` / per-exercise tools to the chat path —
  that multiplies iterations and write-confirmation round-trips on a model already prone to empty
  responses after tool sequences.
- **Do NOT pre-fetch training data into the snapshot.** It would bloat every conversation's system
  prompt for a non-default query class.
- **Do NOT rebuild `WeeklyBriefingOverlay` as standard glass components.** It is intentionally
  backdrop-free for Dialog hosting (`WeeklyBriefingOverlay.kt:106-140`); only swap the refresh glyph
  for an Icon.

## 10. Suggested implementation order

1. **Wire knowledge into the local coach** (§4.1, §6) — additive, isolated, immediately raises
   on-device answer quality. Add `GemmaCoachCoordinator` knowledge tests mirroring the cloud ones.
2. **CoachScreen design-system fixes** (§3.1, §6) — independent, low-risk, removes the clearest
   guideline violations on the screen users actually chat in.
3. **Cloud-only insight placeholder + weekly-review discoverability** (§2.1, §2.3, §6) — makes the
   existing-but-hidden cloud value visible; no model work.
4. **Promote the web-search key field** (§2.2, §6).
5. **`get_training_summary` read tool** (§5.3, §7) — the one genuinely new AI capability; do it after
   the cheap wins and in lockstep with the workout-section plan, respecting the 2B caps in §9.
6. **`AiCoachScreen` settings grouping + echo-detection consolidation** (§3.2, §7).
7. **Bigger refactors** (§8) — shared knowledge pipeline, model registry — only once a second model
   or a measured corpus-size problem justifies them.
