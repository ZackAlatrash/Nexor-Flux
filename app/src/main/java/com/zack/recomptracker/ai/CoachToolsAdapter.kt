package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachJourney
import com.zack.recomptracker.data.coach.NoopCoachJourney
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.preferences.ageYears
import com.zack.recomptracker.data.preferences.displayName
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Static coach guidelines appended to every system prompt. Extracted as a constant so the
 * grounding rules are unit-testable without constructing the full adapter. The food-lookup rule
 * is the fix for the cloud model inventing macros; the REFERENCE KNOWLEDGE clause lets it use the
 * injected knowledge block as a legitimate source.
 */
internal const val COACH_PROMPT_GUIDELINES: String =
    "- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.\n" +
        "- Call get_training_summary() for lifting, strength, e1RM, volume, frequency, or recovery-load questions; call get_body_trends() for weight, waist, or recomposition questions (\"am I recomping?\").\n" +
        "- For any food's calories or macros you do not already have, you MUST call search_food_library first. If the food is not in the library, call search_web to look it up online — never estimate numbers from memory.\n" +
        "- If a fact you need is not in the snapshot, the food library, or the REFERENCE KNOWLEDGE — for example a restaurant or packaged-food nutrition figure, or a general nutrition, supplement, or training question — call search_web(query=…).\n" +
        "- When you use a web result, cite the source URL in your answer. Answer only from logged data, tool results, REFERENCE KNOWLEDGE, and web results you cite; never invent numbers.\n" +
        "- Use markdown when it improves clarity (short lists, bold key numbers).\n" +
        "- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.\n" +
        "- To fix a logged meal, use delete_meal or edit_meal, identifying it by name; pass a past 'date' if it wasn't today. If the tool returns needs_disambiguation, ask the user which one.\n" +
        "- To build a routine: for EACH exercise, call search_exercises and USE the closest existing match's exact name; then build the ENTIRE routine in ONE create_routine call so the user confirms only once. 'sets' is the number of sets; reps/weight are optional.\n" +
        "- Reuse existing library exercises whenever search returns a match, even if the wording differs (e.g. 'incline dumbbell press' matches 'Dumbbell Incline Bench Press'). Only call create_exercise when search_exercises returns NO matches at all.\n" +
        "- To change a routine: call get_routines first, then edit_routine with ONLY the changes (add/remove/retarget/new_name). Existing exercises are preserved.\n" +
        "- When you must create a new exercise (search found nothing), ask for the muscle group(s) if the user didn't say.\n" +
        "- You have a memory of facts about the user (diet, injuries, preferences). Use remember when they ask you to remember something and forget when they ask you to drop it. Respect these facts in your advice, and don't store the same fact twice.\n" +
        "- Stay on topic: nutrition, body composition, training, and recovery."

/**
 * Production [CoachReadTools]: dispatches tool calls to [CoachToolExecutor] and builds the coach
 * system prompt (plan + profile + today's snapshot + rules). Mirrors
 * `GemmaCoachCoordinator.buildSystemPrompt`, minus the 2B-specific anti-confusion wording — a
 * capable cloud model needs only clear instructions.
 */
class CoachToolsAdapter(
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
    private val handoffStore: CoachHandoffStore,
    private val journey: CoachJourney = NoopCoachJourney,
    private val coachMemory: com.zack.recomptracker.data.coach.CoachMemory = com.zack.recomptracker.data.coach.NoopCoachMemory,
) : CoachReadTools {

    override suspend fun execute(name: String, args: Map<String, String>): String =
        withContext(Dispatchers.IO) { toolExecutor.execute(name, args) }

    override suspend fun systemPromptSnapshot(): String {
        val prefs = planRepository.preferences.first()
        val profile = userProfileStore.preferences.first()
        val today = dateProvider.today()
        val todaySummary = withContext(Dispatchers.IO) { toolExecutor.execute("get_today_summary", emptyMap()) }
        val journeyNarrative = journey.journeyNarrative()
        val memoryBlock = coachMemory.all()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- ${it.text}" }
            .orEmpty()
        val base = buildPrompt(prefs, profile, today, todaySummary, journeyNarrative, memoryBlock)
        val handoff = handoffStore.consume()
        return if (handoff.isNullOrBlank()) base else base + "\n\n" + handoff
    }

    private fun buildPrompt(
        prefs: PlanPreferences,
        profile: UserProfilePreferences,
        today: java.time.LocalDate,
        todaySummary: String,
        journeyNarrative: String,
        memoryBlock: String,
    ): String = buildString {
        val yesterday = today.minusDays(1)
        val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        appendLine("You are a knowledgeable, supportive nutrition and body-recomposition coach inside a tracking app.")
        appendLine("Today: $today ($dayName) | Yesterday: $yesterday")
        appendLine()
        appendLine("Plan: ${prefs.targetCalories} kcal | P ${prefs.targetProteinG}g | C ${prefs.targetCarbsG}g | F ${prefs.targetFatG}g")
        val profileParts = buildList {
            profile.goal?.let { add("Goal: ${it.displayName()}") }
            profile.biologicalSex?.let { add("Sex: ${it.displayName()}") }
            profile.ageYears()?.let { add("Age: $it") }
            profile.heightCm?.let { add("Height: $it cm") }
            profile.activityLevel?.let { add("Activity: ${it.displayName()}") }
            profile.weeklyGymSessions?.let { add("Gym sessions/week: $it") }
        }
        if (profileParts.isNotEmpty()) {
            appendLine()
            appendLine("=== USER PROFILE ===")
            appendLine(profileParts.joinToString(" | "))
            appendLine("=== END PROFILE ===")
        }
        appendLine()
        appendLine("=== TODAY'S DATA SNAPSHOT (fetched at conversation start) ===")
        appendLine(todaySummary)
        appendLine("=== END SNAPSHOT ===")
        if (journeyNarrative.isNotBlank()) {
            appendLine()
            appendLine("=== YOUR JOURNEY SO FAR ===")
            appendLine(journeyNarrative)
            appendLine("=== END JOURNEY ===")
        }
        if (memoryBlock.isNotBlank()) {
            appendLine()
            appendLine("=== WHAT I KNOW ABOUT YOU ===")
            appendLine(memoryBlock)
            appendLine("=== END ===")
        }
        appendLine()
        appendLine("Guidelines:")
        append(COACH_PROMPT_GUIDELINES)
    }
}
