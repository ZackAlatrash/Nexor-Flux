package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.WorkoutSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class ActiveSessionViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) : ViewModel() {

    /** The current ACTIVE session, observed from the DB. */
    val session: StateFlow<WorkoutSession?> = sessionRepository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Elapsed seconds since the session's [WorkoutSession.startedAt].
     * Ticks every second while subscribed.
     */
    val elapsed: StateFlow<Long> = flow {
        while (true) {
            emit(computeElapsed())
            delay(1000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /**
     * Map of exerciseId → list of "weight×reps" strings (one per set index)
     * from the previous completed session for the same workoutId.
     * Populated lazily when a session with a workoutId becomes available.
     */
    private val _prevMap = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val prevMap: StateFlow<Map<Long, List<String>>> = _prevMap

    init {
        viewModelScope.launch {
            session.collect { s ->
                if (s != null && s.workoutId != null && _prevMap.value.isEmpty()) {
                    loadPrevMap(s.workoutId)
                }
            }
        }
    }

    private suspend fun loadPrevMap(workoutId: Long) {
        val prev = sessionRepository.getLastCompletedSession(workoutId) ?: return
        val map = prev.exercises.associate { ex ->
            ex.exerciseId to ex.sets.map { set ->
                buildPrevHint(set)
            }
        }
        _prevMap.value = map
    }

    private fun buildPrevHint(set: SessionSet): String {
        val w = set.weightKg
        return if (w != null) {
            val wStr = if (w == w.toLong().toDouble()) w.toLong().toString() else w.toString()
            "${wStr}×${set.reps}"
        } else {
            "${set.reps}"
        }
    }

    private fun computeElapsed(): Long {
        val s = session.value ?: return 0L
        return runCatching {
            val start = Instant.parse(s.startedAt)
            ChronoUnit.SECONDS.between(start, Instant.now()).coerceAtLeast(0L)
        }.getOrElse { 0L }
    }

    // ── Set mutations ─────────────────────────────────────────────────────────

    fun updateKg(se: SessionExercise, set: SessionSet, kg: Double?) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSet(
                    SessionSetEntity(
                        id = set.id,
                        sessionExerciseId = se.id,
                        setNumber = set.setNumber,
                        reps = set.reps,
                        weightKg = kg,
                        rir = set.rir,
                        completed = set.completed,
                    )
                )
            }
        }
    }

    fun updateReps(se: SessionExercise, set: SessionSet, reps: Int?) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSet(
                    SessionSetEntity(
                        id = set.id,
                        sessionExerciseId = se.id,
                        setNumber = set.setNumber,
                        reps = reps ?: 0,
                        weightKg = set.weightKg,
                        rir = set.rir,
                        completed = set.completed,
                    )
                )
            }
        }
    }

    fun updateRir(se: SessionExercise, set: SessionSet, rir: Int?) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSet(
                    SessionSetEntity(
                        id = set.id,
                        sessionExerciseId = se.id,
                        setNumber = set.setNumber,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        rir = rir,
                        completed = set.completed,
                    )
                )
            }
        }
    }

    /**
     * Toggle a set's completed state. Requires reps > 0 to mark complete.
     */
    fun toggleComplete(se: SessionExercise, set: SessionSet) {
        val newCompleted = if (set.completed) false else (set.reps > 0)
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSet(
                    SessionSetEntity(
                        id = set.id,
                        sessionExerciseId = se.id,
                        setNumber = set.setNumber,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        rir = set.rir,
                        completed = newCompleted,
                    )
                )
            }
        }
    }

    fun addSet(se: SessionExercise) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.addSet(
                    sessionExerciseId = se.id,
                    reps = 0,
                    weightKg = null,
                    rir = null,
                )
            }
        }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch {
            runCatching { sessionRepository.removeSet(setId) }
        }
    }

    // ── Exercise mutations ────────────────────────────────────────────────────

    /**
     * Add exercises by library IDs to the current session.
     * Looks up the name from the exercise library.
     */
    fun addExercises(ids: LongArray) {
        val sessionId = session.value?.id ?: return
        viewModelScope.launch {
            ids.forEach { id ->
                runCatching {
                    val exercise = exerciseLibraryRepository.getById(id) ?: return@forEach
                    sessionRepository.addExerciseToSession(sessionId, id, exercise.name)
                }
            }
        }
    }

    fun removeExercise(se: SessionExercise) {
        viewModelScope.launch {
            runCatching { sessionRepository.removeSessionExercise(se.id) }
        }
    }

    fun moveExerciseUp(se: SessionExercise) {
        val exercises = session.value?.exercises ?: return
        val sorted = exercises.sortedBy { it.sortOrder }
        val idx = sorted.indexOfFirst { it.id == se.id }
        if (idx <= 0) return
        val newOrder = sorted.toMutableList()
        val tmp = newOrder[idx]
        newOrder[idx] = newOrder[idx - 1]
        newOrder[idx - 1] = tmp
        viewModelScope.launch {
            runCatching {
                sessionRepository.reorderSessionExercises(newOrder.map { it.id })
            }
        }
    }

    fun moveExerciseDown(se: SessionExercise) {
        val exercises = session.value?.exercises ?: return
        val sorted = exercises.sortedBy { it.sortOrder }
        val idx = sorted.indexOfFirst { it.id == se.id }
        if (idx < 0 || idx >= sorted.size - 1) return
        val newOrder = sorted.toMutableList()
        val tmp = newOrder[idx]
        newOrder[idx] = newOrder[idx + 1]
        newOrder[idx + 1] = tmp
        viewModelScope.launch {
            runCatching {
                sessionRepository.reorderSessionExercises(newOrder.map { it.id })
            }
        }
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    fun setNote(text: String) {
        val sessionId = session.value?.id ?: return
        viewModelScope.launch {
            runCatching {
                sessionRepository.setSessionNote(sessionId, text.ifBlank { null })
            }
        }
    }

    fun setExerciseNote(se: SessionExercise, text: String) {
        viewModelScope.launch {
            runCatching {
                sessionRepository.setSessionExerciseNote(se.id, text.ifBlank { null })
            }
        }
    }

    // ── Finish ────────────────────────────────────────────────────────────────

    /**
     * Mark the session COMPLETED. Returns the session id on success, null if no
     * active session is loaded yet.
     */
    suspend fun finish(): Long? {
        val s = session.value ?: return null
        val duration = runCatching {
            val start = Instant.parse(s.startedAt)
            ChronoUnit.SECONDS.between(start, Instant.now()).toInt().coerceAtLeast(0)
        }.getOrElse { null }
        runCatching { sessionRepository.completeSession(s.id, duration) }
        return s.id
    }
}
