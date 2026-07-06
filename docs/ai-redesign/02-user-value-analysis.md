# 02 — User-Value Analysis (brutally honest)

A no-flattery pass over every AI surface in the app, judged by one question only: **does a real
user get value they'd act on?** Context that frames the whole document: the owner used the app for
~1 month and *rarely found a single AI card useful enough to change behavior*. That is the verdict
to explain, not to soften. Assumes a **cloud-only** AI going forward (rich prompts, long context,
unbounded tools).

Evidence base: `01-current-ai-audit.md` (what each surface is/consumes/ignores),
`04-data-utilization.md` (what data is walled off), `05-market-research.md` (what actually earns
trust). Citations reference those docs and `file:line` on `develop`.

The market research gives the yardstick, repeatedly: **"Whoop only delivers value to users who act
on the data"** and **"vanity/opaque scores with no attached decision are ignored"**
(`05-market-research.md` §3). Every surface below is scored on *decision attached* and *behavior
changed*, because that is the only thing the market rewards.

---

## The core diagnosis (why the owner ignored the cards)

Three structural reasons the cards failed, all confirmed by the audit:

1. **They narrate, they don't decide.** WEEKLY_PATTERN and CROSS_METRIC literally hand the model a
   finished numeric sentence and ask it to paraphrase (`01`§2.4, §2.7). NOISE_DEFUSER and
   ACTIVITY_NEAT are 2–3 number prompts the model can't enrich (`01`§8.2). The user reads a
   sentence that restates a number they already saw on the same screen. No new information, no
   decision → nothing to act on.
2. **They're siloed and shallow.** Every card sees one pre-computed context object and nothing else
   (`01`§2.10, `04` "AI-poor"). The app stores waist, skinfold, RIR, e1RM, sleep, soreness, notes —
   and *none of it reaches a card*. So the cards can't say the one thing this app is uniquely
   positioned to say (cross-domain cause), and instead say thin single-domain things a smart-scale
   already says.
3. **They're 100% reactive.** Nothing reaches out; the cards wait to be scrolled past (`01`§7). The
   market's most-valued behavior — "felt accountability from something that notices"
   (`05`§1, §5.7) — is completely absent. A card you have to go find, that then tells you nothing
   new, is a card you stop looking at by week two. That is exactly what happened.

Everything below is a consequence of these three.

---

## Feature-by-feature

### PROGRESS_TREND — Trends screen
- **Why open it:** to see if the plan is working. Legitimate intent.
- **Care?** Somewhat — trend direction is the whole point of a recomp app.
- **Concrete action?** Weak. It ends with "one thing to keep doing," which is a *don't-change*
  message, not an action.
- **Behavior change?** Rarely — it's a narrator of the chart directly above it.
- **Missed if gone?** The chart wouldn't be. The sentence might not be noticed.
- **Verdict: Improve.** The intent is real but the card is blind to *why* the trend is what it is —
  no recovery, no intake, no training (`01`§2.1). This should become the recomposition-signal
  surface (`04` insight #1: weight flat + waist/skinfold down = "you're recomping"), which is the
  single most motivating message the app can send and is computed for the AI nowhere today.

### RECOVERY_READINESS — Body screen
- **Why open it:** "am I ok to train hard today?" A high-intent question the market proves people
  value (Whoop/Oura readiness, `05`).
- **Care?** Yes, *if* it's trustworthy and carries a decision.
- **Concrete action?** Partially — it gives "one suggestion."
- **Behavior change?** Low today, because it's blind to actual training load (only a boolean
  `trained`) and to nutrition (under-eating drives poor recovery, invisible here) (`01`§2.2). It's a
  readiness score without the inputs that make readiness meaningful.
- **Missed if gone?** The raw sleep/energy/soreness numbers wouldn't be. The AI sentence would.
- **Verdict: Improve (invest).** This maps directly onto the proven Whoop/Oura pattern — but only
  if it earns the deload/train-hard decision from RIR + soreness + sleep trend (`04` insight #3),
  not just today's scores. Right now it's a vanity score; the market ignores those (`05`§3).

### REST_OF_DAY — Food Log (today only)
- **Why open it:** "what should I still eat?" — the highest-frequency, highest-intent moment in a
  food tracker.
- **Care?** Yes. This is arguably the most naturally-useful surface in the app.
- **Concrete action?** Yes in principle ("prioritize protein for remaining meals").
- **Behavior change?** Held back badly: it can't name a food. It ignores carbs/fat, what was
  actually eaten, planned meals, and the food library (`01`§2.3). "You're low on protein" without
  "your usual chicken breast is 40g" is advice the user already knew.
- **Missed if gone?** The remaining-macro numbers wouldn't be (they're deterministic). The sentence
  would be forgettable.
- **Verdict: Improve (invest).** Give it the food library + eaten foods and it becomes concrete and
  daily-useful ("add 150g of your saved Greek yogurt to close the 30g protein gap"). That is a real
  action, at the exact moment of intent. Highest-leverage daily card.

### WEEKLY_PATTERN — Dashboard hero "Coach spotted"
- **Why open it:** it's the big hero card; the user sees it whether they want to or not.
- **Care?** Marginally. The finding ("Saturday drove 68% of the surplus") is genuinely useful — but
  it's produced by a **deterministic detector**, not the AI (`01`§2.4).
- **Concrete action?** The detector's statement implies one; the LLM adds "one small suggestion."
- **Behavior change?** The *detector* could change behavior. The *AI layer* changes nothing — it
  paraphrases a complete sentence.
- **Missed if gone (the AI part)?** No. The detector's statement stands alone.
- **Verdict: Merge/Remove the AI layer.** Keep the detector; it does the work. The LLM paraphrase is
  near-zero marginal value and costs tokens (`01`§8.2). Fold the finding into the weekly
  check-in/briefing rather than a cosmetic dashboard rewrite.

### TARGET_CHANGE — Dashboard "Why your target changed"
- **Why open it:** "why did my calories move?" — a real trust question (MacroFactor's whole edge is
  explaining this, `05`).
- **Care?** Yes, *when it fires*.
- **Concrete action?** No — it's explanatory by design.
- **Behavior change?** It builds trust in the auto-adjustment, which is valuable, but it duplicates
  two other surfaces.
- **Missed if gone?** Not as a standalone card — the same explanation lives in the legacy verdict
  card and the Weekly Briefing.
- **Verdict: Merge.** Strict subset of the legacy weekly-verdict card and the Briefing action block
  (`01`§2.5, §8.1). Three surfaces narrate one decision. Collapse into the Briefing.

### NOISE_DEFUSER — Dashboard "Scale check"
- **Why open it:** the user just saw a scary +0.8kg jump and wants reassurance. Genuinely
  well-timed.
- **Care?** Yes, in that moment — this is the one card with real emotional utility.
- **Concrete action?** Deliberately none ("nothing to act on"). That's correct here.
- **Behavior change?** Prevents a *bad* behavior change (panic-cutting) — valuable, but it's a
  2-number prompt the model can't enrich (`01`§2.6).
- **Missed if gone?** The reassurance would be missed on jump days. It's cheap and good.
- **Verdict: Keep (as templated copy).** Tightly-scoped, well-timed, trust-building. But it barely
  needs an LLM — it's a template. Keep the behavior; don't pay much for the "AI."

### CROSS_METRIC — Dashboard "Coach noticed a link"
- **Why open it:** curiosity. "The coach noticed something" is an appealing promise.
- **Care?** Could be high — behavior→outcome links are exactly what feels like coaching (`05`§1).
- **Concrete action?** One suggestion.
- **Behavior change?** Low, because only **one** link is implemented (protein↔hunger) and it's a
  paraphrase of a pre-computed statement (`01`§2.7). The promise is powerful; the delivery is one
  hard-coded pair.
- **Missed if gone?** As-is, no. The *concept* is one of the biggest opportunities in the app.
- **Verdict: Replace.** Kill the single hard-coded paraphrase; rebuild as a real cross-domain layer
  (sleep↔lifts, steps↔weight-stall, training↔recovery, RIR+soreness→deload — `04` insights #2–5).
  This is the market's biggest open gap ("almost nobody reasons across domains," `05`§4). Worth
  real investment — but not in its current form.

### ACTIVITY_NEAT — Dashboard "Activity"
- **Why open it:** step count. Every fitness app and the phone itself already shows this.
- **Care?** Low. It's a "walk more" nudge with no recomp context (`01`§2.8).
- **Concrete action?** "Walk more" — technically an action, practically ignored.
- **Behavior change?** Minimal. This is the definition of a commodity smart-scale nudge.
- **Missed if gone?** No. The step ring/streak already conveys it.
- **Verdict: Remove (as a standalone card).** Its only real value is as an *input* to cross-domain
  reasoning ("steps dropped 3k/day, that's why the weight stall" — `04` insight, steps↔energy).
  Standalone, it's noise. Fold the signal into the weekly check-in; delete the card.

### Legacy WEEKLY_VERDICT card — Dashboard "Calorie Decision"
- **Why open it:** it fires on the dashboard when the plan changes; the user encounters it.
- **Care?** Yes — the weekly calorie decision is the core of the app.
- **Concrete action?** Yes: "trim from 2500 to 2300." This is the richest, most decision-carrying
  insight prompt in the app (`01`§2.9, full `AdjustmentResult` + all signals).
- **Behavior change?** Highest potential of the insight cards — it's an actual verdict with a number.
- **Missed if gone?** The *decision* would be, but it's told three times.
- **Verdict: Merge (into the Briefing).** Best content, worst duplication. This, TARGET_CHANGE, and
  the Briefing all narrate the same verdict with three prompt builders and three copy styles
  (`01`§8.1). One canonical weekly verdict, told once, on the Briefing's skeleton-merge
  architecture.

### Coach chat — Coach tab
- **Why open it:** ask a question, or log by voice/text.
- **Care?** Depends entirely on whether it knows *their* data. Generic AI chat is the thing users
  most reliably ignore (`05`§3: "generic AI chat with no personalization adds little").
- **Concrete action?** Yes — it can log meals/metrics/targets with confirmation (`01`§3).
- **Behavior change?** Real for the logging path. Weak for the advice path, because it is **blind to
  training** — no workout tool at all, in a *recomposition* app (`01`§3 "headline value gap";
  `04`§Redesign #1). It can't answer "how's my bench going?"
- **Missed if gone?** The logging convenience would be. The coaching wouldn't be, until it can see
  lifts + body history.
- **Verdict: Keep + invest.** The architecture is sound (tool loop, confirmations, honest failure
  handling, knowledge grounding). The gap is data access, not plumbing. Add `get_training_summary`
  and body-measurement history and it becomes the cross-domain coach nobody else ships. This is a
  top-3 investment. But it should be the *on-demand supplement*, not the main event (`05`§5.2).

### Weekly Briefing — Dashboard overlay
- **Why open it:** the badge signals "your week is ready." Closest thing to the proven
  weekly-check-in cadence the whole market converges on (MacroFactor/Whoop/Oura, `05`§1).
- **Care?** Yes — a curated weekly assessment that comes to you is *the* pattern users reward.
- **Concrete action?** Yes: it wraps the actual verdict + apply-target.
- **Behavior change?** Highest ceiling in the app — it's the right shape (deterministic skeleton +
  prose merge so the model can't corrupt numbers, `01`§4).
- **Missed if gone?** Yes. This is the surface most worth keeping.
- **Verdict: Keep + invest (make it the spine).** Its only real weakness is that it's still a
  *passive badge*, not a push, and it's fed only the 5 graded signals — not training, not notes, not
  cross-domain cause. Make it proactive and feed it everything. This is the #1 investment.

### Recipe namer — Recipe builder
- **Why open it:** fun. "Anabolic Oats." User explicitly taps it.
- **Care?** In a low-stakes, on-brand novelty way, yes.
- **Concrete action?** N/A — it's flavor, not coaching.
- **Behavior change?** None intended.
- **Missed if gone?** Mildly — it's a delight touch, cheap, self-contained, opt-in.
- **Verdict: Keep.** Cheap, isolated, on-brand, cloud-ready as-is (`01`§5). It doesn't pretend to be
  coaching, so it isn't judged as coaching. Leave it alone.

### Knowledge base — corpus + retriever
- **Why open it:** invisible to the user; it grounds the coach.
- **Care?** Indirectly — it's why coach answers aren't generic.
- **Concrete action?** N/A (infrastructure).
- **Behavior change?** Raises coach answer quality; that's its whole job.
- **Missed if gone?** The coach would get more generic — and generic is the ignored failure mode
  (`05`§3).
- **Verdict: Keep + extend.** It's prepended text, not a tool, so it's near-free to also ground
  insight cards and the Briefing — currently chat-only (`01`§6, §8.4.4). Extend it to the two
  surfaces worth investing in.

---

## 1. Verdict table (sorted by verdict)

| Feature | Verdict | Why (one line) |
|---|---|---|
| Weekly Briefing | **Keep + invest** | Right architecture, right cadence — make it proactive and the spine of the AI. |
| Coach chat | **Keep + invest** | Sound plumbing; blind to training/body-history — fix the data access, not the code. |
| Knowledge base | **Keep + extend** | Near-free grounding; stop restricting it to chat, ground insights + Briefing too. |
| NOISE_DEFUSER | **Keep** | Well-timed anti-panic reassurance; barely needs an LLM but earns its slot. |
| Recipe namer | **Keep** | Cheap, opt-in, on-brand novelty; doesn't pretend to coach. |
| PROGRESS_TREND | **Improve** | Real intent, but blind to *why*; rebuild as the recomposition-signal surface. |
| RECOVERY_READINESS | **Improve** | Proven readiness pattern, but a vanity score until it carries a deload/train decision. |
| REST_OF_DAY | **Improve** | High-intent daily moment crippled by no food-library/eaten-food access. |
| CROSS_METRIC | **Replace** | Powerful promise, one hard-coded paraphrase; rebuild as a real cross-domain layer. |
| ACTIVITY_NEAT | **Remove** | Commodity "walk more" nudge; keep the signal as a cross-domain input only. |
| WEEKLY_PATTERN (AI layer) | **Merge/Remove** | Detector does the work; the LLM paraphrase adds nothing — fold into the check-in. |
| TARGET_CHANGE | **Merge** | Strict subset of the weekly verdict; one of three surfaces telling one story. |
| Legacy WEEKLY_VERDICT card | **Merge** | Best verdict content, worst duplication; collapse into the Briefing. |

---

## 2. The delete list (net-negative or pure noise)

Fewer high-trust surfaces beat many weak ones (`05`§5.8, "ship narrow not Mulberry-wide"). Delete:

1. **ACTIVITY_NEAT card (standalone).** A "walk more" nudge with no recomp context (`01`§2.8) —
   duplicates the step ring/streak the user already sees, adds nothing the phone doesn't. Net
   clutter. Keep steps only as an *input* to cross-domain reasoning.
2. **WEEKLY_PATTERN's LLM paraphrase layer.** The detector produces a complete numeric sentence; the
   model just rewords it (`01`§2.4, §8.2). Pure token cost for cosmetics. Keep the detector, delete
   the AI rewrite (or fold the finding into the weekly check-in).
3. **CROSS_METRIC as currently built.** One hard-coded protein↔hunger paraphrase masquerading as
   "the coach noticed a link" (`01`§2.7). It over-promises and under-delivers, which actively erodes
   trust the way a wrong/thin output does (`05`§3). Delete the current implementation (the *concept*
   is reborn in the invest list, not kept as-is).
4. **TARGET_CHANGE card + legacy WEEKLY_VERDICT card as separate dashboard surfaces.** Not deleted
   for being bad — deleted for being *redundant*. Three surfaces narrate one weekly verdict
   (`01`§8.1). Two of the three are pure duplication and should not exist independently.
5. **Dead `rich` insight modes.** 4–6 sentence prompt variants that are never invoked, even by cloud
   (`01`§2.10). Either wire them up (for the invest surfaces) or delete them — right now they're
   dead capability implying the cloud model is under-used.

Net effect: the dashboard drops from a wall of thin, ignorable cards to a small set of surfaces that
each carry a decision.

---

## 3. The merge map (redundant surfaces saying the same thing)

**The weekly verdict is narrated in THREE places.** This is the single worst redundancy
(`01`§8.1). Collapse to one:

```
  legacy WEEKLY_VERDICT card ─┐
  TARGET_CHANGE card ─────────┼──►  ONE canonical Weekly Briefing verdict
  Weekly Briefing action block ┘     (deterministic skeleton + prose merge,
                                      the pattern already proven in the Briefing)
```
- **Keep:** the Briefing's architecture (model can't alter numbers/verdict — `01`§4).
- **Fold in:** TARGET_CHANGE's "why the number moved" explanation (MacroFactor's trust mechanism,
  `05`§1) becomes a *section* of the Briefing, not a separate card.
- **Delete:** both standalone dashboard cards.

**Second merge — dashboard pattern findings into the check-in:**
```
  WEEKLY_PATTERN detector fact ─┐
  ACTIVITY_NEAT step signal ────┼──►  weekly check-in modules
  CROSS_METRIC link ────────────┘     (curated, "only when it can change the trajectory")
```
Rather than 3–4 always-on dashboard cards each paraphrasing one signal, surface the *strongest*
finding(s) inside the weekly check-in, MacroFactor-style curation (`05`§2). One place, curated,
decision-attached — instead of scattered narrators.

**Third merge — two coach system-prompt builders → one.** `GemmaCoachCoordinator.buildSystemPrompt`
(2B-nursing wording) dies with local; only `CoachToolsAdapter.buildPrompt` survives (`01`§8.1). Free
cleanup once cloud-only.

---

## 4. The 2–3 surfaces worth truly investing in

Concentrate the AI budget here; let the rest be deterministic or deleted.

### #1 — The Weekly Briefing, made proactive and cross-domain (the spine)
**Why:** Every serious competitor converges on a curated weekly check-in that *comes to the user*
(MacroFactor, Whoop, Oura — `05`§1). The app already has the right architecture (deterministic
skeleton + prose merge) and the right trigger scaffolding — but it's a passive badge fed only 5
graded signals. Two changes make it the best-in-class surface:
- **Make it proactive.** The `proactiveReview` capability and the deterministic weekly pipeline
  already exist and *nothing fires them* (`01`§1.1, §8.4.1). A weekly push is the highest-leverage
  single add in the whole redesign. "Felt accountability from something that notices" is the most
  defensible coaching value in the market (`05`§5.7).
- **Feed it everything and name the cross-domain cause.** Give it training, notes, and raw series so
  it can say the one thing nobody else can: *"weight trend flat despite the deficit; steps down
  3k/day and lifting volume up — holding calories, nudging the step target"* (`05`§5.1). That
  cross-domain verdict is the app's whole reason to exist and the market's biggest open gap
  (`05`§4).

### #2 — The Coach, given eyes on training + body history (the on-demand supplement)
**Why:** The plumbing is already good (tool loop, write-confirmations, honest failures, knowledge
grounding — `01`§3). The gap is purely **data access**: no workout tool, no body-measurement
history, in a *recomposition* app (`01`§3, `04`§Redesign #1). Add `get_training_summary` (sessions,
sets, RIR, e1RM/volume — all already computed in `domain/workout/`) and body-history reads, and the
coach can finally answer "how's my bench going, and is my cut capping it?" — closing the
lifting→nutrition loop that even Hevy leaves open (`05`§4). Keep it as the *supplement* to the
weekly spine, not the main event (`05`§5.2).

### #3 — A real cross-domain insight layer (rebuilt from CROSS_METRIC's ashes)
**Why:** The current CROSS_METRIC is one hard-coded pair, but the *idea* — behavior→outcome links —
is what actually feels like coaching (`05`§1, §6) and is the single biggest market gap (`05`§4). The
data for 5 high-value links already exists and reaches no AI today (`04` insights #1–5):
recomposition signal (weight flat + waist/skinfold down), e1RM-stall plateau + strength↔nutrition,
RIR+soreness+sleep deload signal, training-day vs rest-day nutrition, sleep↔lifts/hunger. Built well,
each carries a decision (the market's non-negotiable, `05`§4) and feeds the weekly spine. This is the
highest-*ceiling*, highest-*risk* investment — do it only after the Briefing and Coach are solid,
and only with every insight carrying a "so do X."

**One principle over all three:** *every score and insight must carry a decision.* The universal
market failure is orphaned metrics (`05`§3, §5.4), and it's exactly why the owner ignored the cards —
they narrated numbers without telling him what to do. Fix that, on fewer surfaces, and the AI starts
earning its place.
