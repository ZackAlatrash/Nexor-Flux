package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.preferences.displayName
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
) : CoachReadTools {

    override suspend fun execute(name: String, args: Map<String, String>): String =
        withContext(Dispatchers.IO) { toolExecutor.execute(name, args) }

    override suspend fun systemPromptSnapshot(): String {
        val prefs = planRepository.preferences.first()
        val profile = userProfileStore.preferences.first()
        val today = dateProvider.today()
        val todaySummary = withContext(Dispatchers.IO) { toolExecutor.execute("get_today_summary", emptyMap()) }
        val base = buildPrompt(prefs, profile, today, todaySummary)
        val handoff = handoffStore.consume()
        return if (handoff.isNullOrBlank()) base else base + "\n\n" + handoff
    }

    private fun buildPrompt(
        prefs: PlanPreferences,
        profile: UserProfilePreferences,
        today: java.time.LocalDate,
        todaySummary: String,
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
            profile.ageYears?.let { add("Age: $it") }
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
        appendLine()
        appendLine("Guidelines:")
        appendLine("- For today's data, you may answer from the snapshot above. Call get_today_summary(date=…) for any other date, and get_weekly_trends() for multi-day or adherence questions.")
        appendLine("- Use markdown when it improves clarity (short lists, bold key numbers). Answer only from logged data and tool results; never invent numbers.")
        appendLine("- To log food, call log_meal(...); the tool checks the food library automatically. To record a metric, call log_metric(...). These actions are confirmed by the user before they run.")
        append("- Stay on topic: nutrition, body composition, training, and recovery.")
    }
}
