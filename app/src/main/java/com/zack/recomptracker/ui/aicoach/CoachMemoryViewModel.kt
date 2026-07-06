package com.zack.recomptracker.ui.aicoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.coach.CoachMemoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoachMemoryViewModel(private val memory: CoachMemory) : ViewModel() {
    val entries: StateFlow<List<CoachMemoryEntry>> =
        memory.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) { if (text.isNotBlank()) viewModelScope.launch { memory.add(text) } }
    fun update(id: String, text: String) { if (text.isNotBlank()) viewModelScope.launch { memory.update(id, text) } }
    fun delete(id: String) { viewModelScope.launch { memory.delete(id) } }
}
