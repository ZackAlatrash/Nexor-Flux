package com.zack.recomptracker.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.RoutineShareRepository
import com.zack.recomptracker.data.share.RoutineShareInbox
import com.zack.recomptracker.domain.share.Resolution
import com.zack.recomptracker.domain.share.RoutineSharePayload
import com.zack.recomptracker.domain.share.RoutineShareResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One previewed exercise row: display name + whether importing will create it new. */
data class ImportExerciseRow(val name: String, val setCount: Int, val willCreate: Boolean)

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class Preview(
        val name: String,
        val note: String?,
        val exercises: List<ImportExerciseRow>,
        val saving: Boolean = false,
    ) : ImportUiState
    data class Saved(val workoutId: Long) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class SharedRoutineImportViewModel(
    private val routineShareRepository: RoutineShareRepository,
    inbox: RoutineShareInbox,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var payload: RoutineSharePayload? = null

    init {
        val uri = inbox.consume()
        if (uri == null) {
            _state.value = ImportUiState.Error("This routine couldn't be opened. Try tapping the file again.")
        } else {
            viewModelScope.launch {
                when (val result = routineShareRepository.readPayload(uri)) {
                    is RoutineShareResult.Success -> {
                        payload = result.payload
                        val resolutions = routineShareRepository.resolve(result.payload)
                        _state.value = ImportUiState.Preview(
                            name = result.payload.name,
                            note = result.payload.note,
                            exercises = toRows(result.payload, resolutions),
                        )
                    }
                    RoutineShareResult.NotARoutineFile ->
                        _state.value = ImportUiState.Error("This file isn't a Recomp routine.")
                    RoutineShareResult.UnsupportedVersion ->
                        _state.value = ImportUiState.Error("This routine was shared from a newer version of the app. Update to import it.")
                    RoutineShareResult.Damaged ->
                        _state.value = ImportUiState.Error("This routine file is damaged and can't be opened.")
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val toSave = payload
        if (current !is ImportUiState.Preview || current.saving || toSave == null) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            runCatching { routineShareRepository.importRoutine(toSave) }
                .onSuccess { newId -> _state.value = ImportUiState.Saved(newId) }
                .onFailure { _state.value = ImportUiState.Error(it.message ?: "Couldn't save this routine.") }
        }
    }

    private fun toRows(payload: RoutineSharePayload, resolutions: List<Resolution>): List<ImportExerciseRow> =
        payload.exercises.mapIndexed { i, ex ->
            ImportExerciseRow(
                name = ex.name,
                setCount = ex.sets.size,
                willCreate = resolutions.getOrNull(i)?.willCreate ?: false,
            )
        }
}
