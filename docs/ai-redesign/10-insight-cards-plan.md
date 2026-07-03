# AI Insight Cards — Rethink & Plan

**Branch:** `redesign/ai-coaching` · **Produced:** 2026-07-02 · **Planning only — nothing is implemented.**

This document is a focused re-think of the **AI insight cards** across the app (distinct from the broader
coaching-system docs 01–09 in this folder, which cover the chat coach and proactive spine). It was produced
by five parallel research agents: a code-verified card inventory, a brutally-honest value audit, competitor
research across 17 apps, a fresh proposal for useful cards, and a keep/improve/remove disposition.

**The prompt behind it:** after ~1 month of daily use, most AI insight cards have *not* felt useful. The
only card that feels genuinely useful is the Food Log **meal-suggestion card** added on this branch — which,
tellingly, contains **zero AI** (it's deterministic). That one fact drives the entire analysis.

**Scope guardrails (unchanged):** do not redesign the AI architecture, do not replace the coordinator/gating
plumbing, do not touch the on-device model stack. This is about *which cards to add, improve, remove, or
keep*, their content, placement, and timing — working within the existing system (cloud insight coordinator,
deterministic gates, the Weekly Briefing skeleton-merge, the coach handoff pattern). Analysis assumes the
**cloud** model is primary.

---

## The one principle everything below serves

> **An insight is only worth showing if it gives the user a specific, optioned _action_ at the moment they
> have to _decide_ — using their _own_ data. Deterministic by default; spend cloud LLM tokens only where the
> model interprets multi-signal data the user genuinely cannot combine themselves.**

The meal-suggestion card wins on four axes at once — **specific** (your exact remaining gap), **optioned**
(3 foods + a combo), **actionable** (tap to log), **well-timed** (fires only once the day is half gone and a
real gap exists) — and needs no AI to do it. Every current AI card fails at least one of those axes, usually
two or three. The fix for the whole surface is not "better prompts." It is: **make each card prescribe an
action at a decision moment, delete the ones that only paraphrase a number, and spend LLM tokens only where
the model produces genuinely new synthesis.**

---

## 1. Current AI card inventory

Ten card-surfaces look like AI insights today. All `GeneratedInsightCard` cards fire **reactively** (a
`LaunchedEffect` calls `onInsightVisible` when the card scrolls into view) → the coordinator dedupes on a
content hash → cloud or local AI generates ≤2 sentences → it renders in a collapsible glass pill.

| # | Card (title) | Screen | Fires when | Engine | Verdict at a glance |
|---|---|---|---|---|---|
| 1 | **PROGRESS_TREND** — "Trend analysis" | Trends | ≥2 weight/waist trend points | Cloud **+ Local** | Genuine multi-signal synthesis; stops at *describing* |
| 2 | **RECOVERY_READINESS** — "Recovery readiness" | Body | any recovery metric logged | Cloud **+ Local** | Good shape, but fires post-log (too late) & blind to training/nutrition |
| 3 | **REST_OF_DAY** — "Rest of day" | *(none)* | **dormant** — superseded by #10 | Cloud + Local | Dead code |
| 4 | **WEEKLY_PATTERN** — "Coach spotted" (HERO) | Dashboard | a detector produces a top fact | **Cloud only** | LLM paraphrases a finished detector sentence |
| 5 | **TARGET_CHANGE** — "Why your target changed" | Dashboard | calorie target moves | **Cloud only** | Redundant — 1 of 3 verdict narrations |
| 6 | **NOISE_DEFUSER** — "Scale check" | Dashboard | weight jump ≥0.6 kg vs trend | **Cloud only** | Excellent gate, but a 3-number template the LLM can't enrich |
| 7 | **CROSS_METRIC** — "Coach noticed a link" | Dashboard | context provided | **Cloud only** | Best *concept* (correlation), thinnest execution (1 hard-coded pair, paraphrase) |
| 8 | **ACTIVITY_NEAT** — "Activity" | Dashboard | step goal + steps set | **Cloud only** | Generic "walk more"; any-app boilerplate |
| 9 | **WEEKLY_VERDICT** (legacy) — "Calorie decision" | Dashboard | non-HOLD / adherence <75% / waist drift | **Cloud only** | Richest prompt in the app, but redundant with #5 & Briefing |
| 10 | **MealSuggestionCard** — "Meal ideas" | Food Log | >50% of day elapsed + a macro gap + library thick enough | **Deterministic** (no AI) | **The one card the user finds useful** — the template |

**Two non-card AI features for context:** the **Coach chat** (cloud tool-loop; notably has *no*
training/workout tool despite a recomp focus) and the **Weekly Briefing** (cloud, prose-only JSON merged
onto a deterministic `WeeklyReviewData` skeleton — the app's best-designed AI surface).

**Structural findings that shape the plan:**
- **Triple redundancy.** `WEEKLY_VERDICT` (#9) + `TARGET_CHANGE` (#5) + the Weekly Briefing all narrate the
  *same* weekly calorie-adjustment verdict.
- **Three paraphrase cards.** `WEEKLY_PATTERN` (#4), `CROSS_METRIC` (#7), `NOISE_DEFUSER` (#6) hand the model
  a finished sentence and ask for a reword — near-zero marginal LLM value, real cloud-token cost.
- **Everything is reactive** (scroll/tap). No proactive AI, though the scaffolding exists.
- **Rich data is invisible to every card:** free-text notes, per-lift detail (name/reps/sets/RIR), per-meal
  timing, carbs/fat context, training load, and the 2nd-place pattern facts (only top-1 is ever shown).
- **Cloud "rich" (4–6 sentence) insight modes are defined but never used** — the cloud model is asked for the
  same 1–2 liners as the 2B local model.

---

## 2. Current AI card usefulness audit (brutally honest)

Scored on: helps a **decision?** · changes **behavior?** · **specific** to the user? · **proactive** or just
descriptive? · worth **cloud tokens?** · would the user **miss** it?

| Rank | Card | Decision | Behavior | Specific | Stance | Tokens worth it | Missed? | Verdict |
|---|---|---|---|---|---|---|---|---|
| 1 | **#10 MealSuggestion** | yes | yes | specific | proactive-in-moment | yes (0 tokens) | **yes** | **KEEP** — the model to copy |
| 2 | #2 RecoveryReadiness | yes | partial | specific | reactive | marginal | meh | FIX (timing) |
| 3 | #7 CrossMetric | partial | partial | semi | reactive | marginal | meh | FIX (highest upside) |
| 4 | #1 ProgressTrend | partial | no | specific | descriptive | marginal | meh | FIX (add an action) |
| 5 | #9 WeeklyVerdict | yes | partial | specific | reactive | marginal | partial | FIX (make it *the* verdict) |
| 6 | #6 NoiseDefuser | yes | partial | semi | reactive | **no** | meh | FIX (de-AI, keep gate) |
| 7 | #4 WeeklyPattern | partial | partial | semi | reactive | **no** | meh | FIX (de-AI the paraphrase) |
| 8 | #8 ActivityNEAT | partial | partial | semi | descriptive | no | **no** | KILL / de-AI |
| 9 | #5 TargetChange | yes-but-late | no | redundant | descriptive | no | **no** | KILL (merge) |
| 10 | #3 RestOfDay | — | — | — | dormant | no | **no** | KILL (dead code) |

**Why the AI cards feel useless — root causes:**
1. **Paraphrase, not insight.** #4/#6/#7 reword a sentence the code already wrote. Users feel it as filler.
2. **Descriptive, not prescriptive.** Almost every card *narrates state* and ends in a period; the useful one
   ends in a tap-to-log action.
3. **Triple-redundant verdict.** Saying the same weekly decision three times makes each instance feel *less*
   insightful and triples the token bill for one idea.
4. **Wrong timing / surface.** Cards fire on scroll or post-log, not at the decision moment (recovery fires
   *after* you log sleep, not when you're deciding whether to train).
5. **Generic-anyone content.** "Walk more," "it's water weight" could be shown to any user of any tracker.
6. **Shallow data.** Models never see notes, per-lift detail, meal timing, carbs/fat, or training load — so
   they can't out-reason the pre-computed numbers.

**Net:** of 9 AI cards, ~3 spend cloud tokens on pure paraphrase/redundancy, 2 are dead or generic, and only
2–3 do genuine synthesis. **The single genuinely useful card uses no AI at all.**

---

## 3. Competitor research summary (17 apps)

Full per-app notes live in the research scratch; the signal is remarkably consistent across nutrition,
wearable, and training apps.

### The winning pattern (everyone who does it well)
**Number/score → named drivers → ONE action, comparative to the user's own baseline, delivered
proactively** — Fitbit/Garmin Readiness, Whoop Daily Outlook, Oura Contributors, Caliber Strength Score.
Transparency beats prose: users trust a legible score whose drivers are shown far more than an opaque
paragraph.

### App-by-app, sharpest takeaways
- **MacroFactor (the closest comparator).** Makes a target change *trustworthy* with four stacked mechanics:
  (1) ground it in **one transparent equation** the user can check; (2) **attribute the change to the user's
  own behavior**, not the model ("your intake ran below target, so your true maintenance looks higher"); (3)
  be **conservative and say so** (tentative now, confirm next week — never whipsaws); (4) always answer **"why
  now"** and **require approval** before mutating the plan. Deliberately has *no* chatty-AI centerpiece.
- **Cronometer.** **Preview-impact** bars (solid = already eaten, striped = what this meal *will* add) make
  the insight actionable *at decision time*. One highlighted "low today" nutrient beats a dashboard dump.
- **MyFitnessPal.** Weekly Digest is a **descriptive scrapbook** ("you logged 12 vegetables") — the fluff to
  avoid. Meal Scan photo logging is ~71% accurate → correction tax erodes trust.
- **Noom.** Behavior-change **framing** (the "why") is the differentiator, but avoid guilt ("you slipped"
  drives churn) and over-notifying (content "repetitive after month two").
- **Whoop.** Product arc **chat → proactive narrative**: the GPT-4 chat box was under-used and most-criticized
  (generic "get 10 hours of sleep," confidently wrong); the fix was **Daily Outlook**, a pre-generated
  *morning* card. Comparative phrasing ("your RHR is trending lower") is what users praise.
- **Oura.** **Two-tier** system to copy: cheap deterministic band messages + question-framed contributors for
  everyday "what happened," LLM **Advisor** reserved for open-ended "why/what should I do." Praised Advisor
  features: persistent **memory**, **tone toggle**. Criticism: it restates the score cards over a shallow
  7-day window.
- **Garmin.** Respected "intelligence" is almost all **non-generative** algorithms (Body Battery, Training
  Readiness). The one *generative-AI* feature ("Active Intelligence") is the weakest — "the laziest
  implementation of AI," restating one stat you already see. It only became useful when it **correlated two
  domains** (late-night eating ↔ sleep). Monetizing thin AI triggered a user petition.
- **Apple Health.** Its flagship AI coach (nutrition a "huge focus") was **scaled back, unshipped** — the
  nutrition-coaching whitespace is wide open. Steal the structural discipline: establish a **personal
  baseline** before alerting, then fire only on a **multi-signal anomaly** (Vitals); short-window-vs-long
  5-band verdict (Training Load); Monday weekly-recap cadence.
- **Hevy.** Best-loved mechanic is the **live PR banner** the instant a record set is completed. Weakness both
  reviewers cite: it logs and celebrates but never tells you what to do next.
- **RP Hypertrophy.** The reference for "insight that changes the *real* prescription": structured feedback →
  volume-landmark rules → a concrete next step ("pump low + no soreness → +2 sets next chest day"). Lesson:
  **state the driver and the resulting number**, keep the rule transparent, and don't dress a simple
  deterministic rule up as "AI."
- **Freeletics / EvolveAI.** On-demand "**Adapt Today**" (one-tap: no equipment / no time / sore → regenerated
  session) beats silent adaptation; show the causal link "you said X → I changed Y."
- **Strong, Future, Caliber, Samsung, Cronometer.** Reinforce the same split: computed transparent verdict =
  trusted; template band→canned-advice tip (Samsung "skip the gym") = fluff ceiling; human/hardware
  interpretation is the thing an AI-only app must *replace* with genuinely computed verdicts.

### What to steal / what to avoid
**Steal:** score→driver→one-action; comparative-to-own-baseline phrasing; MacroFactor's
`why-now → arithmetic → attribute-to-you → Approve/Adjust`; Cronometer preview-impact; **cross-domain
correlation** (the universal unmet need); proactive morning/weekly cards over a blank chat box; anomaly
alerts gated on a personal baseline; the Oura two-tier (deterministic card + LLM handoff for "why");
event-timed PR celebration that ends in an action.

**Avoid:** paraphrasing a stat the user can already see; walls of text; generic "walk more"/streak
gamification; silent target changes; guilt/moralizing; over-notifying; photo logging that needs constant
correction.

---

## 4. Proposed useful AI insight cards

Eleven cards. Every one ends in an **action**, uses the user's **own** data, and spends cloud tokens **only**
where the model synthesizes something the user can't assemble. Engine legend: **Deterministic** (no AI) ·
**Mix** (skeleton-merge: LLM writes ≤1 sentence over deterministic numbers) · **Cloud-LLM** (genuine
synthesis).

### P0 — the five that make the system feel like a coach

**1. Morning Readiness** — *replaces & relocates RECOVERY_READINESS (#2)*
Dashboard (compact echo on Body). **Proactive, first app-open of the day** (Whoop Daily Outlook cadence),
once/day if ≥2 recovery inputs exist. Uses sleep, energy/hunger/soreness vs personal 7-day baselines, whether
a session is planned today, yesterday's adherence. The user learns a single legible readiness read *before*
deciding how to train/eat, decisive driver named. **Action:** a training/intake adjustment now, + "Ask coach
to adjust today →". **Mix** — deterministic band & driver selection; LLM writes only the verdict sentence.
*Fixes the audit's #1 timing complaint.*
> *"5h sleep vs your usual 7.4 and soreness 8/10 — recovery's low. Keep today's session light or swap to
> mobility. Ask coach to adjust →"*

**2. Cross-Signal Discovery** — *rebuilds CROSS_METRIC (#7) from stub into the flagship*
Dashboard (hero slot when it fires). **Gated on a detected correlation** over a rolling window (Oura
"Discoveries"), at most one/week. Uses the whole cross-domain set — protein hit-rate, carb/meal timing,
surplus/deficit, sleep, energy/hunger/soreness, trained-days, weight/waist trend. The user learns a link
between two domains they cannot eyeball. **Action:** a testable tweak + one-tap "Track this →" that sets a
mini-experiment the next weekly card evaluates. **Cloud-LLM (genuine)** — deterministic detectors surface
*candidate* correlations (keeps the math honest); the LLM picks the most decision-relevant one and phrases
the hypothesis + action. **This is the app's differentiator — the market whitespace.**
> *"On your 3 lowest-protein days this month, next-morning hunger averaged 8/10 vs 4/10 otherwise. Hit 150g
> protein daily this week and see if the cravings ease? Track this →"*

**3. Meal Impact Preview** — *new; steals Cronometer striped-bar preview*
Food Log, on the meal add / plan confirmation sheet. **At decision time — before commit.** Uses the pending
meal's macros vs eaten totals & remaining targets. The user sees what this meal *will* do to their day.
**Action:** "log as-is / halve the rice to stay under carbs / add a protein source to close the 22g gap."
**Deterministic** — pure arithmetic, zero tokens.
> *"Adding this brings you to 92% protein but 108% carbs for the day. Log as-is, or swap the rice for greens
> to stay in range →"*

**4. Meal Suggestion** — *KEEP, essentially as-is (the house template)*
Food Log. Existing gate (>50% of day + macro gap + thick library). **Deterministic + optional coach
handoff.** The one card users love; protect it and copy its shape everywhere.
> *"≈520 kcal · 35g protein to go. Greek yogurt + berries (280 kcal, 24g P) closes most of it. Log →"*

**5. Weekly Verdict** — *consolidates WEEKLY_VERDICT (#9) + TARGET_CHANGE (#5), kills the triple redundancy*
The **Weekly Briefing** surface (one verdict, one place). **Weekly, proactive**, on review day, gated by the
existing InsightGate. Uses the full AdjustmentResult + reason codes. The user learns the decisive number →
why the target moves → the new number, **attributed to their own behavior**, tentative-then-confirmed.
**Action: Approve / Adjust** — never silent. **Mix (skeleton-merge)** — deterministic numbers; LLM writes
only the connective "why-now / attribute-to-you" prose (MacroFactor's trust shape) on the skeleton that
prevents number fabrication.
> *"Your weight trend held flat while you averaged 180 kcal under target — so your real maintenance looks
> ~120 kcal higher. I'd nudge your target from 2,150 to 2,270. Approve / Adjust →"*

### P1 — high-value upgrades & de-AI cleanups

**6. Recomp Progress Verdict** — *upgrades PROGRESS_TREND (#1) from description to prescription*
Trends screen, on open, when ≥2 trend points exist. Uses weight/waist/e1RM trends + adherence **plus
per-muscle volume & training frequency** (currently invisible to it). The user learns whether recomp is
happening *and the single limiting factor*. **Action:** one lever to pull. **Mix** — deterministic trend
classification; LLM synthesizes the multi-trend narrative + names the lever.
> *"Weight flat, waist down 0.3cm/wk, lifts up 4% — textbook recomp. Your only lag is bench e1RM (flat 3
> weeks). Add a set to chest this week →"*

**7. Training Readiness Handoff** — *new; closes the "coach is blind to training" gap*
Train screen, **pre-workout**, gated on a recovery signal. Uses soreness (per-muscle if available), sleep,
energy, days since last session for the target muscle. The user learns whether today's planned lift matches
their recovery. **Action:** a concrete session tweak + one-tap "Adapt session →" (Freeletics). **Deterministic**
band rules + optional coach handoff for "why."
> *"Chest is still sore 7/10 from Monday and you slept 6h. Legs are fresh — swap today to lower body, or cut
> chest to 2 sets. Adapt session →"*

**8. Scale Check** — *de-AI's NOISE_DEFUSER (#6): keep the gate, drop the LLM*
Dashboard, existing gate (jump ≥0.6 kg contradicting the smoothed trend). **Deterministic template** — the
LLM can't enrich three numbers.
> *"+0.8kg overnight, but your 2-week trend is still down 0.2kg/wk. That's water, not fat — trust the trend
> and keep logging. See trend →"*

**9. Weekly Pattern Spotlight** — *de-AI's WEEKLY_PATTERN (#4); stop paraphrasing*
Folded into the Weekly Briefing as a section. Renders the deterministic detector facts directly —
**including the 2nd-place fact the current card discards.** **Deterministic**, zero tokens.
> *"Saturday drove 68% of this week's surplus. Pre-log your Saturday dinner and you'd have landed on target.
> Plan Saturday →"*

### P2 — polish & gap-fillers

**10. Live PR Callout** — *new; steals Hevy's most-loved mechanic*
Train screen, inline, **the instant a logged set beats a stored record** (e1RM/heaviest/best-set volume/most
reps). **Deterministic.** Ends in a forward action (not a trophy) to stay non-fluff.
> *"New bench e1RM: 102kg, up from 99kg. Logged — I'll raise next week's target. Try +2.5kg on your last set? →"*

**11. Consistency Check-In** — *new; MacroFactor "clean data" framing, not a streak*
Dashboard, gated only when logging-consistency drops enough to degrade the adjustment engine's confidence
(not a daily nag). Framed as **input-quality, never virtue or guilt.** **Deterministic template.**
> *"You've logged 3 of the last 7 days. Two more and I can recalc your target with confidence — no guilt,
> just cleaner data. Log today →"*

---

## 5. Cards to keep / improve / merge / move / remove / replace

Disposition of the **existing** 10 cards (each maps to a proposal above).

| # | Card | Disposition | Reason | Effort |
|---|---|---|---|---|
| 1 | PROGRESS_TREND | **KEEP** (minor improve) | Real multi-signal recomp synthesis — the whitespace; just add an action + feed it volume/adherence | S / M |
| 2 | RECOVERY_READINESS | **IMPROVE** | Right shape; fix timing (proactive-morning) + add training-load & nutrition context | M |
| 3 | REST_OF_DAY | **REMOVE** | Dead/dormant, superseded by the deterministic #10 | S |
| 4 | WEEKLY_PATTERN | **REPLACE** (deterministic) + handoff | HERO slot paraphrasing a finished detector sentence; render it directly, show ranked facts | M |
| 5 | TARGET_CHANGE | **MERGE** → Weekly Briefing | Third narration of the same weekly verdict | M |
| 6 | NOISE_DEFUSER | **REPLACE** (deterministic copy) | Great gate, but a 3-number template the LLM can't enrich | S |
| 7 | CROSS_METRIC | **IMPROVE** (invest heavily) | Market's #1 unmet need; expand detectors + give LLM real series to interpret | L |
| 8 | ACTIVITY_NEAT | **REPLACE** (det.) / **MERGE** → Cross-Signal | Any-app "walk more"; either deterministic or a driver inside a real correlation | S |
| 9 | WEEKLY_VERDICT | **MERGE** → Weekly Briefing | Richest prompt, but redundant; route it into the skeleton-merge | M |
| 10 | MealSuggestionCard | **KEEP** (adopt as template) | The one useful card; deterministic + coach handoff = house style | S |

**Grouped:**
- **KEEP (2):** PROGRESS_TREND, MealSuggestionCard — the two legitimate roles for AI on cards (genuine
  cross-signal interpretation; deterministic-card-with-coach-handoff). Everything else should look like one of
  these.
- **IMPROVE (2):** RECOVERY_READINESS (timing + cross-domain context), CROSS_METRIC (the big genuine-value
  build — cross-domain correlation).
- **MERGE (2):** TARGET_CHANGE + WEEKLY_VERDICT → the Weekly Briefing, resolving the triple-redundant verdict
  onto the one surface that prevents number fabrication.
- **REPLACE (2–3):** WEEKLY_PATTERN + NOISE_DEFUSER swap LLM paraphrase for deterministic copy (keep their
  good gates/detectors, add coach handoffs); ACTIVITY_NEAT → deterministic or merged into Cross-Signal.
- **REMOVE (1):** REST_OF_DAY (dead code).

**Rule of thumb this establishes:** *if a deterministic detector already produced the sentence, render it
deterministically and spend the LLM only where it interprets raw multi-signal data the user cannot combine
themselves.*

---

## 6. Recommended next steps

1. **Subtract first (fast, high-signal).** Remove REST_OF_DAY; de-AI NOISE_DEFUSER, WEEKLY_PATTERN, and
   ACTIVITY_NEAT into deterministic templates (keeping their gates/detectors). This alone reclaims most of the
   wasted cloud tokens and removes the cards that read as filler — with almost no build risk.
2. **Consolidate the verdict.** Route WEEKLY_VERDICT's rich prompt into the Weekly Briefing and fold
   TARGET_CHANGE in. One weekly verdict, on the skeleton-merge surface, with MacroFactor's
   `why-now → arithmetic → attribute-to-you → Approve/Adjust` framing.
3. **Fix timing on the good cards.** Make Recovery a **proactive morning** card (Morning Readiness); add an
   **action** to PROGRESS_TREND (Recomp Progress Verdict).
4. **Build the differentiator.** Invest in Cross-Signal Discovery — expand the deterministic detector library
   (protein↔hunger, carbs↔energy, sleep↔next-day adherence, weekend↔surplus, steps↔weight-trend) and let the
   cloud model interpret the candidate series into a hypothesis + a one-tap experiment. This is the only place
   live cloud generation clearly earns its keep, and it's the market whitespace.
5. **Add the decision-moment cards.** Meal Impact Preview (deterministic, Food Log) and Training Readiness
   Handoff (deterministic, Train) — both cheap, both fire exactly when the user is deciding.
6. **Polish.** Live PR Callout and Consistency Check-In (both deterministic) once the core is in.
7. **Instrument before/after.** Track per-card interaction (tap-through, dismiss, handoff-open) so "useful"
   becomes measurable rather than felt — this is the missing feedback loop that let dead cards persist.

**Guardrails to hold throughout:** ≤1–3 sentences per card; never change a target silently; frame consistency
as data-quality not virtue; throttle proactive cards; every card ends in an action or a handoff, never a
period after a restated number.

---

## 7. Priority order

| # | Proposed card | Priority | Engine | Replaces / status |
|---|---|---|---|---|
| 1 | **Morning Readiness** | **P0** | Mix (skeleton-merge) | Replace/relocate RECOVERY_READINESS |
| 2 | **Cross-Signal Discovery** | **P0** | Cloud-LLM (genuine) | Rebuild CROSS_METRIC |
| 3 | **Meal Impact Preview** | **P0** | Deterministic | New |
| 4 | **Meal Suggestion** | **P0** | Deterministic + handoff | Keep (protect) |
| 5 | **Weekly Verdict** | **P0** | Mix (skeleton-merge) | Consolidate #9 + #5 + Briefing |
| 6 | Recomp Progress Verdict | P1 | Mix (skeleton-merge) | Upgrade PROGRESS_TREND |
| 7 | Training Readiness Handoff | P1 | Deterministic + handoff | New (fills training gap) |
| 8 | Scale Check | P1 | Deterministic | De-AI NOISE_DEFUSER |
| 9 | Weekly Pattern Spotlight | P1 | Deterministic | De-AI WEEKLY_PATTERN |
| 10 | Live PR Callout | P2 | Deterministic | New |
| 11 | Consistency Check-In | P2 | Deterministic | New |

**Coverage map:**

| Screen | Cards | Timing |
|---|---|---|
| **Dashboard** | Morning Readiness, Cross-Signal Discovery, Scale Check, Consistency Check-In | Proactive morning + gated anomalies |
| **Food Log** | Meal Impact Preview, Meal Suggestion | At decision time (pre-commit / mid-day gap) |
| **Trends** | Recomp Progress Verdict | On review-screen open |
| **Train** | Training Readiness Handoff, Live PR Callout | Pre-workout + event-timed PR |
| **Body** | Morning Readiness (compact echo) | Proactive morning |
| **Weekly Briefing** | Weekly Verdict, Weekly Pattern Spotlight | Weekly, proactive |

**Deterministic-vs-LLM ratio:** 11 cards → **7 fully deterministic, 3 skeleton-merge** (LLM writes ≤1
sentence over deterministic numbers), **1 genuine cloud-LLM** (Cross-Signal Discovery). This realizes the
audit's "cut from ~6 paraphrase-heavy cloud cards down to ~2–3 that genuinely synthesize" — every remaining
token is spent on cross-domain or trend→action synthesis, never on paraphrase, and proactive timing replaces
scroll-reactive firing on the four most important cards.

### Cards deliberately NOT proposed (anti-fluff discipline)
- A standalone **Activity/NEAT "walk more"** card — generic boilerplate; steps belong inside the Weekly
  Verdict's energy-balance math as a TDEE input.
- A standalone **Target-Change** card — a third narration of the same weekly decision; folded into the verdict.
- An **"ask me anything" chat-first hero** card — every competitor is migrating *away* from the blank chat box;
  chat stays a secondary affordance reached via handoffs.
- A daily **"wellness tip" template** card — the documented fluff ceiling of a one-liner (Samsung).
- A **streak / "you logged 12 vegetables" digest** — descriptive, not prescriptive; drives churn (MFP/Noom).
