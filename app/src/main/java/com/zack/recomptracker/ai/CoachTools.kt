package com.zack.recomptracker.ai

/**
 * Backend-neutral coach tool schemas, shared by the cloud coach (and, for now, the legacy on-device
 * coach). Extracted out of `GemmaCoachCoordinator` in the Phase-0 isolation refactor so the cloud
 * path has no compile-time dependency on any local/Gemma file — see
 * `docs/ai-redesign/08-technical-architecture.md` §5. The new AI coach system depends on these
 * constants from here; when the local stack is deleted (Phase 6) this file is unaffected.
 *
 * Each entry is a raw JSON object `{"name":..,"description":..,"parameters":..}`. The cloud path
 * sends them as OpenAI `tools` entries; the legacy Gemma path wraps them in its own SchemaTool.
 */
val COACH_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"get_today_summary","description":"Get a specific day's food log, macro totals, and daily metrics. Omit 'date' for today.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"ISO date YYYY-MM-DD. Omit for today."}},"required":[]}}""",
    """{"name":"get_weekly_trends","description":"Get last 7 days of daily macro totals (calories, protein, carbs, fat) and adherence percent. Use this for weekly trends or any multi-day macro question.","parameters":{"type":"object","properties":{},"required":[]}}""",
    """{"name":"search_food_library","description":"Search your saved food library by name. If the user specified a weight in grams, pass it as 'grams' and the tool returns macros already scaled to that weight — use those directly in log_meal.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Food name only — no quantities or weights"},"grams":{"type":"number","description":"Optional: weight in grams requested by the user. If provided, returned macros are pre-scaled to this weight."}},"required":["query"]}}""",
    """{"name":"log_meal","description":"Add a meal to the food log. Defaults to today. Pass a future 'date' to PLAN the meal for that day instead of logging it as eaten. The tool looks up your food library automatically and uses the correct macros. Pass grams if the user specified a weight. If the food is NOT in the library, you MUST also provide calories, protein_g, carbs_g, and fat_g.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name"},"grams":{"type":"number","description":"Optional: weight in grams. Macros are scaled automatically if food is in library."},"meal_type":{"type":"string","description":"One of: Breakfast, Lunch, Dinner, Snack. Default: Snack"},"date":{"type":"string","description":"Optional ISO date YYYY-MM-DD. A future date plans the meal instead of logging it eaten. Omit for today."},"calories":{"type":"integer","description":"Required only if food is NOT in your library. Omit for library foods."},"protein_g":{"type":"number","description":"Required only if food is NOT in your library."},"carbs_g":{"type":"number","description":"Required only if food is NOT in your library."},"fat_g":{"type":"number","description":"Required only if food is NOT in your library."}},"required":["name"]}}""",
    """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
    """{"name":"update_calorie_target","description":"Update the daily calorie target. Value must be between 500 and 6000.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer","description":"New daily calorie target in kcal (500–6000)"}},"required":["target_calories"]}}""",
)

/** Tool names that mutate user data and therefore require explicit confirmation. */
val COACH_WRITE_TOOLS: Set<String> = setOf("log_meal", "log_metric", "update_calorie_target")

/**
 * Web-search tool schema. CLOUD COACH ONLY — never added to the local Gemma tool list (the 2B
 * model is poor at tool calls and the local backend is meant to work offline). Read-only, so it
 * is not in [COACH_WRITE_TOOLS] and runs without user confirmation.
 */
val SEARCH_WEB_TOOL_SCHEMA: String =
    """{"name":"search_web","description":"Search the public web for a fact you don't already have — e.g. calories or macros for a restaurant or packaged food that isn't in the user's library, or a general nutrition, supplement, or training question. Returns a short answer plus source URLs. Always cite the source URL in your reply.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"A concise search query, e.g. \"McDonald's Big Mac calories\""}},"required":["query"]}}"""

/** The cloud coach's full tool list: the shared tools plus web search. */
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA
