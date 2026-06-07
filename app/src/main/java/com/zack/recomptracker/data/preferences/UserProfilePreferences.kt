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
)

@Serializable
enum class BiologicalSex { MALE, FEMALE }

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
