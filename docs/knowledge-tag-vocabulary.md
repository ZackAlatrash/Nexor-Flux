# Knowledge Base — Controlled Tag Vocabulary

The single source of truth for chunk tags. Every corpus chunk must tag **only** from this list so
retrieval is consistent across the whole corpus (the #1 failure mode is two chunks tagging the same
concept differently — e.g. one uses `cut`, another `fat-loss` — so a user query matches only one).

## How retrieval uses tags (read before tagging)

- The retriever (`KeywordKnowledgeRetriever`) matches query words against tags (weight **2.0**),
  titles (3.0), and body (1.0). Tags are how a chunk gets found by words that aren't in its body.
- **Light stemming is applied** to both queries and tags: plural `-s`, `-ing`, `-ed` are normalized
  (`proteins`→`protein`, `training`→`train`, `meals`→`meal`). So you do **NOT** need to list plurals
  or verb forms of the same root — `protein` covers `proteins`, `train` covers `training/trained`.
- Stemming does **NOT** unify irregular pairs or double-consonant gerunds. So you **DO** list both:
  `lose` AND `loss`; `cut` AND `cutting`; `sore` AND `soreness`. These are in the vocab below.
- Tag with the words a real user would **type in a question**, not the formal term only.

## How to tag a chunk

Use the per-section `tags:` line (first line under the `## ` heading):

```markdown
## Eat more protein while cutting
tags: protein, cut, cutting, deficit, lose, lean, muscle
When you are in a calorie deficit...
```

- Pick **4–8 tags** per chunk: the concept's core tags + the user-language synonyms that fit.
- Only use tags from this file. If a genuinely needed term is missing, add it here in your batch
  and note it in your report so it stays controlled.

---

## NUTRITION & MACROS

- **Protein:** protein, macros, leucine
- **Fat loss / cutting:** fat, loss, lose, cut, cutting, deficit, lean, shred, shredded, ripped, tone, calories, calorie
- **Muscle gain / bulking:** muscle, gain, gains, build, bulk, bulking, surplus, mass, size, grow, bigger, growth
- **Maintenance:** maintenance, maintain, recomp, recomposition
- **Calories / energy balance:** calories, calorie, kcal, energy, intake, "energy-balance", tdee
- **Dietary fat:** fat, fats, "dietary-fat", hormones
- **Carbohydrate:** carbs, carb, carbohydrate, "low-carb", keto, fuel
- **Fiber / satiety / hunger:** fiber, fibre, satiety, full, fullness, hunger, appetite, craving
- **Meal frequency / timing:** meal, meals, frequency, timing, breakfast, snack, "post-workout"
- **Diet adherence / breaks / plateau:** diet, adherence, refeed, "diet-break", plateau, "metabolic-adaptation"
- **Weight / rate of change:** weight, scale, rate, fast, slow, sustainable
- **Protein sources / quality:** "high-protein", sources, whey, "protein-quality", diaas, pdcaas

## TRAINING & HYPERTROPHY

- **Volume:** volume, sets, "sets-per-week", "weekly-sets"
- **Frequency:** frequency, sessions, split, "how-often"
- **Load / intensity:** load, intensity, heavy, light, "1rm", reps, "rep-range"
- **Failure / effort:** failure, rir, "reps-in-reserve", effort, hard
- **Progression:** "progressive-overload", progression, overload, progress
- **Exercise selection / technique:** exercise, selection, movement, tempo, "range-of-motion", rom, "muscle-length", form, technique
- **Periodization / programming:** periodization, program, programming, mesocycle, deload
- **Minimum effective dose:** minimum, "minimum-dose", "time-efficient", maintenance
- **Concurrent training / cardio:** cardio, concurrent, running, interference, conditioning
- **Rest intervals:** rest, "rest-interval", "rest-time", "how-long", time
- **Outcomes:** hypertrophy, muscle, strength, growth, gains, size

## RECOVERY & SLEEP

- **Sleep:** sleep, rest, bed, bedtime, hours, "sleep-hygiene", "sleep-quality", nap, napping
- **Sleep & appetite / fat loss:** appetite, hunger, "weight-loss", cravings
- **Soreness / DOMS:** sore, soreness, doms, ache, stiff, "muscle-soreness"
- **Fatigue / low energy:** fatigue, tired, drained, exhausted, "low-energy", energy
- **Overtraining / overreaching:** overtraining, overreaching, burnout, "under-recovery"
- **Deload / fatigue management:** deload, recovery, recover, taper, rest, readiness
- **Monitoring / stress:** readiness, hrv, monitoring, stress
- **Recovery modalities:** "cold-water", "ice-bath", "active-recovery", massage, "foam-rolling", modalities
- **Injury / performance:** injury, performance, power, output

## SUPPLEMENTS & FOODS

- **Creatine:** creatine, "creatine-monohydrate", mono, loading
- **Caffeine / pre-workout:** caffeine, coffee, "pre-workout", preworkout, stimulant, "energy-drink"
- **Beta-alanine:** "beta-alanine", carnosine, tingles, paresthesia, buffering, endurance
- **Protein supplements:** "protein-powder", whey, casein, shake, supplement
- **HMB:** hmb
- **Citrulline:** citrulline, "citrulline-malate", pump
- **Sodium bicarbonate:** "sodium-bicarbonate", "baking-soda", bicarb, buffering
- **Supplement evidence / general:** supplement, supplements, evidence, effective, "worth-it", safe, dose
- **Foods / food data:** food, foods, "high-protein", sources, eggs, chicken, "food-database", usda
- **Protein quality:** "protein-quality", diaas, pdcaas, "amino-acids", leucine, "plant-based", vegan, vegetarian

---

## Cross-cutting (any domain may use)

goal, beginner, advanced, women, female, safety, "how-much", "how-many", dose, timing

## Body parts (for "bigger arms" style queries)

arms, legs, chest, back, shoulders, abs, glutes, biceps
