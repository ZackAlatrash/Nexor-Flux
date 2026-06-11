# Weekly Briefing — Design Spec

**Date:** 2026-06-11
**Branch:** `feature/weekly-briefing` (off `main`)
**Status:** Approved design, pre-implementation

## Summary

A proactive, AI-narrated **weekly briefing** that closes the core "dietitian" loop:
*here's your week, here's the verdict, here's what to change.* It surfaces the
data the app already computes (`AdjustmentInput` + `AdjustmentResult` + trends +
adherence) as a first-class experience instead of a discarded one-liner.

The feature is **cloud-only** — it runs exclusively when the effective AI backend
is CLOUD (selected *and* cloud config complete). Because it never touches the
on-device 2B model, it has none of that model's reliability constraints and can
produce long, structured, multi-section output.

## Background — what already exists

- `DashboardViewModel` already computes the full `AdjustmentInput` on every data
  change: weight trend (kg/wk), waist trend (cm/wk), adherence %, days logged,
  performance trend, recovery trend. It runs `AdjustmentEngine.evaluate()` and
  gets an `AdjustmentResult` (verdict / recommended calorie change / reason codes
  / summary).
- It **already persists** a `WeeklyReviewEntity` to Room whenever the verdict or
  recommended change flips, anchored to Monday (`weekStart`).
- `WeeklyReviewEntity` is currently written but **never surfaced** to the user
  except in backup export.
- The AI insight system (`RoutingInsightCoordinator` → `CloudInsightCoordinator` /
  `GemmaInsightCoordinator`) can narrate from an `InsightContext`, but the output
  is hard-capped to 2 sentences (`InsightPromptBuilder.limitToSentences(it, 2)`),
  stripped of formatting — the one-liner-card constraint.
- The coach chat is `RoutingCoachCoordinator` over `GemmaCoachCoordinator` (local)
  and `CloudCoachCoordinator` (cloud). Both build their system message from a
  single `systemPromptSnapshot()` — the injection point for handoff context.
- `PlanRepository` exposes `save(prefs)`; applying a calorie change mirrors the
  coach's existing `update_calorie_target` tool.

This feature is therefore **mostly experience, not computation**: a trigger, a
surface, a richer narration, an apply loop, and a chat handoff — over data that is
already produced.

## The experience

### Dashboard entry point

A persistent **"Weekly Review" button** (card/pill) on the dashboard, always
visible regardless of backend.

It carries a **reminder badge** ("New" dot) when either:
- a new review week has started (the Monday-anchored `weekStart` advanced) and the
  user hasn't opened this week's briefing yet, **or**
- the cached briefing auto-refreshed because the input signature changed mid-week.

The badge clears once the user opens the overlay. "Last seen" state (week + last
seen signature) is tracked in `AppPreferences` (DataStore).

### The overlay

Tapping opens a **full-height modal overlay** (scrollable; chosen over a bottom
sheet because the sectioned content is too tall for a sheet to feel good). The
overlay resolves to one of four states:

1. **Upsell** — not on cloud backend. Explains the feature and links to Settings to
   enable cloud AI. **No model call.**
2. **Building your first review** — on cloud, but fewer than **7** logged days in
   the window (or adherence too low to evaluate). Templated empty state showing
   days remaining / what's missing. **No model call.**
3. **Generating** — loading while the cloud call runs.
4. **Briefing** — the rendered result (early or full phase, see below).

### Two-phase briefing (the 7-day rule)

The briefing's own data gate is **7 days**, but the `AdjustmentEngine` only emits a
*calorie-change* verdict at **14+** days (under 14 it returns `WAIT_FOR_DATA`).
This produces two phases — **without modifying the engine**:

- **EARLY (7–13 days):** trends + narrative "early read." The action section
  explains it's too early to change calories. **No Apply button.**
- **FULL (14+ days):** full verdict. When the verdict is INCREASE or REDUCE, the
  action section shows the **Apply** loop.

### Briefing content (sectioned, from structured JSON)

The model returns JSON rendered as clean sections:

1. **Headline verdict** — one punchy AI sentence
   (e.g. "Waist −0.4 cm, weight flat, adherence 86%, squats up → this is recomp,
   hold calories").
2. **Your week** — short narrative paragraph.
3. **Per-signal breakdown** — weight, waist, adherence, strength, recovery; each
   row = the **deterministic number/delta** + one sentence of AI interpretation.
4. **Recommended action** — verdict + rationale. In FULL phase with an
   INCREASE/REDUCE verdict: an **"Apply: set target to X kcal"** button →
   quick confirm (mirroring the coach's write-tool confirmation) →
   `planRepository.save(prefs.copy(targetCalories = X))`.
5. **What to watch next week** — forward-looking note.

A manual **Regenerate** action is available in the overlay.

### Grounding principle

The numbers and the verdict come **only** from the deterministic
`AdjustmentEngine` / trend calculators. The model narrates and interprets — it
**never invents or overrides** a number or the verdict. The generator's prompt is
handed those values as authoritative.

### Chat handoff (rich, but quiet)

A **"Discuss with your coach"** action hands off into the existing coach chat:

- Clears coach history and injects a `=== WEEKLY BRIEFING CONTEXT ===` block into
  `systemPromptSnapshot()` containing **both** the underlying weekly data **and**
  the briefing the user just read.
- Adds a behavioral directive: *the user just read this briefing and opened chat to
  ask about it — give at most a one-line greeting, do not re-explain the briefing,
  then wait for their question and answer concisely from this context.*
- **No auto-message** is sent — the chat opens seeded and the user speaks first, so
  the coach listens rather than monologues.
- Works for whichever backend the coach is on (local or cloud), since both build
  from `systemPromptSnapshot()`.

## Architecture

All additions are additive and follow existing `ai/` + repository patterns.

| Component | Layer | Responsibility |
|---|---|---|
| `WeeklyBriefing` (+ `SignalLine`, `ActionBlock`, `BriefingPhase`) | `ai/` model | Structured briefing: headline, narrative, per-signal lines, action, watch-next, `EARLY`/`FULL` phase |
| `WeeklyBriefingPromptBuilder` | `ai/` | Builds the rich payload (week metrics + deltas + daily calories/adherence + strength lifts + recovery + engine verdict/reason codes + phase) and the JSON-output + grounding instructions |
| `WeeklyBriefingGenerator` | `ai/` | **Cloud-only.** Uses `OpenAiCompatClient` + `CloudConfig`, runs the call (no sentence cap), parses JSON → `WeeklyBriefing`, retry-once-then-fallback on bad JSON. Separate from the one-liner insight path |
| `WeeklyBriefingRepository` | `data/repository` | Caching brain: computes the input signature, returns cached briefing on match, regenerates on mismatch, persists, exposes current-week state |
| `WeeklyReviewViewModel` | `ui/` | Overlay state machine + badge state + apply + handoff |
| `WeeklyReviewButton`, `WeeklyBriefingOverlay` | `ui/component` | Dashboard button w/ badge; full-screen modal with the sectioned render + apply-confirm dialog |

### Storage — Room v8 → v9 migration

Add three nullable columns to `weekly_reviews` (already keyed by `weekStart`):

- `briefingJson: String?` — the structured narrative (serialized `WeeklyBriefing`)
- `briefingSignature: String?` — the input signature the briefing was generated for
- `briefingGeneratedAt: String?`

Round-trip the new fields in `BackupModels` / `BackupRepository`. No new table.

### Input signature (drives "auto-refresh on change")

A hash of the **rounded / bucketed** key inputs: weight trend, waist trend,
adherence bucket, performance trend, recovery trend, verdict, recommended change,
phase. Same inputs → same signature → cache hit. A material shift flips the
signature → regenerate + badge. Rounding prevents regenerating on trivial decimal
wiggle.

### Data flow

1. `DashboardViewModel` already computes `AdjustmentInput` + `AdjustmentResult` on
   every data change.
2. `WeeklyBriefingRepository` consumes that same stream to compute the signature and
   drive badge state (no recomputation of trends/adherence).
3. On overlay open the VM resolves the state:
   - not cloud → **Upsell** (no call)
   - cloud + <7 logged days (or adherence too low) → **Building your first review**
     with `daysRemaining`
   - else → repo returns cached-or-generated briefing → **Ready** (EARLY or FULL)
4. **Apply** → `planRepository.save(prefs.copy(targetCalories = X))` behind a confirm.
5. **Handoff** → clear coach history, inject briefing context + quiet directive into
   `systemPromptSnapshot()`, navigate to coach screen; no auto-message.

## Edge cases

- **Not cloud** → Upsell, no call.
- **<7 days / adherence too low to evaluate** → Building-your-first-review state.
- **7–13 days** → EARLY briefing, no Apply button.
- **Cloud call fails / times out** → Error state with retry; keep last cached
  briefing readable if present.
- **JSON parse failure** → retry once, then fall back to a templated render from the
  deterministic data + raw narrative text (or Error if even that fails).
- **Backend switches cloud → local while overlay open** → cached briefing stays
  readable; Regenerate is disabled.
- **Signature changes mid-week** → badge appears; auto-refresh on next open.

## Testing

- `AdjustmentEngine` stays pure and untouched.
- **Signature stability** — same inputs → same hash; bucket-internal wiggle → no
  regen; verdict change → regen.
- **Repository** — cache hit/miss + persistence + auto-refresh on signature change.
- **Generator** — JSON parse + retry + fallback, using a fake `OpenAiCompatClient`.
- **ViewModel** — state machine: backend gating, 7-day and 14-day thresholds,
  apply-confirm, badge clearing.
- **PromptBuilder** — output includes grounding instruction + phase + all signals.

## Out of scope (YAGNI)

- Changing `AdjustmentEngine` thresholds or logic.
- A separate Weekly Review history screen / browsable past weeks (history stays in
  Room; only the current week's briefing is surfaced).
- Push notifications for the Monday reminder (in-app badge only).
- Pre-generating briefings in the background.
