# AI Ideas Backlog

New AI feature ideas surfaced while researching how the best apps (WHOOP, Oura, Fitbit/Gemini,
Apple Health, MacroFactor, Carbon) use AI + stats. These are *beyond* the current insight-card
redesign — captured here so they aren't lost. Nothing here is committed; it's a menu.

Each idea: what it is, why it'd help, rough effort, and what it depends on. Ordered loosely by
value-to-effort. Last updated 2026-06-15.

---

## A. High value, builds directly on the redesign

### A1. Proactive insight ranking ("show only the one that matters")
Every researched app converges on **surfacing one insight at a time**, chosen by impact. Right now
each card fires independently on its own screen. Add a ranking step that scores candidate insights
by `magnitude-of-deviation × actionability × novelty` and promotes the single best one to the
dashboard hero slot (others stay on their screens).
- **Why:** "five insights = zero insights." This is the difference between a coach and a dashboard.
- **Effort:** Medium. Needs a scoring function over the already-computed contexts.
- **Depends on:** the richer contexts from this redesign.

### A2. Weekly "What changed and why" digest
A short Monday-morning narrative that ties the week together: trend update + the one target change
(if any) + the single behavioral lesson. Modeled on MacroFactor's weekly check-in and Apple's
trend notifications.
- **Why:** novelty drives retention — a digest that *teaches* something beats a static dashboard.
- **Effort:** Medium. New prompt + a once-weekly trigger. Reuses `WeeklyBriefingGenerator` (already
  exists) as the backbone.
- **Depends on:** weekly review data (already computed in `domain/review`).

### A3. "Stay quiet" intelligence (the JITAI gate)
Implement rule 7 of the doctrine as real behavior: when the user is on-plan and trends are flat,
suppress the card entirely instead of generating filler. A small deterministic gate in front of
the LLM call (no tokens spent when there's nothing to say).
- **Why:** kills filler, saves cloud cost, builds trust that a card means something.
- **Effort:** Low–Medium. A `shouldFire(context): Boolean` per card.
- **Depends on:** baseline/threshold data from this redesign.

---

## B. Cross-metric intelligence

### B1. Correlation "aha" engine
Detect and surface genuine cross-domain links over a rolling window: protein↔hunger score,
adherence↔weight-trend, sleep↔soreness, deficit↔energy. Fitbit's Gemini coach leans on exactly
this ("how does my sleep affect my recovery?").
- **Why:** the most "intelligent-feeling" insights; hard for the user to spot themselves.
- **Effort:** Medium–High. Needs a lightweight correlation detector in `domain/insight`.
- **Risk:** correlation ≠ causation — must hedge ("you *tend to* …"), never claim mechanism.

### B2. Plateau detector + break-the-plateau coaching
Recognize a true plateau (trend flat for N weeks at adherence ≥ threshold) vs. a noise week, and
coach the difference — the single most common point where recomp users quit.
- **Why:** turns the highest-churn moment into a coached one.
- **Effort:** Medium. Trend-flatness detection over multiple weeks.

---

## C. Logging-friction reducers (Fogg "ability" lever)

### C1. Pattern-based "log this?" suggestions
From recurring meal patterns ("you log oats most weekday mornings"), offer a one-tap pre-fill.
- **Why:** the biggest lever on logging consistency is reducing logging cost.
- **Effort:** Medium. Pattern mining over meal history.

### C2. Smart protein-gap nudge at the right moment
A time-aware Rest-of-Day trigger: if dinner isn't logged and protein < 70% of target by evening,
nudge with one concrete swap — JITAI "opportunity window" targeting.
- **Why:** lands the nudge while the user can still act.
- **Effort:** Low–Medium (once Rest-of-Day has time-of-day context).

---

## D. Engagement / trust mechanics

### D1. Adherence-as-unlock progression
Carbon's mechanic: more consecutive clean-logging weeks → the app advertises *more sophisticated*
evaluation. Frame logging consistency as unlocking accuracy, never as a compliance score.
- **Why:** reframes adherence positively; motivates logging without shame.
- **Effort:** Medium. A progression state + copy.

### D2. Tone toggle (Conversational ↔ Direct)
Oura ships this. Let the user pick coach voice; swap a line in the system prompt.
- **Why:** cheap personalization; meaningfully changes perceived fit.
- **Effort:** Low. One preference + prompt branch.

### D3. "Why did my target change?" tappable explainer
Whenever the plan changes the calorie/macro target, attach the causal one-sentence explanation
(the target-change card) so a silent number change never feels arbitrary.
- **Why:** the #1 trust-eroder in adjustment apps is an unexplained target change.
- **Effort:** Low once the target-change card exists (part of this redesign).

---

## E. Bigger bets (later)

### E1. Conversational "coach proactively checks in"
Have the coach open a thread on its own when something notable happens (a great week, a stall),
rather than waiting for the user. Fitbit's coach is explicitly designed to "proactively check in."
- **Effort:** High. Needs a trigger system + notification + the coach loop.

### E2. Photo meal-scan → log (MyFitnessPal Meal Scan, Fitbit/MFP)
Vision model estimates a meal from a photo and pre-fills a log entry.
- **Effort:** High. Vision model + estimation + correction UX. Likely cloud-only.

### E3. Natural-language weekly goal setting
"I want to lose 0.4 kg/week but keep my lifts" → the plan engine translates intent into targets,
explained back in plain language.
- **Effort:** High. Couples the coach to the plan generator.

---

## Notes
- Anything that emits a number must obey the doctrine: only cite computed values, hedge blips,
  assert trends, never diagnose. See `insight-output-doctrine.md`.
- Ideas that reduce logging friction (C*) likely have the highest retention impact, because every
  downstream insight depends on consistent logging.
