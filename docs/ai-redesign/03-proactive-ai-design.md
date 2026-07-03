# 03 — Proactive AI Design (cloud-only redesign)

**Goal:** turn the AI from a card that only renders when the user happens to scroll to it into a
coach that *notices* — one that reaches out, warns, celebrates, and remembers the journey over
weeks. The differentiator (per `05-market-research.md`) is that all four domains — nutrition,
lifting, steps, body metrics — plus a recomposition adjustment engine live in **one** app, so we
can do the cross-domain "noticing" that Whoop/Oura/Hevy/MacroFactor each can't.

**Non-negotiables carried from the evidence:**
- **Every message carries a decision** — never an orphaned metric (`05:§3` "vanity scores users
  ignore", `05:§5.4`). A trigger that can't name a next action doesn't fire.
- **Trend over point-in-time** — smooth, and *hold* when confident (`05:§5.5`). Deterministic
  detectors gate first; the LLM only ever phrases a fact the engine already computed.
- **Right-sized proactivity** — the market punishes spam (`05:§3` "notification spam", MacroFactor's
  "only when it can meaningfully impact your goal"). At most **one** thing surfaces at a time.
- **Cloud-only** — no 2B tool-iteration cap; the LLM can hold raw series and reason cross-domain
  (`04:§redesign-implications`). But proactivity is **deterministic-first**: signals are detected in
  pure Kotlin (`domain/*`), the LLM only writes the sentence, so it can never fabricate a warning.

This doc assumes the local stack is deleted (`01:§1.3`) and builds on infra that already exists:
`ProcessLifecycleOwner` `onStart` hook + `HealthSyncWorker` periodic WorkManager job
(`RecompTrackerApp.kt:41-54`, `HealthSyncWorker.kt`) as the "wake up and check" mechanism. **The app
has no notification code today** (`grep` for `NotificationManager`/`NotificationChannel` → none), so
push is greenfield and can be built to the respectful spec below from day one.

---

## Architecture in one paragraph

Introduce a **`CoachSignalEngine`** (pure Kotlin, `domain/coach/`) that runs the trigger catalog
below over the repositories on a schedule (daily via a new `CoachDigestWorker`, plus opportunistically
on `onStart`). Each detector returns a nullable `CoachSignal(kind, priority, severity, statement,
action, dedupKey, surface)` — the same "detector computes a complete numeric statement, LLM
paraphrases" pattern the app already uses for `InsightFact` (`PatternDetectors.kt`,
`InsightModels.kt:27`). The engine ranks all firing signals, applies the rate-limit/priority model
(§2), and emits **at most one** "featured" signal into a `CoachInboxRepository` (DataStore-backed,
so it survives restarts and is deduped by `dedupKey` + week signature, exactly like the existing
`WeeklyReviewViewModel` badge / `markSeen(signature)` mechanism, `WeeklyReviewViewModel.kt:57-83`).
The featured signal is what the home "Today's coaching" slot, the optional push, and the weekly
report draw from. The LLM (cloud `CloudInsightCoordinator` / briefing generator) turns the
`statement` + `action` into 1–2 sentences of prose; it never decides *whether* to speak.

---

## 1. Trigger catalog

Legend — **Fires:** deterministic gate (all pure-Kotlin, computable from existing data). **Says:**
LLM-phrased from the statement. **Action:** the attached decision (mandatory). **Surface:** default
delivery (see §3). **Priority tier:** P0 (safety/high-value, may push) → P3 (ambient, in-app only).
Every field cited maps to `04-data-utilization.md`'s "existing data" inventory.

### A. Body-composition & progress

| # | Trigger | Fires (signal / threshold) | When it fires | Says | Action it drives | Data / existing hook | Priority |
|---|---|---|---|---|---|---|---|
| 1 | **Recomposition win** | `\|weightTrendKgPerWeek\| ≤ 0.20` (maintenance band, `AdjustmentThresholds`) **AND** `waistTrendCmPerWeek ≤ -0.15` over ≥2 weeks (waist genuinely down). Skinfold ↓ raises severity. | Weekly, on review compute | "Scale's flat but your waist is down 0.4 cm/wk — that's fat down, muscle holding. Textbook recomp." | Celebrate + "keep calories where they are." | `bodyWeightKg`, `waistCm`, `waistSkinfoldMm`, `TrendCalculator.trendPerWeek`. **#1 highest-value message in the app (`04:ranked-1`); computed nowhere for the coach today.** | **P0** |
| 2 | **Fat-gain warning** (weight up + waist up) | `weightTrendKgPerWeek > 0.20` **AND** `waistTrendCmPerWeek > 0.25`. This is exactly the engine's `GAINING_WITH_WAIST_INCREASE` verdict (`AdjustmentEngine.kt:48-58`). | Weekly | "Weight and waist both climbing — this is fat, not muscle. Trimming ~100 kcal." | Accept the −100 kcal target change (drives `update_calorie_target`). | Adjustment engine `reasonCodes` already emit this; surface it, don't recompute. | **P0** |
| 3 | **Plateau / stall** (nutrition side) | Weight trend inside ±0.10 kg/wk for ≥3 consecutive weeks **while** the phase goal is loss or gain (not maintenance). | Weekly | "Three weeks flat — your body's adapted to this intake. Here's the adjustment." | Open the weekly briefing / apply verdict. | `TrendCalculator` slopes across ≥3 `WeeklyReviewData` snapshots; needs multi-week memory (§4). | P1 |
| 4 | **Quiet weigh-ins** | No `bodyWeightKg` entry in ≥5 days **AND** the trend engine is starving (would flip to `WAIT_FOR_DATA`). | Daily check | "Haven't seen the scale since Tuesday — one weigh-in keeps your trend honest." | Deep-link to log weight. | `DailyLogEntity.bodyWeightKg` recency; mirrors `AdjustmentEngine`'s `daysLogged < 14` starvation. | P2 |

### B. Training (the biggest AI-blind dataset — `04:§training`, `01:§8.4`)

| # | Trigger | Fires | When | Says | Action | Data / hook | Priority |
|---|---|---|---|---|---|---|---|
| 5 | **New e1RM PR** | A completed set's Epley e1RM (`WorkoutProgressAnalyzer.estimatedOneRepMax`) exceeds the all-time `bestEstimatedOneRepMax` for that lift by a meaningful margin (≥1 kg, to filter rep-math noise). | Event: on session save | "New bench e1RM — 102 kg, up from 99. The cut isn't costing you strength." | Celebrate + tie to recomp (strength held during deficit = good sign). | `SessionSetEntity` → `WorkoutProgressAnalyzer.trendPoints`. Fires from `HealthSyncWorker`/session-save, not scroll. | **P0** |
| 6 | **Lift plateau** (e1RM stall) | Per-lift `bestEstimatedOneRepMax` series flat or declining across the last N (≥3) sessions of that lift, **tied to nutrition**: if in a deficit, note it. | Weekly, per primary lift | "Bench e1RM's been flat 3 sessions while you're cutting — normal in a deficit; don't add volume, protect it." | Hold volume / consider the deficit is capping strength. | `ExerciseStatsCalculator.oneRepMaxSeries` joined to phase/deficit. `04:ranked-2`. | P1 |
| 7 | **Deload-due** | Rising `sorenessScore` trend **AND** falling `rir` (grinding reps) **AND** `recoveryTrend == POOR`. Classic overreaching triad (`04:ranked-3`). | Weekly | "Soreness up, reps getting grindy, sleep's short — a deload week would bank the fatigue." | Suggest a deload / lighter week. | `SessionSetEntity.rir`, `DailyLogEntity.sorenessScore/sleepHours/energyScore`, `TrendCalculator.recoveryTrend`. | P1 |
| 8 | **Missed-workout pattern** | Workout streak broken past its rest tolerance (`StreakCalculator` uses `restDays=2` for WORKOUT, gap ≤3 continues) **OR** weekly sessions < `weeklyGymSessions` profile target for 2 wks. | Weekly / on streak-break check | "Two sessions short of your 4/wk target two weeks running — one session back this week resets it." | Nudge to train / re-plan the week. | `StreakCalculator` WORKOUT result, `WorkoutSessionEntity` count vs `UserProfilePreferences.weeklyGymSessions`. | P2 |

### C. Nutrition & adherence

| # | Trigger | Fires | When | Says | Action | Data / hook | Priority |
|---|---|---|---|---|---|---|---|
| 9 | **Calorie-adherence drift** | 7-day `dailyAdherencePercent` drops below the plan's `adherenceMinimumPercent` (80%) after a prior stretch above it — a *change*, not a static low. | Weekly | "Adherence slipped to 71% this week from 88% — mostly weekends. One planned Saturday keeps the trend readable." | Attach the derailment day if present (#12); nudge. | `AdherenceCalculator.dailyAdherencePercent`; `AdjustmentEngine` `LOW_ADHERENCE`. | P1 |
| 10 | **Protein miss on training days** | Protein attainment on `trained == true` days averages materially below rest days (or below target) over the window. Training-axis version of the existing weekday/weekend detector (`PatternDetectors.detectWeekdayWeekend`). | Weekly | "On lifting days you're hitting 78% protein vs 96% on rest days — you're under-fuelling the days that matter most." | Front-load protein on training days. | `DailyLogEntity.trained` joined to `MealEntryEntity` protein. `04:ranked-4`. | P1 |
| 11 | **Unconfirmed planned meals** | ≥2 planned (`MealEntryEntity.planned == true`) entries for *past* days never confirmed — plan-follow-through gap. | Daily check | "You've got 3 planned meals from yesterday still unconfirmed — confirm or clear them so your totals are real." | Deep-link to confirm/clear. | `planned` flag recency (`01:§source-of-truth`). Felt-accountability "noticing" (`05:§5.7`). | P2 |
| 12 | **Derailment day** | Existing `detectDerailmentDay` — a single day drove ≥60% of the week's ≥700 kcal surplus (`PatternDetectors.kt:31`). | Weekly | "Saturday drove 68% of this week's surplus — the other six days were dialled in." | Name the pattern; suggest a weekend plan. | Already implemented; reroute from scroll-only card to the proactive slot. | P2 |
| 13 | **Weakest-macro** | Existing `detectWeakestMacro` — a macro averaging <85% of target (`PatternDetectors.kt:56`). | Weekly | "Protein's your gap — averaging 74% of target this stretch." | One lever to fix it. | Already implemented; reroute. | P3 |

### D. Recovery, activity & momentum

| # | Trigger | Fires | When | Says | Action | Data / hook | Priority |
|---|---|---|---|---|---|---|---|
| 14 | **Recovery decline** | `recoveryTrend` degrades to POOR (rolling sleep/energy/soreness) after being OK/GOOD — a *transition*. Cross with under-eating: if in a deficit, name it. | Weekly / daily | "Energy and sleep both sliding this week — often the deficit biting. Consider a diet break day." | Suggest recovery action; possibly `INCREASE_CALORIES` if engine agrees. | `TrendCalculator.recoveryTrend`; can join `AdjustmentEngine` `LOSING_WITH_POOR_RECOVERY`. | P1 |
| 15 | **NEAT collapse** | 7-day step average dropped ≥25% vs the prior 7-day average **AND** weight trend stalled — explains a stall with steps, not calories. | Weekly | "Steps down ~3k/day and the scale's stalled — it's movement, not your intake. Nudge the step goal before cutting food." | Raise/defend step target instead of cutting calories. | `ActivityMetrics` 7-day step avg + `DailyLogEntity.steps`; `04:ranked-honorable`. Cross-domain — the market gap (`05:§4`). | P1 |
| 16 | **Streak about to break** | A qualifying streak's last hit is at the edge of its rest tolerance (`restDays + 1` for the type) and today isn't yet qualified. | Daily, evening | "Your 11-day protein streak is live but today's not logged yet — one entry keeps it." | Deep-link to log. | `StreakCalculator` `current` + `last7Marks` edge. `04:§derived-signals`, `05:§5.7`. | P2 |
| 17 | **Streak / milestone celebration** | A streak crosses a milestone (7/14/30/etc.) or `longest` is beaten. | Event | "14 days straight at your protein target — your longest yet." | Pure reinforcement (no ask). | `StreakResult.current`/`longest`. Sparingly (§5). | P3 |

### E. Plan-journey (long-term, §4)

| # | Trigger | Fires | When | Says | Action | Data / hook | Priority |
|---|---|---|---|---|---|---|---|
| 18 | **Target-change explainer** | Plan calorie target moved (existing TARGET_CHANGE gate). | On change | "Trimmed 100 kcal — waist ticked up two weeks running while weight climbed. New target 2,400." | Explain the *why* in journey terms. | `PlanVersionEntity`; fold the 3 redundant verdict surfaces (`01:§8.1`) into this. | P1 |
| 19 | **Phase / journey milestone** | e.g. "8 weeks in this cut", "down 4 kg since phase start", "calorie target walked 2,700→2,400 over the phase". | Monthly / phase boundary | "Eight weeks in: 2,700→2,400 kcal, weight tracked exactly as modelled. The plan's working." | Reflect + confirm the phase is on track (or suggest a break). | `PlanVersionEntity` history narrative (`04:ranked-honorable`). §4. | P2 |

**Notes on reuse:** #12/#13 already exist as `InsightFact` detectors — the redesign *reroutes* them
from scroll-triggered cards into the proactive engine, it doesn't rebuild them. #2/#18 already exist
as adjustment-engine `reasonCodes`. The genuinely new deterministic work is: the per-lift e1RM
series checks (#5–7), the `trained`-axis protein join (#10), the NEAT-vs-stall cross (#15), and the
multi-week memory that #3/#19 need (§4).

---

## 2. Cadence & restraint (the anti-spam model)

The market's clearest lesson is that noise destroys trust faster than silence
(`05:§3`, MacroFactor's "only when it can meaningfully impact your goal"). So the default posture is
**quiet**, and surfacing is a *scarce* resource.

### 2.1 Priority + severity ranking
Every firing signal has a **priority tier** (P0–P3, above) and a **severity** score computed inside
the detector (e.g. how far past threshold, `PatternDetectors.kt` already does this via its `priority`
int). The engine sorts by `(tier, severity)` and takes the **top 1**.

### 2.2 One-thing rule
- The home "Today's coaching" slot shows **exactly one** featured signal (the winner). Everything
  else that fired is not thrown away — it's demoted to the passive in-context surfaces (the relevant
  screen's insight card) where the user meets it only if they go looking. This preserves the "silo"
  cards as *pull*, and reserves *push/featured* for the single most important thing.
- This directly answers `01:§7`'s finding that today all 9 cards compete equally by scroll position.

### 2.3 Rate limits (hard caps)
| Channel | Cap | Rationale |
|---|---|---|
| **Push notification** | ≤ **2 / week**, and **only P0 or the weekly check-in**. Never two days in a row. | Push is the spam vector; reserve it for the recomp win, the fat-gain warning, a PR, or the weekly report. |
| **Home featured slot** | **1 at a time**, refreshed at most once/day; auto-dismisses when acted on or when its `dedupKey`+signature is marked seen. | Mirrors the existing `markSeen(signature)` badge dedup (`WeeklyReviewViewModel.kt:83`). |
| **Celebration (#17, PRs)** | ≤ **1 / week** total across all celebratory triggers. | A cheerleader that celebrates everything celebrates nothing (`05:§5` "not a cheerleader"). |
| **Same trigger repeat** | A given `dedupKey` can't re-surface until its underlying signal *changes* (new week signature) or ≥7 days pass. | Prevents "adherence low" nagging every day. |

### 2.4 The three rhythms
- **Daily (quiet, in-app only):** the `CoachDigestWorker` runs once (early, before first open) and on
  `onStart`. It evaluates the daily-scoped triggers (#4, #11, #16 streak-edge, urgent #14) and, if a
  P0/P1 fires, stages the home slot. **No push on daily unless P0.** Most days: nothing new — that's
  the point.
- **Weekly (the spine — this is the product):** on the review-data signature change
  (`WeeklyReviewViewModel` already computes this), run the full catalog, pick the winner, generate
  the **weekly check-in** = the existing `WeeklyBriefingGenerator` skeleton-merge (`01:§4`) *extended*
  with the featured cross-domain signal. This is the one moment a push is warranted weekly. Adopts
  MacroFactor's weekly-check-in-as-spine model (`05:§5.2`).
- **Event-triggered (immediate, rare):** PR (#5) and fat-gain/recomp verdicts fire off session-save
  or the sync worker. These are the only things allowed to interrupt mid-week, and only #5 + P0 body
  verdicts qualify.

### 2.5 When the AI must stay silent
- **Low confidence / thin data:** if `daysLogged < 14` or the week is noisy, *hold* — say nothing or
  say "not enough to call yet" (never over-correct, `05:§5.5`; mirrors `AdjustmentEngine`
  `WAIT_FOR_DATA`).
- **Nothing crossed a threshold:** no manufactured insight. A clean on-track week gets, at most, a
  quiet "on track, holding" — which the existing `InsightGate.shouldFireWeekly` already models by
  staying quiet on a clean HOLD (`01:§2.9`).
- **User just acted:** don't nudge to log what was logged 10 minutes ago.

---

## 3. Delivery surfaces

Today every surface is scroll/tap-reactive and lives in a silo (`01:§7`). The redesign adds three
*proactive* surfaces and keeps the isolated coach screen as the on-demand supplement, not the main
event (`05:§5.2`).

### 3.1 Home "Today's coaching" slot (primary)
A single featured card at the top of the Dashboard, fed by the one winning signal from
`CoachInboxRepository`. Replaces the current "whichever card you scrolled to" hierarchy. Uses the
existing tinted `AiInsightCard` glass shell (`01:§2`) with its action as a real button (deep-links to
log weight / confirm meals / open briefing / apply target). If nothing fired, the slot shows a quiet
"On track — holding" state or collapses. This is where 90% of proactivity is *felt*, with zero push.

### 3.2 Weekly check-in / report (the spine)
Extend the existing `WeeklyBriefingOverlay` (`01:§4`) from a passive badge into a genuine weekly
event: same deterministic-skeleton + LLM-prose merge (which correctly prevents the model from
altering numbers), now **led by the featured cross-domain signal** and folding in the three redundant
verdict surfaces (`01:§8.1`). Announced by the badge (already built) **and** one respectful weekly
push (§3.3). This is the curated, comes-to-you check-in the market rewards (MacroFactor/Whoop/Oura).

### 3.3 Push notifications (respectful, greenfield)
No notification code exists yet — build it to spec:
- **Channels:** one low-importance "Coaching" channel + one default-importance "Weekly check-in"
  channel, so the user can mute nudges but keep the report (or vice-versa).
- **Budget:** the §2.3 caps — ≤2/week, P0 or weekly-report only, never consecutive days, respects a
  quiet-hours window.
- **Content = a decision, never a number alone** ("Weight flat, waist down — you're recomping. Tap
  to see the week." not "Recovery: 61%"). Orphaned-metric pushes are the #1 ignored/distrusted thing
  (`05:§3`).
- **Delivery mechanism:** the `CoachDigestWorker` (a sibling of `HealthSyncWorker`,
  reusing the same WorkManager plumbing) posts the notification when it stages a P0 / weekly signal.
- **Fully opt-outable**, defaulting to weekly-report-on / daily-nudges-off (respect first).

### 3.4 In-context nudges (pull, demoted)
The per-screen insight cards (Body/Trends/Food/Train) remain, but become the *demoted* home for
signals that didn't win the featured slot — the user meets them only by navigating there. Training
gets its own cards for the first time (#5–8) since a read-only `get_training_summary` is added
(`01:§8.4` gap). This keeps the silo cards useful as pull without letting them compete for push.

### 3.5 Coach chat (on-demand supplement)
The chat tab stays, but any featured signal is passed into the conversation as context (via the
existing `CoachHandoffStore` one-shot carrier, `01:§3`) so "Discuss with coach" on a proactive card
opens a chat that already knows what it was about. Chat is the place to go *deeper* on a nudge, not
where nudges originate.

---

## 4. Long-term coaching (showing it remembers the journey)

Today every review is stateless (`04:§plan`) and every card is a one-shot. The felt-accountability
the market pays for is "something that knows your history and notices changes" (`05:§5.7`,
Future/Caliber). Two mechanisms:

### 4.1 A rolling coach memory (persisted state)
Add a `CoachJourneyStore` (DataStore) that persists a compact, append-only ledger of:
- the last ~8 weekly review signatures + verdicts (already computed as `WeeklyReviewData`),
- plan-version transitions (`PlanVersionEntity` — already stored, never summarized by AI),
- fired-signal history (which triggers fired when, so we can say "third week of this" and honour the
  §2.3 no-repeat rule),
- phase start + weeks-in-phase.

This is what makes triggers #3 (3-week plateau), #18/#19 (journey narrative), and the no-nag repeat
rule possible — all from data the app already stores but never carries forward.

### 4.2 Multi-week narrative in prompts
Feed that ledger into the weekly briefing prompt so the LLM can say **"three weeks ago you were
losing 0.4 kg/wk; that's slowed to 0.1 — your body's adapting"** or **"you've held 90%+ adherence
for a month straight."** The deterministic engine supplies the numbers across weeks; the LLM narrates
the arc. This is the single thing no competitor does across four domains (`05:§4` cross-domain gap).
Phase awareness ("8 weeks into your cut") comes free from `weeksSincePhaseStart`, already in
`AdjustmentInput`.

### 4.3 Reason across multiple verdicts
With history in hand, the coach can spot meta-patterns the stateless engine can't: "the plan has held
four reviews running — you may be under-eating and stalling, not maintaining" (`04:§plan`, "each
review is stateless today"). This is a P1 weekly signal layered on the ledger.

---

## 5. Habit & motivation (evidence-based, not cheerleading)

Grounded in the market's "nonjudgmental, decision-attached" findings (`05:§2,§3`), not hype.

- **Streaks as momentum, not pressure.** Surface a streak when it's *live and at risk* (#16) or hits
  a real milestone (#17) — capped at 1 celebration/week (§2.3). The existing `StreakCalculator` rest
  tolerance already models "you can miss a day", so the coach reinforces without punishing a rest day.
- **Celebrate the meaningful, sparingly.** Recomp win (#1) and e1RM PRs (#5) are the celebrations that
  land because they're *earned progress the user couldn't see themselves* — the coach's job is to
  make the invisible win visible, once, then move on. Never celebrate merely opening the app.
- **Implementation intentions over exhortation.** Every nudge names a *specific, concrete* next
  action tied to the user's own data ("front-load protein on lifting days", "one planned Saturday",
  "confirm yesterday's 3 meals") rather than "try harder" — the difference between a coach and a
  cheerleader (`05:§5.4` "every score carries a decision").
- **Behavior→outcome callouts** are the strongest motivator (`05:§5.6`): "your best lifting days
  follow 7h+ sleep", "steps down explains the stall, not your intake" (#15). Tying *what you did* to
  *what happened* across domains is the causal story that feels like real coaching — and it's exactly
  the cross-domain reasoning only this app can do.
- **Tone:** nonjudgmental, trend-first, honest about uncertainty. When confidence is low, the
  motivating move is *"hold, we'll know more next week"* — which itself builds trust (`05:§5.5`,
  Whoop's "trust the 7-day trend").

---

## 6. Build order (lowest-risk first)

1. **Home featured slot + `CoachInboxRepository`** fed initially by *existing* detectors (#12/#13 +
   adjustment verdicts #2/#18) rerouted from scroll-cards. Ships proactivity with near-zero new
   detector risk.
2. **`CoachSignalEngine` + `CoachDigestWorker`** (WorkManager sibling of `HealthSyncWorker`) with the
   §2 rate-limit/priority model. Daily quiet evaluation, no push yet.
3. **Push channels** (weekly-report-on / nudges-off default) for the weekly check-in + P0 only.
4. **Training triggers (#5–8)** once the read-only training summary is exposed (`01:§8.4`).
5. **`CoachJourneyStore` + multi-week narrative** (§4) — unlocks #3, #19, and the no-nag ledger.
6. **New cross-domain detectors** (#10 trained-protein, #15 NEAT-stall, #14 recovery-transition).

Each stage is independently shippable and each moves the app off "100% reactive" (`01:§7`).
