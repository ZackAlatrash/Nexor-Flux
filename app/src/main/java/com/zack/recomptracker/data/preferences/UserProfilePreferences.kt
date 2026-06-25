package com.zack.recomptracker.data.preferences

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period

@Serializable
data class UserProfilePreferences(
    val name: String? = null,
    val profilePhotoUri: String? = null,
    val heightCm: Int? = null,
    val birthDate: String? = null,          // ISO yyyy-MM-dd
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,
    val goal: FitnessGoal? = null,
    val dailyStepGoal: Int? = null,
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

/** Age in whole years derived from birthDate, relative to [today]. Null if unset/invalid. */
fun UserProfilePreferences.ageYears(today: LocalDate = LocalDate.now()): Int? {
    val dob = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years
}
