package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.ageYears
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/** Outcome of attempting to generate a plan from the current profile + weight. */
sealed interface PlanGenerationOutcome {
    data class Ready(val plan: GeneratedPlan) : PlanGenerationOutcome
    data class MissingProfileFields(val fields: List<String>) : PlanGenerationOutcome
    data object NeedsWeight : PlanGenerationOutcome
}

/**
 * Validates the profile, supplies weight, and delegates the math to PlanCalculator.
 * Pure — no Android, no IO. Missing required fields take priority over missing weight.
 */
class PlanGenerator(
    private val calculator: PlanCalculator = PlanCalculator(),
) {
    fun generate(
        profile: UserProfilePreferences,
        weightKg: Double?,
        today: LocalDate = LocalDate.now(),
    ): PlanGenerationOutcome {
        val age = profile.ageYears(today.toKotlinLocalDate())
        val missing = buildList {
            if (profile.heightCm == null) add("Height")
            if (age == null) add("Age")
            if (profile.biologicalSex == null) add("Sex")
            if (profile.activityLevel == null) add("Activity level")
            if (profile.goal == null) add("Goal")
        }
        if (missing.isNotEmpty()) return PlanGenerationOutcome.MissingProfileFields(missing)
        if (weightKg == null || weightKg <= 0.0) return PlanGenerationOutcome.NeedsWeight

        val plan = calculator.generate(
            PlanCalculatorInput(
                heightCm = profile.heightCm!!,
                ageYears = age!!,
                sex = profile.biologicalSex!!,
                activityLevel = profile.activityLevel!!,
                goal = profile.goal!!,
                weightKg = weightKg,
            ),
        )
        return PlanGenerationOutcome.Ready(plan)
    }
}
