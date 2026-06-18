package com.zack.recomptracker.ui.train.component

/** Which body silhouette a muscle is highlighted on. */
enum class MuscleView { FRONT, BACK }

/** A resolved highlight: which view, and which body-art slugs to fill with the accent. */
data class MuscleTarget(val view: MuscleView, val slugs: Set<String>)

/**
 * Maps a free-exercise-db `primaryMuscles` list to a highlight target.
 * Uses the first entry (case/space-insensitive). Returns null if empty or unmapped
 * — callers render the generic dumbbell fallback in that case.
 */
fun muscleTargetFor(primaryMuscles: List<String>): MuscleTarget? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> MuscleTarget(MuscleView.FRONT, setOf("chest"))
        "shoulders" -> MuscleTarget(MuscleView.FRONT, setOf("deltoids"))
        "abdominals" -> MuscleTarget(MuscleView.FRONT, setOf("abs", "obliques"))
        "biceps" -> MuscleTarget(MuscleView.FRONT, setOf("biceps"))
        "forearms" -> MuscleTarget(MuscleView.FRONT, setOf("forearm"))
        "quadriceps" -> MuscleTarget(MuscleView.FRONT, setOf("quadriceps"))
        "adductors" -> MuscleTarget(MuscleView.FRONT, setOf("adductors"))
        "neck" -> MuscleTarget(MuscleView.FRONT, setOf("neck"))
        "triceps" -> MuscleTarget(MuscleView.BACK, setOf("triceps"))
        "lats", "middle back" -> MuscleTarget(MuscleView.BACK, setOf("upper-back"))
        "traps" -> MuscleTarget(MuscleView.BACK, setOf("trapezius"))
        "lower back" -> MuscleTarget(MuscleView.BACK, setOf("lower-back"))
        "glutes", "abductors" -> MuscleTarget(MuscleView.BACK, setOf("gluteal"))
        "hamstrings" -> MuscleTarget(MuscleView.BACK, setOf("hamstring"))
        "calves" -> MuscleTarget(MuscleView.BACK, setOf("calves"))
        else -> null
    }
}

/**
 * Friendly muscle-group label for the first of [primaryMuscles] (case/space-insensitive).
 * Back muscles collapse to "Back"; lower-body to "Legs". Null when empty or unmapped.
 */
fun muscleGroupLabel(primaryMuscles: List<String>): String? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> "Chest"
        "shoulders" -> "Shoulders"
        "biceps" -> "Biceps"
        "triceps" -> "Triceps"
        "forearms" -> "Forearms"
        "abdominals" -> "Abs"
        "glutes" -> "Glutes"
        "traps" -> "Traps"
        "neck" -> "Neck"
        "lats", "middle back", "lower back" -> "Back"
        "quadriceps", "hamstrings", "calves", "adductors", "abductors" -> "Legs"
        else -> null
    }
}
