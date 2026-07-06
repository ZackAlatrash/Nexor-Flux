# Coach meal suggestions — engine + chat tool (stage 3a)

**Date:** 2026-07-02
**Status:** Approved (brainstorm)
**Branch:** `redesign/ai-coaching`
**Part of:** feature #3 (meal suggestions). **Stage 3a** = the deterministic engine + `suggest_meals`
chat tool. **Stage 3b** (a separate spec/plan) = the proactive Food-screen card that replaces the
"Rest of Day" insight card, reusing this engine, gated on ~half-the-day-elapsed + a meaningful gap.

## Goal

Help the user decide what to eat to hit the rest of their day's macros: a deterministic engine
computes the remaining-macro **gap** and ranks concrete, portioned suggestions from the user's food
library; the coach presents them (plus web/knowledge recipe ideas), filtered by the user's memory,
and can offer to log the pick.

## Scope

**In scope (3a)**
- Pure `MealSuggester` engine: gap → focus macro (protein → carbs → calories) → ranked library
  suggestions with **amounts**, plus **combos** when protein and carbs are both short.
- `suggest_meals` coach tool (read, non-confirmed) exposing the engine.
- Prompt guidance: the coach supplements with web (`search_web`) / its own knowledge (labelled
  approximate), respects **coach memory** preferences, and offers to log via the existing `log_meal`.

**Out of scope**
- The Food-screen card (stage 3b).
- Any change to `log_meal` (reused as-is, confirmed) or `search_web` (reused as-is).
- Persisting/learning suggestions.

## Data sources (all already available)

- **Plan targets** (calories/protein/carbs/fat): via `PlanRepository`/plan preferences — the same
  source `get_today_summary` uses to derive per-day targets in `CoachToolExecutor`.
- **Eaten totals today:** `logRepository.getDay(today).totals` (`MacroTotals`: calories, proteinG,
  carbsG, fatG) — eaten (not planned) entries.
- **Food library:** `logRepository.getSavedFoods()` → `SavedFoodEntity` (name, per-serving
  calories/proteinG/carbsG/fatG, `householdServingName`, `householdServingGrams`).

## Pure engine — `MealSuggester` (`domain/food/`)

Pure Kotlin (no Android), fully unit-tested.

**Input:** `remaining` macros (targets − eaten, each clamped ≥0), `proteinMetRatio` (eaten/target),
and the library foods (as a small pure DTO: name, serving label, serving grams, and per-serving
calories/protein/carbs/fat).

**Focus selection:**
- `PROTEIN` when `proteinMetRatio < 0.85` AND `remaining.protein ≥ 5 g`.
- else `CARBS` when `remaining.carbs ≥ 10 g` AND `remaining.calories > 0`.
- else `CALORIES` when `remaining.calories ≥ 100`.
- else `NONE` (on target — the tool returns an "on target" result, no suggestions).

**Ranking (single suggestions):** among library foods, score by **focus-macro density** (focus grams
per calorie) so a pick fills the macro with the least calorie cost; drop foods that contribute ~zero
of the focus macro. Take the top ~5 distinct foods.

**Portions:** for each suggested food, compute an **amount** sized to fill about **half the remaining
focus macro**, then cap it so the suggestion's calories do not exceed `remaining.calories`. Express as
servings and/or grams using `householdServingGrams` (e.g. "≈180 g" or "≈2 servings"); round to a
tidy number. Report the resulting calories/protein/carbs/fat for that amount.

**Combos:** when focus is `PROTEIN` AND `remaining.carbs ≥ 10 g` (both short), produce 1–2 combos —
a top protein-dense food + a top carb-dense food, each portioned to ~half its gap, with combined
calories ≤ `remaining.calories`. Report the combo's summed macros.

**Output** (pure result object; the tool serializes it):
```
SuggestionResult(
  remaining: Macros, focus: Focus,
  suggestions: List<Suggestion>,   // name, amountLabel, calories, protein, carbs, fat
  combos: List<Combo>,             // items(name, amountLabel), calories, protein, carbs, fat
  libraryThin: Boolean,            // true when the library yields no usable suggestion
)
```
Deterministic; every number computed here, never by the LLM.

## Tool — `suggest_meals` (`CoachToolExecutor`, read, NOT in `COACH_WRITE_TOOLS`)

`suggest_meals(date?)`:
1. `targets` = plan targets for the date (default today); `eaten` = `logRepository.getDay(date).totals`.
2. `remaining` = targets − eaten (clamped ≥0); `proteinMetRatio` = eaten.protein / targets.protein.
3. Build library DTOs from `getSavedFoods()`; run `MealSuggester`.
4. Return JSON: `{"remaining":{...},"focus":"protein|carbs|calories|none","suggestions":[{"name","amount","calories","protein_g","carbs_g","fat_g","exact":true}...],"combos":[{"items":[{"name","amount"}...],"calories","protein_g","carbs_g","fat_g"}...],"library_thin":<bool>}`.
   - `"exact":true` marks library items (exact macros).
   - `focus="none"` → `{"remaining":...,"focus":"none","message":"on target"}`.
Add to `CLOUD_COACH_TOOL_SCHEMAS` (a new `SUGGESTION_TOOL_SCHEMAS` list appended). `toolStatusText`
→ "Planning your meals…".

## Coach behaviour (prompt guidance in `COACH_PROMPT_GUIDELINES`)

- "When the user asks what to eat, or how to hit their remaining macros/protein, call `suggest_meals`.
  Recommend 2–3 concrete options **with amounts** from the result.
- Library items (`exact:true`) have exact macros — state them confidently. For other ideas (from
  `search_web` or your own nutrition knowledge), say the macros are approximate and cite the web
  source if you searched.
- If `library_thin` is true or the user wants variety, use `search_web` (or your knowledge) for a
  recipe that fits the `remaining`/`focus`, respecting the user's memory (diet, allergies, dislikes).
- After the user picks one, offer to log it with `log_meal`."

Web search is the existing `search_web` (needs the web-search key configured; otherwise the coach
falls back to its own knowledge). No new web plumbing.

## Architecture / boundary

- `MealSuggester` is pure in `domain/food/` (no Android/LLM). The tool lives in `ai/CoachToolExecutor`;
  schema in `ai/CoachTools.kt`. Nothing imports `ai/local`. `AiCoachBoundaryTest` stays green.
- Deterministic thesis preserved: the engine computes the gap, ranking, portions, and combo macros;
  the LLM only phrases and (for non-library ideas) supplies approximate numbers it labels as such.

## Testing

- `MealSuggester` (pure): focus = protein when protein <85% met; focus flips to carbs once protein
  ~met; ranking prefers high focus-density foods; portion fills ~half the focus gap and never exceeds
  remaining calories; combo appears only when protein+carbs both short and stays within calories;
  empty/low library → `libraryThin`; `focus = none` when on target.
- `suggest_meals` tool: computes `remaining` from targets − eaten and returns focus + suggestions;
  library-thin path returns the gap with `library_thin:true`; past-date via `date`.
- Not in `COACH_WRITE_TOOLS` (runs without a confirm dialog).

## Rollout

Subagent-driven TDD in the MAIN checkout on `redesign/ai-coaching` (worktree isolation branches from
the wrong base here — main tree + base sanity check). Review pass, then on-device: ask the coach
"what should I eat to hit my protein?" and confirm it suggests portioned options respecting memory,
and offers to log. Stage 3b (the card) follows in its own spec/plan.
