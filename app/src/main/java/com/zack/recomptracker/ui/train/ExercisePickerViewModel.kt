package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.domain.workout.Exercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class ExercisePickerUiState(
    val query: String = "",
    val muscleFilter: String = "All",
    val results: List<Exercise> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
)

/** Maps a muscle-group chip label to the set of muscle strings it should match. */
private val MUSCLE_GROUP_KEYWORDS: Map<String, List<String>> = mapOf(
    "Chest"     to listOf("chest"),
    "Back"      to listOf("lats", "middle back", "lower back", "traps"),
    "Shoulders" to listOf("shoulders"),
    "Legs"      to listOf("quadriceps", "hamstrings", "glutes", "calves"),
    "Arms"      to listOf("biceps", "triceps", "forearms"),
    "Core"      to listOf("abdominals"),
)

val MUSCLE_FILTER_CHIPS = listOf("All", "Chest", "Back", "Shoulders", "Legs", "Arms", "Core")

private fun Exercise.matchesMuscleFilter(chip: String): Boolean {
    if (chip == "All") return true
    val keywords = MUSCLE_GROUP_KEYWORDS[chip] ?: return false
    return primaryMuscles.any { muscle ->
        keywords.any { kw -> muscle.contains(kw, ignoreCase = true) }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExercisePickerViewModel(
    private val repository: ExerciseLibraryRepository,
) : ViewModel() {

    private val _query  = MutableStateFlow("")
    private val _filter = MutableStateFlow("All")
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** Exercises from the repository — searched when query non-blank, else full list. */
    private val repoResults = _query
        .debounce(180)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                repository.observeAll()
            } else {
                flow { emit(repository.search(q)) }
            }
        }

    val state: StateFlow<ExercisePickerUiState> = combine(
        _query,
        _filter,
        _selected,
        repoResults,
    ) { query, filter, selected, exercises ->
        val filtered = exercises.filter { it.matchesMuscleFilter(filter) }
        ExercisePickerUiState(
            query = query,
            muscleFilter = filter,
            results = filtered,
            selected = selected,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState())

    fun setQuery(q: String) { _query.value = q }

    fun setFilter(f: String) { _filter.value = f }

    fun toggle(id: Long) {
        _selected.value = _selected.value.toMutableSet().also { s ->
            if (!s.add(id)) s.remove(id)
        }
    }

    fun selectedExercises(): List<Long> = _selected.value.toList()

    suspend fun createCustom(name: String): Long {
        val id = repository.addCustomExercise(name)
        _selected.value = _selected.value + id
        return id
    }

    suspend fun detail(id: Long): Exercise? = repository.getById(id)
}
