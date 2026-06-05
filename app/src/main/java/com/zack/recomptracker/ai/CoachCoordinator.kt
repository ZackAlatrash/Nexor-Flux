package com.zack.recomptracker.ai

import kotlinx.coroutines.flow.StateFlow

interface CoachCoordinator {
    val state: StateFlow<CoachState>
    fun sendMessage(text: String)
    fun clearHistory()
}

sealed class CoachState {
    object Unavailable : CoachState()
    object Ready : CoachState()
    data class Idle(val history: List<ChatMessage>) : CoachState()
    data class Thinking(val history: List<ChatMessage>, val toolStatus: String? = null) : CoachState()
    data class Responding(val history: List<ChatMessage>, val partial: String) : CoachState()
    data class Error(val history: List<ChatMessage>, val message: String) : CoachState()
}

data class ChatMessage(val role: Role, val text: String)

enum class Role { User, Assistant }
