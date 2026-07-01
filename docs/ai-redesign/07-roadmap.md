# AI Coaching Redesign — Prioritized Roadmap

Derived from the vision (`06-ai-product-vision.md`) and the five research docs. **Planning only.**
Ordered so trust is won early and the risky/high-ceiling work comes once the spine exists. Each phase
is independently shippable and verifiable in the running app.

Guiding rule: **subtract before you add.** The app's problem is too many weak AI surfaces, not too few.
The first real work is *deletion*.

---

## Phase 0 — Cloud-only cutover *(foundation, mostly deletion)*
Remove the on-device stack so everything after is built on one clean cloud path.
- Delete `Gemma*` coordinators/service/holder, `ModelVariant` + DownloadManager/SHA-256 plumbing,
  the three `Routing*` coordinators, the `AiBackend` toggle, `AiCapabilities`, `CLOUD_ONLY_KINDS`,
  and 2B-only patches (`containsEchoPhrase`, empty-text nudge).
- `CloudInsightCoordinator` / `CloudCoachCoordinator` become the only coordinators; wire them directly.
- Simplify the AI settings screen to cloud config only (base URL, model, API key, web-search key).
- **Risk:** touches DI (`AppContainer`) + settings UI. **Verify:** insights + chat still work on cloud;
  no dead on-device code paths remain.
- *Net: large code reduction, zero user-facing loss (local was being deprecated anyway).*

## Phase 1 — Cut the weak surfaces, collapse the redundant ones *(quick trust win)*
Apply Agent 2's delete/merge verdicts. Fewer, stronger surfaces.
- **Remove** the LLM paraphrase layer on WEEKLY_PATTERN, CROSS_METRIC-as-built, the standalone
  ACTIVITY_NEAT card (fold its intent into the proactive engine later), and the dead `rich` prompt modes.
- **Collapse** the three verdict-narrators (legacy weekly-verdict card, TARGET_CHANGE, Weekly Briefing)
  into a single weekly surface.
- **Keep untouched:** NOISE_DEFUSER, recipe namer, knowledge base.
- **Verify:** the dashboard is quieter; no card merely restates a chart.
- *Note: this removes the ACTIVITY_NEAT card just shipped on develop — intended; the redesign supersedes
  standalone scroll-cards. Its NEAT logic returns inside the weekly check-in / proactive engine.*

## Phase 2 — The proactive spine: CoachSignalEngine + weekly check-in *(the core bet)*
This is the heart of the redesign — make the AI reach out.
- Build the pure-Kotlin **`CoachSignalEngine`** (deterministic triggers with thresholds; see the trigger
  catalog in `03-proactive-ai-design.md`), reusing existing detectors (`domain/adjustment`,
  `domain/insight`, `domain/review`, `domain/trend`, `domain/streak`, `domain/activity`).
- Feed the existing skeleton-merge `WeeklyBriefingGenerator` **all four domains** and fire it
  proactively (the `proactiveReview` scaffolding already exists and currently fires nothing).
- Add a **`CoachDigestWorker`** (WorkManager sibling of `HealthSyncWorker`) + `onStart` hook that runs
  the engine, ranks signals, and writes the single winner to a DataStore-backed **`CoachInboxRepository`**
  (dedup via the same week-signature `markSeen` used by the weekly-review badge).
- Add the **"Today's Coaching"** home slot (one item, silent if nothing clears the bar).
- **Verify:** with a week of data, the check-in produces a cross-domain verdict + one action; the home
  slot shows at most one thing and stays empty when it should.

## Phase 3 — Make the coach a real recomposition coach *(depth)*
- Give Coach chat a **`get_training_summary`** tool (lifts, e1RM, volume, frequency, RIR/soreness) and
  **body-measurement history** (weight + waist/skinfold trends), and ground replies in the knowledge base.
- Extend knowledge grounding beyond chat into the weekly check-in.
- **Verify:** ask the coach "how's my bench trending?" / "am I recomping?" and get a data-grounded answer.

## Phase 4 — Cross-domain insight layer *(highest ceiling, do once the spine is proven)*
Rebuild what CROSS_METRIC pretended to be, surfaced through the proactive engine (not scroll cards):
the 5 links whose data already exists — recomposition signal, e1RM plateau, deload-due, training-day vs
rest-day nutrition, sleep↔performance (`04-data-utilization.md`).
- **Verify:** each link fires only on its real signal and drives a concrete action.

**Status (2026-07-01): DONE at the detector layer.** All 5 links are `CoachSignalEngine`
detectors, each computing every number deterministically and firing only on its real signal
(verified by review). deload-due (`OPEN_TRAINING`) and sleep↔hunger (informational — no
sleep-logging action type exists, honest `NONE`) are new `TODAY`-surface detectors reaching the
"Today's Coaching" slot; recomp / e1RM-plateau / training-day-protein shipped in Phase 2.

**Deferred follow-up — weekly-signal surfacing / engine convergence.** The three Phase-2 links
are `WEEKLY`-surface, but the only live consumer of engine signals (the digest) filters to
`TODAY`, so they currently reach no screen. Their intended surface (the weekly check-in) is served
by the *older* `WeeklyReviewComputer`, not `CoachSignalEngine`. Closing this means either flipping
those detectors to `TODAY` or making the weekly check-in consume the engine's `WEEKLY` winner
(the doc-faithful "one engine → both surfaces" convergence, which must de-dupe against
`WeeklyReviewComputer`'s existing body/training/nutrition verdicts). Tracked as a distinct task.

## Phase 5 — Journey memory, celebration & respectful push *(the "coach that remembers")*
- **`CoachJourneyStore`** ledger feeding multi-week narrative into prompts ("3 weeks ago you…").
- Greenfield **push notifications** (none exist today) built to a decision-attached, opt-outable spec;
  enforce the caps: ≤2/week, P0 or weekly report only, never consecutive days, ≤1 celebration/week.
- PR + recomposition-win celebration events.
- **Verify:** pushes are rare, always actionable, and never spammy; the coach references past weeks.

**Status (2026-07-01): DONE.** Built in 6 commits + a review-fix pass, all TDD, boundary green:
- **`CoachJourneyStore`** (`data/coach`, DataStore) — append-only fired-signal + weekly-verdict ledger with
  recurrence detection; `journeyNarrative()` is injected into the weekly-briefing and chat prompts (omitted
  byte-identically when <2 weeks of history). Recorded from real staged winners + weekly reviews in the digest.
- **Celebration** — `NEW_PR`/`RECOMP_WIN` render in the *same* Today's-Coaching slot with a warm gold skin
  (`FrostedCard` + new `celebration*` tokens + trophy), keyed off `CoachSignal.isCelebration`. No new surface.
- **`RateLimiter`** (pure, `domain/coach`) + `PushHistoryStore` — enforces ≤2/week, P0-or-weekly-only,
  ≤1/day (never consecutive **or** same day), ≤1 celebration/week (weekly-report push exempt), quiet hours.
- **Push** — greenfield `CoachNotifier`/`AndroidCoachNotifier` (2 channels: `weekly_check_in` default,
  `coaching` low), decision-attached gate (no push without verdict + actionable deep-link), `CoachPushEmitter`
  gates on prefs → `RateLimiter` → gate, wired into the digest; `POST_NOTIFICATIONS` + tap deep-links.
  The digest `run()` is `Mutex`-serialized so the worker/foreground race can't double-push.
- **Settings** — a quiet-by-default "Notifications" group in the AI Coach screen (weekly on, ambient off,
  quiet-hours display); opt-out first-class.
On-device push delivery + permission dialog + the celebration/notification visuals are the user's to verify.

---

## Sequencing rationale
- **0 → 1 first** because a smaller, honest surface area is the fastest trust win and makes everything
  cheaper to build. Don't build new AI on top of the old sprawl.
- **2 is the pivot** — proactivity is the single highest-leverage change (Agent 1 + Agent 3). Everything
  before it is setup; everything after enriches it.
- **4 and 5 last** because they have the highest ceiling but depend on the engine + trust existing first.

## What to avoid (for now)
- **No new data collection** — no progress photos, no goal-weight field, no body-fat input yet. The
  entire redesign runs on data already logged. (Add later only if a specific insight demands it.)
- **No notification spam / vanity scores.** If a surface can't attach a decision, cut it, don't ship it.
- **No second chatbot / no "AI everywhere."** One coach, three surfaces, one voice.
- **Don't let the LLM invent numbers** — the engine computes facts, the model only phrases them.
- **Don't rebuild the deterministic engines** (adjustment, trend, streak, adherence) — reuse them as the
  signal source.
