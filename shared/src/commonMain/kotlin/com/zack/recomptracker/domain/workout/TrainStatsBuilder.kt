package com.zack.recomptracker.domain.workout

/**
 * Builds the Stats entry screen's data: the six muscle categories, each with the distinct
 * exercises the user has actually logged (>=1 completed set) that target that category.
 *
 * Pure: derives entirely from already-loaded domain data — completed-session [history] and the
 * exercise [library] (for each exercise's primaryMuscles). No Room / Android dependencies.
 */
object TrainStatsBuilder {

    /** One logged exercise within a category, with lightweight usage stats. */
    data class LoggedExerciseSummary(
        val exerciseId: Long,
        val name: String,
        val primaryMuscles: List<String>,
        val sessionCount: Int,
        val lastDate: String?,
    )

    /** A category and the logged exercises under it (possibly empty). */
    data class CategoryStats(
        val category: MuscleCategory,
        val exercises: List<LoggedExerciseSummary>,
    )

    fun build(history: List<WorkoutSession>, library: List<Exercise>): List<CategoryStats> {
        val musclesByExerciseId: Map<Long, List<String>> = library.associate { it.id to it.primaryMuscles }

        // exerciseId -> (name, set of session dates) for exercises with >=1 completed set.
        data class Acc(var name: String, val dates: MutableSet<String> = mutableSetOf())
        val acc = mutableMapOf<Long, Acc>()

        for (session in history) {
            for (ex in session.exercises) {
                val hasCompleted = ex.sets.any { it.completed }
                if (!hasCompleted) continue
                val a = acc.getOrPut(ex.exerciseId) { Acc(ex.exerciseName) }
                a.name = ex.exerciseName
                a.dates += session.date
            }
        }

        val summaries: List<Pair<MuscleCategory, LoggedExerciseSummary>> = acc.mapNotNull { (id, a) ->
            val muscles = musclesByExerciseId[id] ?: emptyList()
            val category = muscleCategoryFor(muscles) ?: return@mapNotNull null
            category to LoggedExerciseSummary(
                exerciseId = id,
                name = a.name,
                primaryMuscles = muscles,
                sessionCount = a.dates.size,
                lastDate = a.dates.maxOrNull(),
            )
        }

        val byCategory = summaries.groupBy({ it.first }, { it.second })
        return MuscleCategory.entries.map { category ->
            CategoryStats(
                category = category,
                exercises = byCategory[category].orEmpty().sortedBy { it.name.lowercase() },
            )
        }
    }
}
