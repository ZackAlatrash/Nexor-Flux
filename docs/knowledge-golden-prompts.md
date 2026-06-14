# Golden Prompts — Knowledge Base Verification

A reference set for judging the cloud coach's quality. Each prompt lists **what should happen** so
you can tell good from bad.

## How to test in the app

1. AI settings → set cloud **base URL + model + API key** (e.g. OpenRouter, `openai/gpt-oss-20b:free`).
2. Toggle backend to **Cloud**.
3. Open the **coach chat** (knowledge injection runs on the cloud coach chat only — not the local
   backend, not insight cards).
4. Ask each prompt. Judge against the "expected" column.

**What "good" looks like overall:** the answer is *specific* (gives the actual number/range), *sourced*
(names the evidence, e.g. "ISSN" — the grounding rule asks it to cite), *on-topic*, and it **never
invents food macros** (it calls the food-library tool instead). Generic, number-free, or hallucinated
answers = fail.

> Tip: to test retrieval *without* burning API calls, use the retrieval harness instead
> (`docs/knowledge-tag-vocabulary.md` explains tags; run the probe — see below).

---

## 1. Direct hits (sanity check)

| Prompt | Expected |
|---|---|
| How many sets per week should I do to build muscle? | ~10–20 sets/week per muscle; sweet spot 12–20 (Baz-Valle). |
| How much creatine should I take? | 3–5 g/day maintenance; optional 20 g/day loading for 5–7 days (ISSN). |
| How much protein do I need when cutting? | Higher in a deficit: ~2.3–3.1 g/kg to protect muscle (ISSN). |

## 2. Colloquial / synonyms (tests stemming + tag vocab)

| Prompt | Expected |
|---|---|
| How do I get shredded? | Fat-loss: moderate deficit, high protein, rate ~0.5–1%/wk. |
| Is coffee worth it before lifting? | Caffeine 3–6 mg/kg, ~60 min pre; strong evidence. |
| I feel smashed and achy after every session | DOMS info: peaks 24–72 h; what helps vs doesn't. |
| Am I bulking too fast? | Slow, controlled surplus; flags fast weight gain = excess fat. |

## 3. Cross-domain (multi-topic)

| Prompt | Expected |
|---|---|
| I'm cutting but always exhausted — what's wrong? | Pulls nutrition (deficit) + recovery (sleep/fatigue) knowledge. |
| Best way to keep muscle while losing fat? | High protein (2.3–3.1 g/kg) + resistance training + modest deficit. |
| Does poor sleep ruin my fat loss? | Yes — sleep restriction shifts loss toward muscle; raises appetite. |

## 4. Numeric grounding / anti-hallucination (critical)

| Prompt | Expected |
|---|---|
| How many calories in 200g of chicken breast? | **Calls the food-library tool** — does NOT invent a number. |
| What's my protein target if I weigh 80 kg? | Applies 1.6–2.2 g/kg → ~128–176 g; cites the basis. |
| How many calories should I eat to lose weight? | Asks for / uses the user's data; deficit ~300–500 kcal framing, not a made-up absolute. |

## 5. Myth-busting / adversarial (evidence-based correction)

| Prompt | Expected |
|---|---|
| Do I need to eat every 3 hours to keep my metabolism up? | No — total daily intake dominates; meal frequency is preference. |
| Is fasted cardio better for fat loss? | No meaningful advantage when calories are equated. |
| Do I have to do keto to lose fat? | No — no fat-loss edge when calories + protein are equated (ISSN). |
| Is creatine a steroid or bad for my kidneys? | Not a steroid; safe in healthy people up to studied doses. |

## 6. Vague / open

| Prompt | Expected |
|---|---|
| Help me lose weight. | Surfaces deficit + high protein + fiber/satiety basics. |
| How do I get bigger arms? | Training volume + progressive overload (not a magic exercise). |

## 7. Off-topic / guardrail (should stay on-topic; inject nothing)

| Prompt | Expected |
|---|---|
| What's the capital of France? | Politely declines / redirects; no knowledge injected. |
| Write me a poem. / What stock should I buy? | Stays on nutrition/training/recovery scope. |

---

## Retrieval harness (offline, no API)

To see exactly which chunks are retrieved for any prompt list:

1. Edit `app/src/test/resources/retrieval-probe-questions.txt` (one prompt per line; `#` = comment).
2. Run: `./gradlew :app:testDebugUnitTest --tests "*RetrievalProbe"`
3. Read: `app/build/retrieval-probe.txt` — shows the top-3 chunks + scores + sources per prompt.

Good retrieval = the right chunk is in the top-3 for prompts 1–6, and **no hits** for the off-topic
prompts in section 7.
