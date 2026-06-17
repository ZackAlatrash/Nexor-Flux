package com.zack.recomptracker.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseRecap(
    val name: String,
    val sets: Int,
    val topSet: String,
    val volume: Double,
    val isPr: Boolean,
)

data class SessionSummaryUiState(
    val loading: Boolean = true,
    val workoutName: String = "",
    val date: String = "",
    val durationSeconds: Int = 0,
    val totalVolume: Double = 0.0,
    val totalSets: Int = 0,
    val prCount: Int = 0,
    val note: String = "",
    val exercises: List<ExerciseRecap> = emptyList(),
)

class SessionSummaryViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    private val _state = MutableStateFlow(SessionSummaryUiState())
    val state: StateFlow<SessionSummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
        }
    }

    private suspend fun load() {
        val session = sessionRepository.getSession(sessionId) ?: run {
            _state.update { it.copy(loading = false) }
            return
        }

        // ── Duration ─────────────────────────────────────────────────────────────
        val duration: Int = run {
            val start = session.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val end = session.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (start != null && end != null) {
                (end.epochSecond - start.epochSecond).toInt().coerceAtLeast(0)
            } else {
                session.durationSeconds ?: 0
            }
        }

        // ── Date display ─────────────────────────────────────────────────────────
        val dateDisplay = runCatching {
            LocalDate.parse(session.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }.getOrElse { session.date }

        // ── Volume + sets ─────────────────────────────────────────────────────────
        val totalVolume = session.exercises.sumOf { ex ->
            WorkoutProgressAnalyzer.sessionVolume(ex.sets)
        }
        val totalSets = session.exercises.sumOf { ex ->
            ex.sets.count { it.completed }
        }

        // ── PR detection ─────────────────────────────────────────────────────────
        // For each exercise: compute this session's best est-1RM, then compare to
        // the best est-1RM in history EXCLUDING this session's date.
        val sessionDate = session.date
        val exerciseRecaps = session.exercises.sortedBy { it.sortOrder }.map { ex ->
            val completedSets = ex.sets.filter { it.completed }
            val vol = WorkoutProgressAnalyzer.sessionVolume(ex.sets)
            val bestSet = WorkoutProgressAnalyzer.bestSet(ex.sets)

            val topSetStr = if (bestSet != null) {
                val weight = bestSet.weightKg
                if (weight != null) {
                    val kg = if (weight == weight.toLong().toDouble()) {
                        weight.toLong().toString()
                    } else {
                        "%.1f".format(weight)
                    }
                    "${kg}×${bestSet.reps}"
                } else {
                    "BW×${bestSet.reps}"
                }
            } else {
                ""
            }

            val thisSessionBest1RM = bestSet?.let {
                WorkoutProgressAnalyzer.estimatedOneRepMax(it.reps, it.weightKg)
            }

            val isPr = if (thisSessionBest1RM != null) {
                val history = sessionRepository.getExerciseHistory(ex.exerciseId)
                val priorBest = history
                    .filter { it.date != sessionDate }
                    .mapNotNull { WorkoutProgressAnalyzer.estimatedOneRepMax(it.reps, it.weightKg) }
                    .maxOrNull()
                // PR if no prior history or this session beats it
                priorBest == null || thisSessionBest1RM > priorBest
            } else {
                false
            }

            ExerciseRecap(
                name = ex.exerciseName,
                sets = completedSets.size,
                topSet = topSetStr,
                volume = vol,
                isPr = isPr,
            )
        }

        val prCount = exerciseRecaps.count { it.isPr }

        _state.update {
            it.copy(
                loading = false,
                workoutName = session.workoutName,
                date = dateDisplay,
                durationSeconds = duration,
                totalVolume = totalVolume,
                totalSets = totalSets,
                prCount = prCount,
                note = session.note ?: "",
                exercises = exerciseRecaps,
            )
        }
    }

    fun setDuration(seconds: Int) {
        _state.update { it.copy(durationSeconds = seconds.coerceAtLeast(0)) }
    }

    fun setNote(text: String) {
        _state.update { it.copy(note = text) }
    }

    suspend fun save() {
        val s = _state.value
        sessionRepository.completeSession(sessionId, s.durationSeconds)
        sessionRepository.setSessionNote(sessionId, s.note.takeIf { it.isNotBlank() })
    }

    suspend fun discard() {
        sessionRepository.abandonSession(sessionId)
    }
}
