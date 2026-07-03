# AI Coaching — Market Research (2024–2026)

Research into how leading fitness/health apps use AI and algorithmic coaching, to inform the
redesign of our AI coach. Our app is unusual in that it combines **nutrition + lifting + steps +
body metrics + a recomposition adjustment engine** in one place; almost every competitor below is
strong in only one domain. The goal here is to extract **principles and openings**, not features to
copy.

Scope: prioritised the apps most relevant to nutrition + lifting + recovery (MacroFactor, Whoop,
Oura, Hevy, RP, Caliber, Future, Noom), with lighter coverage of the rest.

---

## Per-app findings

### MacroFactor — adaptive nutrition (most relevant)
**What its "AI"/algorithm does.** Not an LLM chatbot at its core — it's an *adaptive expenditure
algorithm*. Instead of a static TDEE formula, it reverse-calculates your true energy expenditure
from your actual food logs + weight trend over multiple weeks, then recommends intake/macro
adjustments at a **weekly check-in**. Expenditure has two states, "holding" and "updating"; it needs
~6/7 days of nutrition logs + ≥1 weight entry per week to keep updating, and applies an
"intelligent smoothing" layer to avoid over-correcting on noisy weeks. It self-reports that after
3–4 weeks its recommendations are "120–170% more accurate" than a standard TDEE equation.
- The weekly check-in surfaces curated **"Coaching Modules"** (MF Coach) chosen from your habits and
  progress — feedback on logging quality, common logging-error resolution, and adjustment rationale.
- Design philosophy is explicitly *minimally disruptive*: "only present new information when
  helpful and only ask questions when answers can meaningfully impact your goal" — a deliberate
  stance against invasive questionnaires and notification spam.
- Weight trend uses a moving average so a post-meal spike shows as ~0.2 lb, keeping the user focused
  on the long-term trajectory rather than daily noise.

**Proactive vs reactive.** Proactive on a **weekly cadence** (the check-in comes to you), reactive
day-to-day (you log; it doesn't nag).

**Praise vs complaints.** Praised by data-driven users for adaptive targets grounded in *their own*
data, a trustworthy verified food database, and transparent expenditure explanations. The recurring
contrast with MyFitnessPal: MFP won't adjust when you plateau, leaving you to troubleshoot alone.
Cost (~$72/yr) and a smaller food DB than MFP are the trade-offs.

Sources: [help: interpreting expenditure changes](https://help.macrofactorapp.com/en/articles/26-how-should-i-interpret-changes-to-my-energy-expenditure),
[algorithm accuracy](https://macrofactorapp.com/algorithm-accuracy/),
[check-ins & coaching modules](https://help.macrofactorapp.com/en/articles/247-introduction-to-check-ins-and-coaching-modules),
[MF Coach](https://macrofactor.com/mf-coach/),
[MacroFactor vs MyFitnessPal](https://nutrola.app/en/blog/macrofactor-vs-myfitnesspal-which-is-better-2026).

### Whoop — recovery/readiness (highly relevant)
**What its AI does.** Two layers. (1) A proprietary ML **Recovery score** (0–100%) computed each
morning from HRV, resting heart rate, respiratory rate, sleep, skin temp, SpO2, plus a **Strain**
score quantifying daily cardiovascular load. (2) **Whoop Coach**, an OpenAI-powered conversational
layer (launched 2023, expanded since) that interprets those scores and tells you *what to do* —
"Daily Outlook" each morning (push vs rest, optimal training windows, using GPS/weather for outdoor
conditions), and "Day in Review" each evening. It compares weekly strain vs recovery capacity and
flags imbalance *before* you feel it.
- Reporting cadence: a **Weekly Performance Assessment** and a **Monthly Performance Assessment**
  (now emailed) tie logged **Journal** behaviors (nutrition, alcohol, bedtime, etc.) to their
  measured effect on recovery — i.e., "your behavior X changed recovery by Y."

**Proactive vs reactive.** Strongly proactive: a daily score is *pushed*, plus weekly/monthly
reports. Coach chat is reactive.

**Praise vs complaints.** Community consensus: the recovery % is trusted **as a multi-week trend**,
not as a single morning number ("trust the 7-day trend"). Biggest complaint: **"ghost workouts"** —
auto-detected activities that spike strain wrongly (a user's 20-min desk session logged as yoga
moved strain 4.1→17.3). Scores also feel "off" after alcohol/illness/time-zone changes. Crucial
meta-lesson from r/whoop: **Whoop only delivers value to users who act on the data** — the score is
worthless without a decision attached.

Sources: [Whoop Coach powered by OpenAI](https://www.whoop.com/us/en/thelocker/whoop-unveils-the-new-whoop-coach-powered-by-openai/),
[how Recovery works](https://www.whoop.com/us/en/thelocker/how-does-whoop-recovery-work-101/),
[Whoop recovery coach guide](https://www.athletedata.health/guides/whoop-recovery-coach),
[Monthly Performance Assessment](https://www.whoop.com/eu/en/thelocker/monthly-performance-assessment/),
[Reddit community review](https://www.aitooldiscovery.com/guides/whoop-reddit).

### Oura — recovery + AI Advisor (highly relevant)
**What its AI does.** A **Readiness score** rolling HRV, resting HR, body temp and sleep into one
number, plus **Oura Advisor** (rolled out to all members 2025), an LLM chat companion that reads
sleep/activity/readiness/stress data. Advisor explains *why* a score moved (examines sleep, activity,
stress as candidate causes), turns insight into action, and can proactively notify on changes (e.g.
deep-sleep drift). Notable UX choices:
- **Configurable persona & cadence**: choose a "conversational" vs "direct" tone and how often it
  checks in.
- **Persistent memory of context**: tell it you're recovering from knee surgery and it factors that
  into future guidance.

**Proactive vs reactive.** Mixed by design — proactive notifications *and* on-demand chat, with the
user controlling how proactive.

**Praise vs complaints.** In Oura's own Labs testing, 60% said Advisor helped them understand
metrics they hadn't grasped, 56% said it helped turn insight into action. Users valued the
**nonjudgmental** framing. Requires the $5.99/mo membership.

Sources: [Introducing Oura Advisor](https://ouraring.com/blog/oura-advisor/),
[Advisor rolling out to all members](https://www.businesswire.com/news/home/20250331565896/en/Oura-Advisor-an-AI-powered-Personal-Health-Companion-Now-Rolling-Out-to-All-Oura-Members),
[how to use Advisor](https://ouraring.com/blog/how-to-use-oura-advisor/).

### Hevy — lifting logger (relevant)
**What its "AI" does.** Primarily a **workout logger** with strong analytics: weekly volume per
muscle group vs recommended set ranges, auto-updating 1RM estimates with trend lines back to day
one. **Hevy Trainer** (newer) is an *algorithm-based* program generator (goal, experience,
equipment, frequency, time → a full program with rep ranges, rest, starting weights, tips) that also
offers ongoing guidance. Reviews are blunt that Hevy is *not* a real-time coach: **it does not
recommend weights, adjust workouts live, or do periodization/auto-regulation** — "it logs what you
do, it doesn't tell you what to do next."

**Lesson.** Even the leading lifting app leaves a wide-open gap on *closing the loop* between logged
lifting performance and the next prescription. This is exactly the seam our app sits on.

Sources: [Announcing Hevy Trainer](https://www.hevyapp.com/announcing-hevy-trainer/),
[Arvo vs Hevy comparison](https://arvo.guru/vs/hevy),
[Hevy features](https://www.hevyapp.com/features/).

### RP Hypertrophy — auto-regulated lifting (highly relevant)
**What its algorithm does.** The strongest *auto-regulation* model in consumer lifting. After each
session the user rates **pump, soreness, and workload (reps-in-reserve)**; the algorithm adjusts
volume and load using Dr. Mike Israetel's **MV/MEV/MAV/MRV volume landmarks** — rate a set easy and
it pushes reps/weight next week; report high soreness and it pulls volume back. It's periodised and
progression-aware, driven almost entirely by **structured subjective feedback**, not sensors.

**Lesson.** A tight, well-designed subjective feedback loop can drive genuinely adaptive coaching
without any wearable — the exact pattern our recomposition engine can borrow for the *lifting* leg.

Sources: [RP Hypertrophy app (RP Strength)](https://rpstrength.com/pages/hypertrophy-app),
[RP app deep dive](https://wellnd.com/is-the-renaissance-periodisation-app-worth-it-for-serious-lifters),
[RP expert review](https://dr-muscle.com/rp-hypertrophy-app-for-strength-training-expert-review/).

### Caliber — human-coach strength (relevant)
**Model.** Deliberately *human*, not AI: a certified coach designs and adjusts your program and
provides accountability. Reviews repeatedly say the human coach delivers "a level of accountability
and customization that AI simply cannot replicate," and praise supportive, knowledgeable staff.
Trade-off is price (up to ~$200/mo for 1:1). Beginners benefit most from the human feedback.

**Lesson.** The thing users pay a premium for is **accountability and trusted, personalized
adjustment** — not information. AI has to earn that trust, not assume it.

Sources: [BarBend Caliber review](https://barbend.com/caliber-fitness-app-review/),
[Garage Gym Reviews](https://www.garagegymreviews.com/caliber-app-review),
[Trustpilot](https://www.trustpilot.com/review/caliberstrong.com).

### Future — human coach + accountability (relevant)
**Model.** ~$199/mo pairs each user with a real elite coach: kickoff FaceTime, daily messaging,
personalized voice memos, video form review, and Apple-Watch visibility so the coach *sees* when you
start/finish and your heart rate. Reviews are consistent: **"Future works best for those who need
accountability more than information."** Daily check-ins from a real person who knows your history is
the product; the programming is secondary.

**Lesson.** The most defensible coaching value is *felt accountability from something that knows your
history and notices*. An on-device AI can approximate "noticing" cheaply — the gap most AI coaches
leave open.

Sources: [Forbes Health Future review](https://www.forbes.com/health/weight-loss/future-review/),
[Better Living 4-year review](https://onbetterliving.com/future-app/),
[Cora comparison](https://www.corahealth.app/compare/future).

### Noom — behavior-change / psychology (relevant)
**What its AI does.** Noom positions as *psychology-based* (CBT/ACT/DBT daily lessons, quizzes,
assignments) rather than a calorie tracker. **Welli** (launched June 2024) is an AI assistant giving
24/7 Q&A (eating while traveling, social-situation food choices, GLP-1 symptom navigation) to
*complement* — not replace — the weekly human coach check-in. Users like Welli for quick food info
and recipes.

**Complaints.** Reputation dented by **auto-renewal/billing** practices ($62M 2022 settlement);
billing remains a top complaint. Accountability rated middling (~6/10) — Welli answers questions but
the real accountability is the human coach.

**Lesson.** Behavior-change framing (the *why*, habit lessons) is differentiating, but an AI Q&A bot
alone doesn't move the accountability needle — it complements, it doesn't carry.

Sources: [Inside Welli (Noom Engineering)](https://medium.com/noom-engineering/inside-welli-nooms-new-ai-powered-health-assistant-cf5c050e5ae8),
[Noom AI products announcement](https://www.noom.com/in-the-news/noom-introduces-ai-enabled-products-to-enhance-on-demand-health-care-and-interactive-coaching-2/),
[ConsumerAffairs reviews](https://www.consumeraffairs.com/health/noom.html).

### MyFitnessPal — nutrition logger + new AI Coach (relevant)
**What its AI does.** Long the default logger (largest food DB, 50+ integrations). Added an **AI
Coach** (2025) in a dedicated "Coach" tab: open-ended nutrition Q&A plus "hyper-personalized
insights" analyzing how meals/macro-trends affect goals, using the user's own logs. Meal Scan,
barcode, and voice log sit behind Premium/Premium+.
**Weakness (per comparisons):** static targets that don't adapt when you plateau; user-submitted DB
entries carry higher error rates (~15–25% vs ~5–10% for verified DBs).

Sources: [Wareable on MFP AI Coach](https://www.wareable.com/health-and-wellbeing/myfitnesspal-nutrition-ai-coach-feature-announcement),
[9to5Mac](https://9to5mac.com/2026/06/16/myfitnesspal-adds-ai-powered-coach-for-personalized-nutrition-guidance/).

### Strong — lifting logger (lighter)
Positioned like Hevy: a clean logger with analytics and 1RM tracking; not a real-time adaptive
coach. Reinforces that the lifting-tracker category competes on logging UX, not on closing the
performance→prescription loop.
Source: [Hevy vs Strong](https://prpath.app/blog/strong-vs-hevy-2026.html).

### Fitbit (Google) — Gemini personal health coach (lighter)
Google's **Gemini-powered personal health coach** (public beta Oct 2025, iOS + more countries Feb
2026) acts as fitness trainer + sleep coach + wellness advisor: creates multi-week workout plans,
adjusts via continuous feedback, and answers open-ended health/nutrition questions (even "get more
from a doctor visit"). Conversational, chatbot-style. Requires Premium + qualifying device.
Sources: [Fitbit Gemini coach](https://fitbit.google/enterprise/blog/introducing-the-new-personal-health-coach-powered-by-gemini/),
[MobiHealthNews](https://www.mobihealthnews.com/news/google-debuts-gemini-powered-ai-health-coach-fitbit-and-pixel-watch-users).

### Garmin — Active Intelligence + nutrition (lighter)
**Connect+** ($6.99/mo) adds **"Active Intelligence"** — AI insights that surface behavior→outcome
relationships (e.g., "you eat late and sleep badly, try adjusting") — plus new in-Connect **nutrition
logging** (food DB, barcode, AI image recognition). Coaching plans themselves remain
human-authored/adaptive (Galloway, McMillan) rather than chatbot-driven.
Sources: [DC Rainmaker on Garmin nutrition](https://www.dcrainmaker.com/2026/01/garmin-connect-nutrition-logging-connect.html),
[Android Central](https://www.androidcentral.com/wearables/garmin-nutrition-tracking-relies-on-ai-to-log-food-and-recommend-dietary-changes).

### Apple Health / Fitness+ — "Project Mulberry" (lighter, cautionary)
Apple's AI health-coach ("Project Mulberry" / Health+) — trained on Apple-hired physicians, with
planned camera-based real-time form feedback and meal logging — was **scaled back / wound down in
early 2026**; features will ship piecemeal instead. A cautionary tale that an ambitious,
everything-at-once AI coach is hard to ship even for Apple.
Sources: [9to5Mac: Apple scales back](https://9to5mac.com/2026/02/05/apple-reportedly-scales-back-plans-for-ai-powered-health-coach/),
[Project Mulberry](https://9to5mac.com/2025/03/30/apple-health-doctor-project-mulberry/).

### Levels — CGM metabolic insights (lighter)
Turns raw CGM glucose into meal scores, stability scores and **AI pattern insights** ("you respond
better to higher-protein breakfasts," "try eating dinner earlier") and suggested experiments.
Reviews mixed — some call the AI coach "truly terrible" — but CGM-app studies show real
time-in-range gains that scale with engagement.
Sources: [Levels review](https://powermoves.blog/health/levels-health-review/),
[CGM app comparison](https://medium.com/@marisnaylor/continuous-glucose-monitor-cgm-app-comparison-levels-veri-nutrisense-and-january-ai-368c08e263b8).

### Freeletics — adaptive bodyweight AI (lighter)
An **AI Coach** generating and adapting workouts from **self-reported post-workout performance
ratings**, "~90% accurate from week one," refined on 56M+ users' data. Same core pattern as RP: a
subjective feedback loop keeps a static plan honest without sensors.
Source: [How the Freeletics Coach works](https://www.freeletics.com/en/blog/posts/AI-and-your-Coach/).

### Peloton / EvolveAI (lightest)
Peloton competes with Apple Fitness+ on *guided content* rather than data-driven adaptive coaching.
EvolveAI (AI-generated training/nutrition plans) surfaced little credible 2024–26 review signal;
treat as low-confidence.
Source: [Bloomberg via 9to5Mac (Fitness+ vs Peloton)](https://9to5mac.com/2026/02/05/apple-reportedly-scales-back-plans-for-ai-powered-health-coach/).

---

## Synthesis

### 1. What works (patterns that repeatedly earn trust/reliance)
- **Adapt targets from the user's OWN data, and show the math.** MacroFactor's reverse-calculated
  expenditure is the gold standard: it beats static formulas *and* explains *why* the number moved.
  Trust comes from the explanation, not the number.
- **A regular, low-friction check-in that comes to the user.** Weekly (MacroFactor) or daily +
  weekly/monthly (Whoop/Oura) cadence that *pushes* a small, curated assessment. Users don't have to
  remember to ask.
- **Structured subjective feedback loops.** RP and Freeletics prove you can auto-regulate lifting
  from a few post-session ratings (pump/soreness/RIR) — no wearable required.
- **Trend over point-in-time.** Whoop's "trust the 7-day trend," MacroFactor's moving-average
  weight. Smoothing prevents users from over-reacting to daily noise and prevents the coach from
  over-correcting.
- **Tie behavior to measured outcome.** Whoop's Journal→recovery and Garmin's "eat late → sleep
  badly" close the loop between *what you did* and *what happened*. That causal link is what feels
  like coaching.
- **Human accountability (Future/Caliber) is the benchmark AI is measured against.** The value users
  pay most for is *felt accountability from something that knows their history and notices changes* —
  not information.

### 2. What users love
- Targets/plans that **adjust automatically** when they plateau (MacroFactor vs the "MFP won't
  adjust, you troubleshoot alone" complaint).
- **Plain-language "why."** Oura Advisor's biggest measured win was helping users *understand*
  metrics they didn't grasp before, then act.
- **Nonjudgmental tone** and **user-controlled persona/cadence** (Oura's conversational-vs-direct,
  check-in frequency).
- **Curated, minimal interruption** — MacroFactor only surfacing a module "when it can meaningfully
  impact your goal."
- **Noticing and accountability** — Future's daily "your coach can see you started" is the emotional
  core of retention.

### 3. What users ignore or distrust
- **Generic AI chat with no personalization.** A bot that answers questions but doesn't act on *your*
  data adds little (the common "AI Q&A" ceiling; Levels' "truly terrible" coach; algorithmic
  aversion when advice feels generic).
- **Vanity/opaque scores with no attached decision.** A readiness/recovery number is ignored unless
  it says *what to do* — "Whoop only delivers value to users who act on the data."
- **Wrong data eroding trust instantly.** Whoop's "ghost workouts" and scores feeling "off" after
  alcohol/illness show that one visibly-wrong output poisons trust in the whole system. Correctness
  and graceful uncertainty matter more than coverage.
- **Notification spam / invasive questionnaires** — the thing MacroFactor explicitly designs against.
- **Dark-pattern billing** (Noom) destroys goodwill independent of coaching quality.

### 4. Gaps in the market (what almost nobody does well)
- **Cross-domain synthesis.** Every competitor is a silo: MacroFactor = nutrition, Whoop/Oura =
  recovery, Hevy/RP = lifting, Future = human accountability. **Almost nobody reasons across
  nutrition + lifting + steps + body metrics together** — e.g., "your lifting volume rose, steps
  fell, weight trend stalled → here's the single adjustment." That connective coaching is largely
  absent.
- **Closing the lifting performance→prescription loop.** Even Hevy admits it "logs what you do, it
  doesn't tell you what to do next." RP does it via subjective feedback but is lifting-only.
- **Explaining an *adjustment engine's* decisions in plain language.** MacroFactor explains
  expenditure; few explain the *whole* recomposition decision (why calories moved AND how lifting/
  steps/body-trend fed in).
- **Trustworthy on-device/private coaching.** Most AI coaches are cloud LLMs behind a subscription;
  private, on-device reasoning over the user's full history is rare.
- **Right-sized proactivity.** Apps swing between spammy and silent; MacroFactor's "only when it
  matters" curation is the exception, not the norm.

### 5. Opportunities for OUR app (principles, not feature-copies)
Our differentiator is that the data for all these silos already lives in **one** app plus a
**recomposition adjustment engine** — so we can do the cross-domain reasoning nobody else can.

1. **Make the recomposition engine the "coach," and make it explain itself.** MacroFactor earns
   trust by showing why expenditure moved; we can go further — one weekly verdict that *names the
   cross-domain cause*: "Weight trend flat despite the deficit; steps down 3k/day and lifting volume
   up — holding calories, nudging step target." Explanation is the trust mechanism.
2. **A curated weekly check-in as the spine, not a chat box.** Adopt the MacroFactor cadence/curation
   principle (surface a module only when it can change the trajectory) but feed it from *all four*
   domains. Chat is the on-demand supplement, not the main event.
3. **Structured subjective feedback for the lifting leg.** Borrow the RP/Freeletics pattern —
   lightweight per-session or weekly ratings (effort/soreness/energy) — so the engine can
   auto-regulate lifting *and* fold that into the nutrition/step adjustment. This is the seam Hevy
   leaves wide open.
4. **Every score/insight must carry a decision.** Never show a readiness/adherence/trend number
   without the "so do X." The universal complaint is orphaned metrics.
5. **Trend-first, uncertainty-honest.** Smooth body-weight and adherence like MacroFactor/Whoop;
   when confidence is low (too few logs, a noisy week), *say so and hold* rather than over-correct —
   which also protects trust the way Whoop's "trust the 7-day trend" framing does.
6. **Behavior→outcome callouts across domains.** Whoop ties Journal to recovery; we can tie steps,
   protein, sleep proxies and lifting to the body-recomp trend — the causal story that feels like
   real coaching.
7. **Accountability that "notices," privately.** We can't be a human coach, but an on-device coach
   that *notices* ("three planned meals unconfirmed," "logging streak slipping," "weigh-ins gone
   quiet") approximates Future's felt-accountability at near-zero marginal cost — and on-device is a
   genuine trust/privacy edge over the cloud-LLM incumbents.
8. **Ship narrow, not Mulberry-wide.** Apple couldn't ship an everything-at-once AI coach. Our
   opening is a *tightly-scoped* coach that does the one thing no one else can — reason across our
   four domains through the adjustment engine — extremely well.

---

## Sources
- MacroFactor: [expenditure interpretation](https://help.macrofactorapp.com/en/articles/26-how-should-i-interpret-changes-to-my-energy-expenditure) · [algorithm accuracy](https://macrofactorapp.com/algorithm-accuracy/) · [check-ins & coaching modules](https://help.macrofactorapp.com/en/articles/247-introduction-to-check-ins-and-coaching-modules) · [MF Coach](https://macrofactor.com/mf-coach/) · [vs MyFitnessPal](https://nutrola.app/en/blog/macrofactor-vs-myfitnesspal-which-is-better-2026)
- Whoop: [Coach + OpenAI](https://www.whoop.com/us/en/thelocker/whoop-unveils-the-new-whoop-coach-powered-by-openai/) · [Recovery 101](https://www.whoop.com/us/en/thelocker/how-does-whoop-recovery-work-101/) · [recovery coach guide](https://www.athletedata.health/guides/whoop-recovery-coach) · [Monthly Performance Assessment](https://www.whoop.com/eu/en/thelocker/monthly-performance-assessment/) · [Reddit review](https://www.aitooldiscovery.com/guides/whoop-reddit)
- Oura: [Advisor intro](https://ouraring.com/blog/oura-advisor/) · [rollout](https://www.businesswire.com/news/home/20250331565896/en/Oura-Advisor-an-AI-powered-Personal-Health-Companion-Now-Rolling-Out-to-All-Oura-Members) · [how to use](https://ouraring.com/blog/how-to-use-oura-advisor/)
- Hevy: [Hevy Trainer](https://www.hevyapp.com/announcing-hevy-trainer/) · [vs Arvo](https://arvo.guru/vs/hevy) · [features](https://www.hevyapp.com/features/)
- RP Hypertrophy: [official](https://rpstrength.com/pages/hypertrophy-app) · [deep dive](https://wellnd.com/is-the-renaissance-periodisation-app-worth-it-for-serious-lifters) · [expert review](https://dr-muscle.com/rp-hypertrophy-app-for-strength-training-expert-review/)
- Caliber: [BarBend](https://barbend.com/caliber-fitness-app-review/) · [Garage Gym Reviews](https://www.garagegymreviews.com/caliber-app-review) · [Trustpilot](https://www.trustpilot.com/review/caliberstrong.com)
- Future: [Forbes Health](https://www.forbes.com/health/weight-loss/future-review/) · [4-year review](https://onbetterliving.com/future-app/) · [Cora](https://www.corahealth.app/compare/future)
- Noom: [Inside Welli](https://medium.com/noom-engineering/inside-welli-nooms-new-ai-powered-health-assistant-cf5c050e5ae8) · [AI products](https://www.noom.com/in-the-news/noom-introduces-ai-enabled-products-to-enhance-on-demand-health-care-and-interactive-coaching-2/) · [ConsumerAffairs](https://www.consumeraffairs.com/health/noom.html)
- MyFitnessPal: [Wareable](https://www.wareable.com/health-and-wellbeing/myfitnesspal-nutrition-ai-coach-feature-announcement) · [9to5Mac](https://9to5mac.com/2026/06/16/myfitnesspal-adds-ai-powered-coach-for-personalized-nutrition-guidance/)
- Strong: [vs Hevy](https://prpath.app/blog/strong-vs-hevy-2026.html)
- Fitbit: [Gemini coach](https://fitbit.google/enterprise/blog/introducing-the-new-personal-health-coach-powered-by-gemini/) · [MobiHealthNews](https://www.mobihealthnews.com/news/google-debuts-gemini-powered-ai-health-coach-fitbit-and-pixel-watch-users)
- Garmin: [DC Rainmaker](https://www.dcrainmaker.com/2026/01/garmin-connect-nutrition-logging-connect.html) · [Android Central](https://www.androidcentral.com/wearables/garmin-nutrition-tracking-relies-on-ai-to-log-food-and-recommend-dietary-changes)
- Apple: [scales back](https://9to5mac.com/2026/02/05/apple-reportedly-scales-back-plans-for-ai-powered-health-coach/) · [Project Mulberry](https://9to5mac.com/2025/03/30/apple-health-doctor-project-mulberry/)
- Levels: [review](https://powermoves.blog/health/levels-health-review/) · [CGM comparison](https://medium.com/@marisnaylor/continuous-glucose-monitor-cgm-app-comparison-levels-veri-nutrisense-and-january-ai-368c08e263b8)
- Freeletics: [how the Coach works](https://www.freeletics.com/en/blog/posts/AI-and-your-Coach/)
- Cross-app / distrust: [Women's Fitness — should you trust an AI coach](https://womensfitness.co.uk/fitness/should-you-trust-an-ai-fitness-coach-the-truth-behind-smart-fitness-tech/) · [AI-guided trust/bias study](https://arxiv.org/pdf/2404.14521)
