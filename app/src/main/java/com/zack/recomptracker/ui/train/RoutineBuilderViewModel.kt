package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.NewWorkoutLine
import com.zack.recomptracker.data.repository.PlannedSetDraft
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.domain.workout.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── Draft models ──────────────────────────────────────────────────────────────

/** One planned set in the builder. Both targets are optional (null = user left it blank). */
data class BuilderSet(
    val targetReps: Int?,
    val targetWeightKg: Double?,
)

/** One exercise line in the builder. */
data class BuilderExercise(
    val exercise: Exercise,
    val sets: List<BuilderSet>,
    val note: String?,
)

/** Full builder screen state. */
data class RoutineBuilderUiState(
    val workoutId: Long? = null,        // null = new routine
    val name: String = "",
    val note: String = "",
    val exercises: List<BuilderExercise> = emptyList(),
    val isSaving: Boolean = false,
) {
    /** Save is allowed once the user has a non-blank name and at least one exercise. */
    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()

    /** True when any field differs from the initial empty state (used for discard confirm). */
    val isDirty: Boolean get() = name.isNotBlank() || note.isNotBlank() || exercises.isNotEmpty()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RoutineBuilderViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutineBuilderUiState())
    val state: StateFlow<RoutineBuilderUiState> = _state.asStateFlow()

    // ── Load existing workout (edit mode) ─────────────────────────────────────

    suspend fun loadWorkout(workoutId: Long?) {
        if (workoutId == null || workoutId == -1L) return
        val template = workoutRepository.getById(workoutId) ?: return
        val exercises = template.exercises.map { templateEx ->
            BuilderExercise(
                exercise = templateEx.exercise,
                sets = templateEx.plannedSets.map { ps ->
                    BuilderSet(
                        targetReps = ps.targetReps,
                        targetWeightKg = ps.targetWeightKg,
                    )
                }.ifEmpty { listOf(BuilderSet(null, null)) },
                note = templateEx.note,
            )
        }
        _state.update { it.copy(workoutId = workoutId, name = template.name, note = template.note.orEmpty(), exercises = exercises) }
    }

    // ── Name / note ───────────────────────────────────────────────────────────

    fun setName(s: String) = _state.update { it.copy(name = s) }

    fun setNote(s: String) = _state.update { it.copy(note = s) }

    // ── Exercise list mutations ───────────────────────────────────────────────

    /** Resolve picked exercise IDs from the picker and append each with one empty set. */
    suspend fun addExercises(ids: LongArray) {
        val resolved = ids.toList().mapNotNull { exerciseLibraryRepository.getById(it) }
        if (resolved.isEmpty()) return
        _state.update { current ->
            val newExercises = resolved.map { exercise ->
                BuilderExercise(
                    exercise = exercise,
                    sets = listOf(BuilderSet(null, null)),
                    note = null,
                )
            }
            current.copy(exercises = current.exercises + newExercises)
        }
    }

    fun removeExercise(index: Int) = _state.update { current ->
        current.copy(exercises = current.exercises.toMutableList().also { it.removeAt(index) })
    }

    /** Swap exercise at [from] with exercise at [to]. */
    fun reorder(from: Int, to: Int) {
        _state.update { current ->
            val list = current.exercises.toMutableList()
            val item = list.removeAt(from)
            list.add(to.coerceIn(0, list.size), item)
            current.copy(exercises = list)
        }
    }

    // ── Set mutations ─────────────────────────────────────────────────────────

    fun addSet(exIndex: Int) = _state.update { current ->
        val updated = current.exercises.mapIndexed { i, ex ->
            if (i == exIndex) ex.copy(sets = ex.sets + BuilderSet(null, null))
            else ex
        }
        current.copy(exercises = updated)
    }

    fun removeSet(exIndex: Int, setIndex: Int) = _state.update { current ->
        val updated = current.exercises.mapIndexed { i, ex ->
            if (i == exIndex) {
                val newSets = ex.sets.toMutableList().also { it.removeAt(setIndex) }
                // Ensure there is always at least one set in PLAN mode
                ex.copy(sets = newSets.ifEmpty { listOf(BuilderSet(null, null)) })
            } else {
                ex
            }
        }
        current.copy(exercises = updated)
    }

    fun setTarget(exIndex: Int, setIndex: Int, reps: Int?, weightKg: Double?) =
        _state.update { current ->
            val updated = current.exercises.mapIndexed { i, ex ->
                if (i == exIndex) {
                    val newSets = ex.sets.mapIndexed { j, s ->
                        if (j == setIndex) s.copy(targetReps = reps, targetWeightKg = weightKg)
                        else s
                    }
                    ex.copy(sets = newSets)
                } else {
                    ex
                }
            }
            current.copy(exercises = updated)
        }

    // ── Save ─────────────────────────────────────────────────────────────────

    /** Persist the routine and return the saved workout ID. */
    suspend fun save(): Long {
        val s = _state.value
        _state.update { it.copy(isSaving = true) }
        return try {
            val lines = s.exercises.map { ex ->
                NewWorkoutLine(
                    exerciseId = ex.exercise.id,
                    plannedSets = ex.sets.map { bs ->
                        PlannedSetDraft(
                            targetReps = bs.targetReps,
                            targetWeightKg = bs.targetWeightKg,
                        )
                    },
                    note = ex.note,
                )
            }
            if (s.workoutId != null) {
                workoutRepository.updateWorkout(s.workoutId, s.name, s.note.ifBlank { null }, lines)
                s.workoutId
            } else {
                workoutRepository.saveWorkout(s.name, s.note.ifBlank { null }, lines)
            }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }
}
