package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.time.SystemDateProvider
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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
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

    /**
     * Confirmation-dialog text for a pending log_meal, resolved the SAME way execution resolves it
     * (library match + planned/date), so the dialog can't describe a different action than the one
     * performed (review P1-9). Null → the caller falls back to a generic description of the args.
     */
    suspend fun describeLoggedMeal(args: Map<String, String>): String? = null
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
    // Used only to detect a calendar-day rollover mid-conversation, so the once-seeded system
    // snapshot (Today's date + totals) can be rebuilt instead of answering with yesterday's data.
    private val dateProvider: DateProvider = SystemDateProvider(),
) : CoachCoordinator {

    private val _state = MutableStateFlow<CoachState>(CoachState.Unavailable)
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    private val history = mutableListOf<ChatMessage>()
    private val turnLock = Mutex()
    private val requestMessages = mutableListOf<ChatRequestMessage>()
    private var systemSeeded = false
    // The calendar day the system snapshot was (re)seeded for; a change means the snapshot is stale.
    private var seededDate: LocalDate? = null
    // The previous turn's injected reference message, dropped before injecting the next turn's so
    // multi-turn context never accumulates reference blocks.
    private var lastReferenceMessage: ChatRequestMessage? = null

    // Holds the in-flight write-tool confirmation. AtomicReference + getAndSet ensures a tap can only
    // ever complete the deferred it was shown for: the first confirm/cancel/clear atomically claims
    // and clears it, so a stale or double tap can't complete a *later* turn's confirmation (which
    // would silently run an unconfirmed write).
    private val pendingConfirmation = AtomicReference<CompletableDeferred<Boolean>?>(null)

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
        pendingConfirmation.getAndSet(null)?.complete(false)
        scope.launch {
            turnLock.withLock {
                history.clear()
                requestMessages.clear()
                systemSeeded = false
                seededDate = null
                lastReferenceMessage = null
                if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
            }
        }
    }

    override fun confirmPendingAction() { pendingConfirmation.getAndSet(null)?.complete(true) }
    override fun cancelPendingAction() { pendingConfirmation.getAndSet(null)?.complete(false) }

    private suspend fun handleMessage(userText: String) {
        turnLock.withLock {
            val config = configFlow.value ?: run {
                _state.value = CoachState.Error(history.toList(), "Cloud AI not configured.")
                return@withLock
            }
            history.add(ChatMessage(Role.User, userText))
            thinkingSteps.clear()
            committedWrites.clear()
            pushThinking("Thinking…")
            try {
                val today = dateProvider.today()
                // The system snapshot embeds Today's date + totals and is seeded once. If the day
                // rolled over mid-conversation, rebuild it in place (index 0 is always the snapshot)
                // so the coach stops answering "today" with yesterday's data (review P2-3).
                if (systemSeeded && seededDate != today && requestMessages.firstOrNull()?.role == "system") {
                    requestMessages[0] = ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot())
                    seededDate = today
                }
                if (!systemSeeded) {
                    requestMessages.add(ChatRequestMessage(role = "system", content = tools.systemPromptSnapshot()))
                    systemSeeded = true
                    seededDate = today
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
                var nudged = false
                while (true) {
                    if (rounds++ >= MAX_TOOL_ROUNDS) {
                        requestMessages.clear()
                        systemSeeded = false
                        lastReferenceMessage = null
                        _state.value = CoachState.Error(history.toList(), failureMessage("Something went wrong — try again."))
                        break
                    }
                    // Timeout applies to each network completion call; user-confirmation
                    // waits (confirmAndRun) are intentionally excluded from the timeout.
                    val response = withTimeout(TURN_TIMEOUT_MS) {
                        client.completion(config, requestMessages.toList(), toolSchemas)
                    }

                    if (response.toolCalls.isEmpty()) {
                        // A blank completion with no tool call means the model produced nothing
                        // usable — e.g. a route that "reasons" about a tool call but never emits the
                        // structured call. Do NOT fabricate a success ("Done."): nudge once to make
                        // it act or answer, then surface an honest error if it still returns nothing.
                        if (response.text.isBlank()) {
                            if (!nudged) {
                                nudged = true
                                requestMessages.add(ChatRequestMessage(role = "user", content = EMPTY_RESPONSE_NUDGE))
                                continue
                            }
                            requestMessages.clear()
                            systemSeeded = false
                            lastReferenceMessage = null
                            _state.value = CoachState.Error(
                                history.toList(),
                                failureMessage("The AI didn't complete that action — please try again."),
                            )
                            break
                        }
                        val text = response.text
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
                        pushThinking(toolStatusText(call.name))
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
                _state.value = CoachState.Error(history.toList(), failureMessage("Took too long — try again."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requestMessages.clear()
                systemSeeded = false
                lastReferenceMessage = null
                _state.value = CoachState.Error(history.toList(), failureMessage("Something went wrong — try again."))
            }
        }
    }

    private suspend fun confirmAndRun(call: ParsedToolCall): String {
        // For log_meal, describe what will ACTUALLY be written (library-resolved food/macros,
        // planned-vs-logged date) rather than the model's raw args (review P1-9); other tools use the
        // static description. Null from the adapter → fall back to the static text.
        val displayText = (if (call.name == "log_meal") tools.describeLoggedMeal(call.arguments) else null)
            ?: pendingActionDisplayText(call.name, call.arguments)
        val action = PendingCoachAction(
            toolName = call.name,
            args = call.arguments,
            displayText = displayText,
        )
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmation.set(deferred)
        _state.value = CoachState.AwaitingConfirmation(history.toList(), action)
        val confirmed = deferred.await()
        pendingConfirmation.compareAndSet(deferred, null)
        if (!confirmed) return """{"cancelled":true}"""
        pushThinking(toolStatusText(call.name))
        val result = tools.execute(call.name, call.arguments)
        // Executor writes return {"success":true,...} only when the row actually persisted (errors
        // and disambiguation prompts do not). Record it so a later failure can say the data is saved.
        if (result.contains("\"success\":true")) committedWrites.add(writeNoun(call.name))
        return result
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

    // Running process log for the current turn (thinking + tool steps). Safe as a single field
    // because turnLock serialises turns; cleared at the start of each turn.
    private val thinkingSteps = mutableListOf<String>()

    // Short nouns for the writes that were CONFIRMED and actually persisted this turn (review P2-1).
    // Same single-field-under-turnLock safety as thinkingSteps; cleared at the start of each turn.
    // Consulted only on the error paths: if a write already committed, the failure message must say
    // the data is saved rather than a bare "try again" (which invites re-issuing → double-logging).
    private val committedWrites = mutableListOf<String>()

    /**
     * The user-facing message for a turn that failed. When a write already persisted this turn,
     * reassure the user it is saved so they don't re-send the command; otherwise use [base].
     */
    private fun failureMessage(base: String): String =
        if (committedWrites.isEmpty()) {
            base
        } else {
            "I already saved your ${committedWrites.distinct().joinToString(", ")} — no need to send " +
                "that again. Something went wrong finishing my reply."
        }

    /** A short noun for a persisted write tool, used in [failureMessage]. */
    private fun writeNoun(toolName: String): String = when (toolName) {
        "log_meal" -> "meal"
        "log_metric" -> "metric"
        "update_calorie_target" -> "calorie target"
        "create_routine", "edit_routine" -> "routine"
        "create_exercise" -> "exercise"
        "delete_meal", "edit_meal" -> "meal change"
        else -> "change"
    }

    /** Appends a process step (dedupes consecutive duplicates) and emits the updated Thinking state. */
    private fun pushThinking(label: String) {
        if (thinkingSteps.lastOrNull() != label) thinkingSteps.add(label)
        _state.value = CoachState.Thinking(history.toList(), thinkingSteps.toList())
    }

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
        "get_training_summary" -> "Reading your training…"
        "get_body_trends" -> "Reading your body trends…"
        "log_meal" -> "Logging meal…"
        "log_metric" -> "Saving metric…"
        "update_calorie_target" -> "Updating calorie target…"
        "search_web" -> "Searching the web…"
        "get_routines" -> "Reading your routines…"
        "search_exercises" -> "Searching exercises…"
        "create_routine" -> "Creating routine…"
        "edit_routine" -> "Updating routine…"
        "create_exercise" -> "Creating exercise…"
        "delete_meal" -> "Removing meal…"
        "edit_meal" -> "Updating meal…"
        "remember" -> "Updating memory…"
        "forget" -> "Updating memory…"
        "suggest_meals" -> "Planning your meals…"
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
            "create_routine", "edit_routine", "create_exercise" -> routineActionSummary(toolName, args)
            "delete_meal", "edit_meal" -> mealEditActionSummary(toolName, args)
            else -> toolName
        }

    private companion object {
        private const val MAX_TOOL_ROUNDS = 12
        private const val TURN_TIMEOUT_MS = 180_000L

        /** One-shot prompt sent when a completion returns no tool call and no text, to recover the turn. */
        private const val EMPTY_RESPONSE_NUDGE =
            "You returned nothing. If you intended to log food, save a metric, or update a target, " +
                "call the matching tool now. Otherwise, reply to the user's last message in one or two sentences."
    }
}

internal fun routineActionSummary(toolName: String, args: Map<String, String>): String = when (toolName) {
    "create_routine" -> {
        val name = args["name"].orEmpty()
        val ex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["exercises"].orEmpty())
            .map { it.groupValues[1] }.toList()
        "Create routine \"$name\"" + if (ex.isNotEmpty()) " — ${ex.joinToString(", ")}" else ""
    }
    "edit_routine" -> {
        val parts = buildList {
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["add"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("add ${it.joinToString(", ")}") }
            Regex("\"([^\"]+)\"").findAll(args["remove"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("remove ${it.joinToString(", ")}") }
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["retarget"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("retarget ${it.joinToString(", ")}") }
            args["new_name"]?.takeIf { it.isNotBlank() }?.let { add("rename to \"$it\"") }
        }
        "Edit \"${args["name"].orEmpty()}\"" + if (parts.isNotEmpty()) " — ${parts.joinToString("; ")}" else ""
    }
    "create_exercise" -> {
        val muscles = Regex("\"([^\"]+)\"").findAll(args["primary_muscles"].orEmpty())
            .map { it.groupValues[1] }.toList()
        "Create custom exercise \"${args["name"].orEmpty()}\"" + if (muscles.isNotEmpty()) " (${muscles.joinToString(", ")})" else ""
    }
    else -> toolName
}

internal fun mealEditActionSummary(toolName: String, args: Map<String, String>): String = when (toolName) {
    "delete_meal" -> "Delete \"${args["name"].orEmpty()}\" from ${args["date"] ?: "today"}'s log"
    "edit_meal" -> buildString {
        append("Change \"${args["name"].orEmpty()}\"")
        args["grams"]?.toDoubleOrNull()?.let { append(" to ${it.toInt()} g") }
        args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }?.let { append(" ($it kcal)") }
    }
    else -> toolName
}
