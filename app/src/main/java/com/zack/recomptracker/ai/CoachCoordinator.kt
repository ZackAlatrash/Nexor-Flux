package com.zack.recomptracker.ai

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.StateFlow

interface CoachCoordinator {
    val state: StateFlow<CoachState>
    fun sendMessage(text: String)
    fun clearHistory()
    fun confirmPendingAction()
    fun cancelPendingAction()
}

sealed class CoachState {
    object Unavailable : CoachState()
    object Ready : CoachState()
    data class Idle(val history: List<ChatMessage>) : CoachState()
    /**
     * In-progress turn. [steps] is the running process log (thinking + tool calls, in order); the
     * last entry is the active step. Empty until the first step is pushed.
     */
    data class Thinking(val history: List<ChatMessage>, val steps: List<String> = emptyList()) : CoachState()
    data class Responding(val history: List<ChatMessage>, val partial: String) : CoachState()
    data class Error(val history: List<ChatMessage>, val message: String) : CoachState()
    data class AwaitingConfirmation(
        val history: List<ChatMessage>,
        val pendingAction: PendingCoachAction,
    ) : CoachState()
}

data class PendingCoachAction(
    val toolName: String,
    val args: Map<String, String>,
    val displayText: String,
)

/**
 * A single chat turn. [role] + [text] carry the content and define equality (unchanged data-class
 * semantics — two messages with the same role and text are equal).
 *
 * [id] is a process-unique, monotonically increasing identity assigned at construction. It is
 * DELIBERATELY outside the primary constructor so it is excluded from `equals`/`hashCode`/`copy`
 * (a copied or re-constructed message keeps value equality). Messages are only ever appended to the
 * in-memory history and never reordered, so this id is a stable per-item key for `LazyColumn`
 * (unlike the list index, which shifts, or `hashCode()`, which collides for repeated identical
 * text). It is never persisted or serialized, so it needs no serialization-safe form.
 */
data class ChatMessage(val role: Role, val text: String) {
    val id: Long = nextMessageId()
}

private val messageIdCounter = AtomicLong(0L)

private fun nextMessageId(): Long = messageIdCounter.getAndIncrement()

enum class Role { User, Assistant }
