package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.ExerciseStatsCalculator
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzer
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

/** Per-exercise visual info resolved from the library for the active session. */
data class ExerciseVisual(val imagePath: String?, val primaryMuscles: List<String>)

/**
 * One-shot "you just set a PR" signal for the mid-workout banner. Emitted when a set is marked
 * complete and its Epley e1RM beats the best-ever e1RM for that exercise. [headline] is the
 * already-formatted callout line (via [PrCalloutFormatter]); [exerciseId] identifies the lift so the
 * banner fires at most once per exercise per session.
 */
data class PrEvent(
    val exerciseId: Long,
    val exerciseName: String,
    val e1rmKg: Double,
    val headline: String,
)

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

    /** Map of exerciseId → resolved image path + primary muscles, for thumbnails. */
    private val _exerciseVisuals = MutableStateFlow<Map<Long, ExerciseVisual>>(emptyMap())
    val exerciseVisuals: StateFlow<Map<Long, ExerciseVisual>> = _exerciseVisuals

    /**
     * The library exercise whose details popup is currently open, or null when closed.
     * Purely a UI overlay flag — independent of the workout session data, so opening
     * and closing it never touches sets, reps, ordering, or notes.
     */
    private val _detailExercise = MutableStateFlow<Exercise?>(null)
    val detailExercise: StateFlow<Exercise?> = _detailExercise

    /**
     * One-shot PR banner signal. Null when no banner is pending; set when a completing set beats the
     * prior best e1RM for its exercise. The screen consumes it and calls [dismissPrEvent] to clear —
     * a transient StateFlow (not persistent state), so it never re-shows on recomposition.
     */
    private val _prEvent = MutableStateFlow<PrEvent?>(null)
    val prEvent: StateFlow<PrEvent?> = _prEvent

    /**
     * Best-ever e1RM per exerciseId from COMPLETED history, loaded lazily as exercises appear this
     * session. The in-progress session isn't a completed session yet, so this reflects history BEFORE
     * the current set. Updated in-memory when a PR fires so a later set can't re-fire the same lift.
     */
    private val priorBestE1rmByExerciseId = mutableMapOf<Long, Double>()

    /** Exercise ids that already fired their banner this session — the once-per-exercise guard. */
    private val firedPrExerciseIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            session.collect { s ->
                if (s != null && s.workoutId != null && _prevMap.value.isEmpty()) {
                    loadPrevMap(s.workoutId)
                }
                if (s != null) {
                    resolveVisuals(s)
                    loadPriorBestE1rm(s)
                }
            }
        }
    }

    /**
     * Load the best-ever e1RM (completed history) for any session exercise not yet cached. Cheap and
     * idempotent — runs once per exercise id, so repeated session emissions (the per-second timer)
     * don't re-query.
     */
    private suspend fun loadPriorBestE1rm(session: WorkoutSession) {
        val missing = session.exercises.map { it.exerciseId }.distinct()
            .filter { it !in priorBestE1rmByExerciseId }
        missing.forEach { id ->
            val history = runCatching { sessionRepository.getExerciseHistory(id) }.getOrNull().orEmpty()
            val best = ExerciseStatsCalculator.calculate(history).bestOneRepMax
            // Store 0.0 when there's no prior weighted history so the first weighted PR still fires.
            priorBestE1rmByExerciseId[id] = best ?: 0.0
        }
    }

    private suspend fun resolveVisuals(session: WorkoutSession) {
        val current = _exerciseVisuals.value
        val missing = session.exercises.map { it.exerciseId }.distinct()
            .filter { it !in current }
        if (missing.isEmpty()) return
        val resolved = current.toMutableMap()
        missing.forEach { id ->
            val ex = exerciseLibraryRepository.getById(id)
            resolved[id] = ExerciseVisual(
                imagePath = ex?.images?.firstOrNull(),
                primaryMuscles = ex?.primaryMuscles ?: emptyList(),
            )
        }
        _exerciseVisuals.value = resolved
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

    // Each editor writes ONLY its own column (P1-18). Building a whole-entity write from the passed
    // `set` snapshot used to revert a sibling field the user had just typed (the flow hadn't re-emitted
    // yet). Targeted updates can't clobber. `se` is unused now but kept for call-site stability.
    fun updateKg(se: SessionExercise, set: SessionSet, kg: Double?) {
        viewModelScope.launch { runCatching { sessionRepository.updateSetWeight(set.id, kg) } }
    }

    fun updateReps(se: SessionExercise, set: SessionSet, reps: Int?) {
        viewModelScope.launch { runCatching { sessionRepository.updateSetReps(set.id, reps ?: 0) } }
    }

    fun updateRir(se: SessionExercise, set: SessionSet, rir: Int?) {
        viewModelScope.launch { runCatching { sessionRepository.updateSetRir(set.id, rir) } }
    }

    /**
     * Toggle a set's completed state. Requires reps > 0 to mark complete — judged against the CURRENT
     * persisted row (re-read inside the coroutine), not the possibly-stale `set` snapshot, so a
     * just-typed reps value isn't ignored (P1-18). Writes only the completed column.
     */
    fun toggleComplete(se: SessionExercise, set: SessionSet) {
        viewModelScope.launch {
            runCatching {
                val current = sessionRepository.getSet(set.id) ?: set
                val newCompleted = if (current.completed) false else (current.reps > 0)
                sessionRepository.updateSetCompleted(set.id, newCompleted)
                if (newCompleted) {
                    detectPr(se, current)
                }
            }
        }
    }

    /**
     * Fire the PR banner if [set]'s Epley e1RM beats the prior best for [se]'s exercise. At most once
     * per exercise per session, only on a genuine new max. Updates the in-memory best so the same lift
     * can't re-fire, and stores nothing persistent. Visible for unit testing.
     */
    internal suspend fun detectPr(se: SessionExercise, set: SessionSet) {
        val exerciseId = se.exerciseId
        if (exerciseId in firedPrExerciseIds) return
        val e1rm = WorkoutProgressAnalyzer.estimatedOneRepMax(set.reps, set.weightKg) ?: return
        // Ensure a prior-best is loaded even if the session collector hasn't cached it yet.
        val priorBest = priorBestE1rmByExerciseId[exerciseId] ?: run {
            val history = runCatching { sessionRepository.getExerciseHistory(exerciseId) }.getOrNull().orEmpty()
            (ExerciseStatsCalculator.calculate(history).bestOneRepMax ?: 0.0)
                .also { priorBestE1rmByExerciseId[exerciseId] = it }
        }
        if (e1rm <= priorBest) return

        firedPrExerciseIds += exerciseId
        priorBestE1rmByExerciseId[exerciseId] = e1rm
        val headline = PrCalloutFormatter.format(se.exerciseName, buildPrevHint(set)).headline
        _prEvent.value = PrEvent(
            exerciseId = exerciseId,
            exerciseName = se.exerciseName,
            e1rmKg = e1rm,
            headline = headline,
        )
    }

    /** Clear the pending PR banner after the screen has shown it. */
    fun dismissPrEvent() {
        _prEvent.value = null
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

    /**
     * Replace one exercise in the current session with a library exercise — this workout only.
     * The routine template is untouched, so next time the original exercise reappears. The active
     * session flow re-emits from the DB, so the card name/thumbnail refresh automatically. No-op if
     * the replacement id can't be resolved.
     */
    fun replaceExercise(sessionExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch {
            runCatching {
                val exercise = exerciseLibraryRepository.getById(newExerciseId) ?: return@launch
                sessionRepository.replaceSessionExercise(sessionExerciseId, newExerciseId, exercise.name)
            }
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

    /** Persist a full exercise order (used by drag-to-reorder). */
    fun reorderExercises(orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching { sessionRepository.reorderSessionExercises(orderedIds) }
        }
    }

    // ── Exercise details popup ────────────────────────────────────────────────

    /**
     * Open the details popup for a library exercise. Resolves the full [Exercise]
     * from the library by id; if the lookup fails (e.g. a custom exercise that was
     * removed) the popup simply stays closed rather than showing a broken sheet.
     */
    fun showExerciseDetails(exerciseId: Long) {
        viewModelScope.launch {
            runCatching { exerciseLibraryRepository.getById(exerciseId) }
                .getOrNull()
                ?.let { _detailExercise.value = it }
        }
    }

    /** Close the details popup. */
    fun dismissExerciseDetails() {
        _detailExercise.value = null
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
     * Prepare the session for its summary WITHOUT completing it. Records the elapsed
     * duration but leaves the session ACTIVE, so backing out of the summary keeps the
     * workout in progress. The session is only finalised by Save (complete) or Discard
     * (abandon) on the summary screen. Returns the session id, or null if none loaded.
     */
    suspend fun finish(): Long? {
        val s = session.value ?: return null
        val duration = runCatching {
            val start = Instant.parse(s.startedAt)
            ChronoUnit.SECONDS.between(start, Instant.now()).toInt().coerceAtLeast(0)
        }.getOrElse { null }
        runCatching { sessionRepository.updateSessionDuration(s.id, duration) }
        return s.id
    }

    // ── Discard ───────────────────────────────────────────────────────────────

    /**
     * Abandon the ACTIVE session directly (status → ABANDONED) without completing it.
     * A fast path that mirrors the end state of Finish → Session Summary → "Discard
     * Workout", but skips the completion + summary detour. No-op if no active session.
     */
    suspend fun discard() {
        val s = session.value ?: return
        runCatching { sessionRepository.abandonSession(s.id) }
    }
}
