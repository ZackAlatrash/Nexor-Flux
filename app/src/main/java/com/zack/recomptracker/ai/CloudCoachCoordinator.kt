package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Abstraction over the coach's data tools so [CloudCoachCoordinator] is unit-testable without
 * Room. Backed in production by [CoachToolExecutor] + the coach system-prompt builder.
 */
interface CoachReadTools {
    suspend fun execute(name: String, args: Map<String, String>): String
    /** The full system prompt (plan, profile, today's snapshot, rules) for a new conversation. */
    suspend fun systemPromptSnapshot(): String
}

/**
 * [CoachCoordinator] backed by an OpenAI-compatible cloud model. Non-streaming tool loop:
 * send messages -> if the model requests tools, run them (confirming WRITE_TOOLS) -> resend
 * with tool results -> repeat until the model returns text.
 *
 * No per-turn or per-conversation caps (capability `unboundedToolLoop`), but [MAX_TOOL_ROUNDS]
 * is a hard safety ceiling against a runaway loop.
 */
class CloudCoachCoordinator(
    cloudReadyFlow: Flow<Boolean>,
    private val configFlow: StateFlow<CloudConfig?>,
    private val client: OpenAiCompatClient,
    private val tools: CoachReadTools,
    private val scope: CoroutineScope,
    private val knowledgeInjector: KnowledgeInjector = NoOpKnowledgeInjector,
    private val toolSchemas: List<String> = COACH_TOOL_SCHEMAS,
) : CoachCoordinator {

    private val _state = MutableStateFlow<CoachState>(CoachState.Unavailable)
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    private val history = mutableListOf<ChatMessage>()
    private val turnLock = Mutex()
    private val requestMessages = mutableListOf<ChatRequestMessage>()
    private var systemSeeded = false
    // The previous turn's injected reference message, dropped before injecting the next turn's so
    // multi-turn context never accumulates reference blocks.
    private var lastReferenceMessage: ChatRequestMessage? = null

    @Volatile private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    init {
        scope.launch {
            cloudReadyFlow.collect { ready ->
                if (!ready) {
                    _state.value = CoachState.Unavailable
                } else if (_state.value == CoachState.Unavailable) {
                    _state.value = if (history.isEmpty()) CoachState.Ready else CoachState.Idle(history.toList())
                }
            }
        }
    }

    override fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch { handleMessage(trimmed) }
    }

    override fun clearHistory() {
        pendingConfirmation?.complete(false)
        scope.launch {
            turnLock.withLock {
                history.clear()
                requestMessages.clear()
                systemSeeded = false
                lastReferenceMessage = null
                if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
            }
        }
    }

    override fun confirmPendingAction() { pendingConfirmation?.complete(true) }
    override fun cancelPendingAction() { pendingConfirmation?.complete(false) }

    private suspend fun handleMessage(userText: String) {
        turnLock.withLock {
            val config = configFlow.value ?: run {
                _state.value = CoachState.Error(history.toList(), "Cloud AI not configured.")
                return@withLock
            }
            history.add(ChatMessage(Role.User, userText))
            _state.value = CoachState.Thinking(history.toList(), toolStatus = "Thinking…")
            try {
                if (!systemSeeded) {
                    requestMessages.add(ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot()))
                    systemSeeded = true
                }
                // Refresh the per-turn knowledge block: drop the previous turn's block, inject a
                // fresh one for THIS question, positioned immediately before the user message.
                lastReferenceMessage?.let { requestMessages.remove(it) }
                val reference = knowledgeInjector.referenceBlock(userText)
                lastReferenceMessage = if (reference.isNotBlank()) {
                    ChatRequestMessage(role = "system", content = reference).also { requestMessages.add(it) }
                } else {
                    null
                }
                requestMessages.add(ChatRequestMessage(role = "user", content = userText))

                var rounds = 0
                while (true) {
                    if (rounds++ >= MAX_TOOL_ROUNDS) {
                        requestMessages.clear()
                        systemSeeded = false
                        lastReferenceMessage = null
                        _state.value = CoachState.Error(history.toList(), "Something went wrong — try again.")
                        break
                    }
                    // Timeout applies to each network completion call; user-confirmation
                    // waits (confirmAndRun) are intentionally excluded from the timeout.
                    val response = withTimeout(TURN_TIMEOUT_MS) {
                        client.completion(config, requestMessages.toList(), toolSchemas)
                    }

                    if (response.toolCalls.isEmpty()) {
                        val text = response.text.ifBlank { "Done." }
                        requestMessages.add(ChatRequestMessage(role = "assistant", content = text))
                        _state.value = CoachState.Responding(history.toList(), partial = text)
                        history.add(ChatMessage(Role.Assistant, text))
                        _state.value = CoachState.Idle(history.toList())
                        break
                    }

                    // Replay the assistant tool-call turn into request history.
                    requestMessages.add(
                        ChatRequestMessage(
                            role = "assistant",
                            content = null,
                            assistantToolCallsJson = encodeToolCalls(response.toolCalls),
                        ),
                    )

                    // Execute each tool call, collecting results for the next completion.
                    for (call in response.toolCalls) {
                        _state.value = CoachState.Thinking(history.toList(), toolStatus = toolStatusText(call.name))
                        val result = if (call.name in COACH_WRITE_TOOLS) {
                            confirmAndRun(call)
                        } else {
                            tools.execute(call.name, call.arguments)
                        }
                        requestMessages.add(
                            ChatRequestMessage(
                                role = "tool",
                                content = result,
                                toolCallId = call.id,
                                name = call.name,
                            ),
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                requestMessages.clear()
                systemSeeded = false
                lastReferenceMessage = null
                _state.value = CoachState.Error(history.toList(), "Took too long — try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requestMessages.clear()
                systemSeeded = false
                lastReferenceMessage = null
                _state.value = CoachState.Error(history.toList(), "Something went wrong — try again.")
            }
        }
    }

    private suspend fun confirmAndRun(call: ParsedToolCall): String {
        val action = PendingCoachAction(
            toolName = call.name,
            args = call.arguments,
            displayText = pendingActionDisplayText(call.name, call.arguments),
        )
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmation = deferred
        _state.value = CoachState.AwaitingConfirmation(history.toList(), action)
        val confirmed = deferred.await()
        pendingConfirmation = null
        if (!confirmed) return """{"cancelled":true}"""
        _state.value = CoachState.Thinking(history.toList(), toolStatus = toolStatusText(call.name))
        return tools.execute(call.name, call.arguments)
    }

    private fun encodeToolCalls(calls: List<ParsedToolCall>): String {
        val array = buildJsonArray {
            calls.forEach { call ->
                addJsonObject {
                    put("id", call.id)
                    put("type", "function")
                    put(
                        "function",
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(call.name),
                                "arguments" to JsonPrimitive(encodeArgs(call.arguments)),
                            ),
                        ),
                    )
                }
            }
        }
        return array.toString()
    }

    private fun encodeArgs(args: Map<String, String>): String =
        JsonObject(args.mapValues { (_, v) -> JsonPrimitive(v) }).toString()

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
        "log_meal" -> "Logging meal…"
        "log_metric" -> "Saving metric…"
        "update_calorie_target" -> "Updating calorie target…"
        "search_web" -> "Searching the web…"
        else -> "Running tool…"
    }

    private fun pendingActionDisplayText(toolName: String, args: Map<String, String>): String =
        when (toolName) {
            "log_meal" -> buildString {
                append("Log ${args["name"]}")
                val grams = args["grams"]?.toDoubleOrNull()
                if (grams != null) append(" (${grams.toInt()}g)")
                val cals = args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
                if (cals != null && cals > 0) append(" ($cals kcal)")
                val type = args["meal_type"]
                if (!type.isNullOrBlank()) append(" as $type")
                append(" to today's food log")
            }
            "log_metric" -> "Save ${args["metric"]} = ${args["value"]}"
            "update_calorie_target" -> "Update daily calorie target to ${args["target_calories"]} kcal"
            else -> toolName
        }

    private companion object {
        private const val MAX_TOOL_ROUNDS = 12
        private const val TURN_TIMEOUT_MS = 180_000L
    }
}
