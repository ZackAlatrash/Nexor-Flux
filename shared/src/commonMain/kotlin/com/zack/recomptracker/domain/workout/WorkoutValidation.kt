package com.zack.recomptracker.domain.workout

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasons: List<String>) : ValidationResult
}

object WorkoutValidation {

    fun validateTemplate(name: String, exerciseCount: Int, plannedSets: List<Int>): ValidationResult {
        val reasons = buildList {
            if (name.isBlank()) add("Workout name must not be blank.")
            if (exerciseCount < 1) add("A workout must contain at least one exercise.")
            if (plannedSets.any { it < 1 }) add("Each exercise must have at least one planned set.")
        }
        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    fun validateSet(reps: Int, weightKg: Double?, rir: Int?): ValidationResult {
        val reasons = buildList {
            if (reps < 0) add("Reps must not be negative.")
            if (weightKg != null && weightKg < 0.0) add("Weight must not be negative.")
            if (rir != null && rir !in 0..10) add("RIR must be between 0 and 10.")
        }
        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }
}
