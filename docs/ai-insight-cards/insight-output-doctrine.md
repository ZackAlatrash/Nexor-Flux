# AI Insight Card Output Doctrine

How our AI insight cards *should* read, derived from how the best health/fitness apps turn stats
into proactive coaching. This is the target spec the cloud prompts are tuned against and the
rubric the LLM-judge scores against.

**Audience:** anyone editing `InsightPromptBuilder` or the insight context objects.
**Scope:** the cloud-powered insight cards (`CloudInsightCoordinator` → OpenAI-compatible model).
Last updated 2026-06-15.

---

## 1. The one-paragraph thesis

The raw numbers are already on the dashboard. **The card's job is synthesis, not restatement:**
take many signals, compare each to *this user's own baseline*, surface the single most decisive
one, say why it matters in a few words, and give exactly one concrete next step — in a warm,
shame-free voice. And when nothing is meaningfully off-baseline, the best card is a quiet "you're
on track" or *no card at all*. Everything below is detail on that sentence.

---

## 2. How the top apps do it (field findings)

Four independent research sweeps (WHOOP/Oura, Fitbit/Apple/Samsung, MacroFactor/Carbon/RP, plus
the behavior-science literature) converged on the same playbook.

### WHOOP & Oura — synthesis + thresholded proactivity
- **Real Oura Advisor outputs:** *"Your heart rate variability dipped 12% last night. Consider
  reducing training intensity today."* · *"Great recovery overnight! Your readiness is 88 — a
  high-intensity session is ideal."* · *"Recovery looks normal — no adjustments needed."*
- Every readiness contributor is *defined* as a baseline comparison (HRV vs. 14-day-vs-3-month,
  temp vs. long-term average). Insights fire only when a metric extends beyond the personal
  average — **thresholded, not scheduled.**
- WHOOP Coach fuses recovery + strain + sleep + HRV into **one** steer; modulates the action
  ("do 4 repeats instead of 6"), never binary "rest vs. go."
- Tone: conversational, advisory, "never aggressive or pushy" (Oura even ships a tone toggle).

### Fitbit / Apple / Samsung — score → driver → action, and "Keep it Going"
- Pattern: composite score (Readiness/Energy 0–100) → decompose to the **single dominant driver**
  → one recommended intensity for today.
- Apple Trends compares ~90-day vs ~365-day averages and buckets every metric as **Keep it
  Going / Worth a Look / Needs More Data** — it never fakes a trend on thin data, and when a
  metric is good it says "you're still doing great" instead of inventing a stretch goal.
- Apple Vitals only notifies when **2+ metrics agree** and offers *hedged* candidate causes
  ("this could be due to illness or alcohol") — never a diagnosis.
- Fitbit coach one-liners are short, number-bearing, qualifier-tagged: *"You got 7h 14m sleep and
  a fair score of 72."*

### MacroFactor / Carbon / RP — the nutrition gold standard
- **Coach the trend, not the daily number.** Trend weight = "what your weight would be with zero
  fluctuations." Name the noise to pre-empt panic.
- **Small first adjustment, escalate with conviction:** week 1 makes only tentative ~200–300 kcal
  moves; larger moves only after 3–4 consistent weeks.
- **Explain *why a target changed* in one causal sentence that blames the data/metabolism, not the
  user:** "You averaged X and lost Y/wk — faster than your Z goal — so estimated expenditure rose
  to ~W. New target: …"
- **Judge against the *desired weekly rate*, never cumulative "catch-up."**
- **Adherence is an unlock, not a scold:** more consecutive clean weeks → more sophisticated
  evaluation. Low logging → "I'm keeping this read conservative," not "you failed."
- **Zero shame, no red numbers** — shame makes users stop logging bad days, which poisons the data.

### Behavior science — the active ingredients
- **Fogg (B = MAP):** most users already have motivation; the missing lever is **ability** — make
  the action trivially small and specific.
- **JITAI:** fire on a meaningful *state change*, target the moment the user can still act, and
  treat **"provide nothing" as a first-class output.** This single rule kills most filler.
- **Effective BCTs:** goal-setting + self-monitoring + feedback-on-behavior, with cue-anchored
  actions ("after X, do Y").
- **Framing:** gain-frame by default (works for engaged/high-efficacy users); reserve gentle
  loss-framing for a slipping user. Never shame — it lowers self-efficacy.
- **Trust:** LLM health advice hallucinates while sounding authoritative. Only cite numbers in the
  snapshot; hedge one-day blips, assert multi-day trends; stay in scope (nutrition/training, not
  medicine); degrade gracefully on thin data.

---

## 3. The 10 rules (the doctrine)

These are the generation constraints. Every card must satisfy them; the judge scores against them.

1. **Compare to baseline.** State the decisive number *relative to* the user's goal, 7-day, or
   30-day average — never an isolated value. "Protein under target 5 of the last 7 days," not
   "protein was 110 g."
2. **Synthesize to one verdict.** Many signals in → one headline out. Don't list the dashboard.
3. **Observation → why → one action**, in that order.
4. **One card, one action.** No stacked asks, no menus. Make the action small, specific, and
   cue-anchored where possible.
5. **Name the driver** when the headline number is ambiguous (eaten-vs-planned, skipped meal vs.
   on-plan).
6. **Coach the trend; name the noise.** A scale jump that contradicts the trend → say it's water.
7. **"On track" and "nothing to say" are valid outputs.** Don't manufacture concern when flat and
   on-plan. (In the harness this surfaces as a low "should-fire" score, not filler text.)
8. **Explain target changes causally**, blaming data/metabolism, never willpower. Small first move.
9. **No shame; gain-frame; autonomy-supporting tone.** Offer ("one option is…"), don't command.
   Acknowledge progress/partial wins before the gap.
10. **Calibrate certainty to data depth; never hallucinate.** Hedge blips, assert trends, only use
    given numbers, and on thin data say so honestly.

---

## 4. Copy structures (drop-in templates)

- **Deviation → action (workhorse):**
  `"Your {metric} {direction} {N}{unit} {vs. baseline window}. {one small action} today."`
  → "Protein ran 30 g under target 3 days running — add a Greek-yogurt snack today."
- **Driver-aware verdict:**
  `"{headline} is {value}, driven mostly by {driver}. {action keyed to the driver}."`
  → "You're 400 kcal under, mostly from a skipped lunch — front-load tomorrow rather than overeat tonight."
- **Target-change explainer:**
  `"{what you did} → {what happened} → {what it implies} → {new number}."`
  → "You averaged 2,500 kcal and kept losing 0.6 kg/wk — faster than your 0.4 goal — so expenditure looks higher (~2,850). Bumping your target to 2,600."
- **Noise-defuser:**
  `"{scary number today}, but {trend reality}. {cause + reassurance}."`
  → "Up 0.9 kg this morning — but your 10-day trend is flat. Likely sodium and carbs from yesterday; nothing to act on."
- **Adherence-as-unlock (not scold):**
  `"{neutral note on logging gaps}. {what it limits}. {low-friction ask}."`
  → "3 days were only partly logged, so I'm keeping this week's read conservative. A full week lets me dial targets in tighter."
- **Stay-the-course (on track):**
  `"{verdict in 4–6 words}. {trend stat + rate}. No change — keep doing exactly this."`
- **Insufficient data (honest):**
  → "Only 2 days logged this week — not enough to call a trend yet. A few more days and I can compare."

---

## 5. Per-card target spec

For each card: what it reads today, what it should *also* read (context extensions), and the
target shape. Card mechanics live in `docs/ai-coach.md`; this is the **output** spec.

### 5.1 Weekly Summary (verdict)
- **Now:** verdict + reason codes + this-week signals (weight/waist/perf/recovery/adherence).
- **Add:** prior weeks' trend (is this accelerating/decelerating?), desired weekly rate, weeks in
  phase already present.
- **Target:** lead with the decisive signal vs. the goal rate, name the driver, state the verdict
  and *why* in one causal clause. Stay-the-course is a confident output, not a shrug.

### 5.2 Progress Trend
- **Now:** weight/waist/lift/adherence trends over a window.
- **Add:** baseline comparison (this window vs. prior window), point counts already present.
- **Target:** cross-signal interpretation that calls out tension or agreement ("weight flat while
  waist down and lifts up — recomposition"). No calorie advice (that's the Weekly Summary's job).

### 5.3 Recovery Readiness
- **Now:** today's sleep/energy/hunger/soreness + trained flag.
- **Add:** each score vs. the user's recent personal average (so "5 h" reads as "2 h below your
  usual").
- **Target:** one readiness verdict + one modulated suggestion. Hedged, never medical.

### 5.4 Rest of Day
- **Now:** calories/protein consumed vs. target, calorie zone, meals logged.
- **Add:** time of day / typical remaining-meals pattern if available; pace vs. a normal day.
- **Target:** where they stand + one priority for remaining meals. Frame the gap, no invented foods.

### 5.5 Weekly Pattern
- **Now:** a single `InsightFact` statement from `InsightEngine`.
- **Add:** confidence/priority already present; surface it as hedged vs. asserted language.
- **Target:** rephrase the finding as one encouraging observation with the number up front.

### 5.6 New cards (this redesign)
- **Target-change explainer** — the load-bearing MacroFactor card; fires when the verdict changes
  the calorie target. Uses the target-change template above.
- **Noise-defuser** — fires when today's scale reading contradicts the smoothed trend.
- **Cross-metric "aha"** — links two domains (protein↔hunger, adherence↔weight-trend,
  deficit↔energy). Flagged by the research as the highest-value, most "intelligent-feeling" card.

---

## 6. Anti-patterns (banned — the judge penalizes these)

| Anti-pattern | Why it fails |
|---|---|
| Vague ("eat better", "stay consistent") | No ability lever, no action |
| Restating the log ("you ate 1,800 kcal") | No comparison, no "so what" |
| Preachy / commanding ("you must…") | Kills autonomy, breeds resistance |
| Shaming / red-number framing | Lowers self-efficacy → users stop logging |
| Multi-action firehose | Paralysis |
| Hallucinated metric (a number not in the snapshot) | Destroys trust permanently |
| Overconfident on thin data | Wrong + authoritative = over-trusted |
| Filler when on-track | Trains users to ignore cards |
| Medical overreach ("you're overtrained / deficient") | Out of scope |
| Repeating an ignored insight verbatim | Desensitization |

---

## 7. The LLM-judge rubric

Each generated card is scored 1–5 on five axes plus a gate. Two judge modes (harness
`-DinsightJudge`): **manual/Claude** (default — the harness prints clean outputs and a human or
Claude scores them; used because a weak free model judging itself is too noisy) and **model** (the
automated LLM-judge below — best pointed at a strong judge model).

- **Accuracy (1–5):** uses only numbers present in the prompt; no invented metrics.
- **Actionability (1–5):** exactly one concrete, small next step.
- **Proactivity / insight (1–5):** baseline comparison, names the driver, non-obvious synthesis.
- **Tone (1–5):** warm, autonomy-supporting, zero shame.
- **Brevity (1–5):** ≤ 2 sentences, no preamble or filler.
- **Should-fire (pass/fail):** given the data, should this card have spoken at all? (Catches filler
  on on-track/flat states.)

A card "passes" an iteration when every axis ≥ 4 and should-fire is correct for the scenario.
