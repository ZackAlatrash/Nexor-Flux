package com.zack.recomptracker.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class UserProfilePreferences(
    val heightCm: Int? = null,
    val ageYears: Int? = null,
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,
    val goal: FitnessGoal? = null,
    val trainingExperience: TrainingExperience? = null,
    val plannedTrainingDays: Int? = null,
)

@Serializable
enum class BiologicalSex { MALE, FEMALE }

@Serializable
enum class TrainingExperience {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

@Serializable
enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
}

@Serializable
enum class FitnessGoal {
    AGGRESSIVE_CUT,
    MODERATE_CUT,
    MINI_CUT,
    RECOMP,
    LEAN_BULK,
    MODERATE_BULK,
    AGGRESSIVE_BULK,
}

fun FitnessGoal.displayName(): String = when (this) {
    FitnessGoal.AGGRESSIVE_CUT -> "Aggressive Cut"
    FitnessGoal.MODERATE_CUT -> "Moderate Cut"
    FitnessGoal.MINI_CUT -> "Mini Cut"
    FitnessGoal.RECOMP -> "Recomp"
    FitnessGoal.LEAN_BULK -> "Lean Bulk"
    FitnessGoal.MODERATE_BULK -> "Moderate Bulk"
    FitnessGoal.AGGRESSIVE_BULK -> "Aggressive Bulk"
}

fun BiologicalSex.displayName(): String = when (this) {
    BiologicalSex.MALE -> "Male"
    BiologicalSex.FEMALE -> "Female"
}

fun ActivityLevel.displayName(): String = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly Active"
    ActivityLevel.MODERATELY_ACTIVE -> "Moderately Active"
    ActivityLevel.VERY_ACTIVE -> "Very Active"
}

fun TrainingExperience.displayName(): String = when (this) {
    TrainingExperience.BEGINNER -> "Beginner"
    TrainingExperience.INTERMEDIATE -> "Intermediate"
    TrainingExperience.ADVANCED -> "Advanced"
}
