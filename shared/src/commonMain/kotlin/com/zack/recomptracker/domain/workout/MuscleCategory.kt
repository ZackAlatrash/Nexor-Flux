package com.zack.recomptracker.domain.workout

/**
 * The six broad, gym-style muscle groups the Stats screen browses by.
 * [displayName] is the user-facing label; the enum order sets list order on screen.
 */
enum class MuscleCategory(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs"),
    CORE("Core"),
}

/**
 * Buckets a free-exercise-db `primaryMuscles` list into one broad [MuscleCategory] using the
 * first entry (case/space-insensitive). Returns null when empty or unmapped (e.g. neck-only) —
 * such exercises are omitted from the Stats list.
 */
fun muscleCategoryFor(primaryMuscles: List<String>): MuscleCategory? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> MuscleCategory.CHEST
        "shoulders" -> MuscleCategory.SHOULDERS
        "lats", "middle back", "lower back", "traps" -> MuscleCategory.BACK
        "biceps", "triceps", "forearms" -> MuscleCategory.ARMS
        "quadriceps", "hamstrings", "calves", "glutes", "abductors", "adductors" -> MuscleCategory.LEGS
        "abdominals" -> MuscleCategory.CORE
        else -> null
    }
}
