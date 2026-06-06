package com.zack.recomptracker.ai

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Drives the multi-turn AI coach conversation on top of LiteRT-LM.
 *
 * Tool calling is handled manually (automaticToolCalling = false): the engine surfaces
 * [com.google.ai.edge.litertlm.ToolCall]s on the returned [Message], this coordinator
 * runs them through [CoachToolExecutor], and feeds the JSON results back as
 * [Content.ToolResponse] until the model produces a plain-text answer.
 *
 * Concurrency model
 * ─────────────────
 * [turnLock] serialises the coach's own turns (one user message at a time).
 * [GemmaInsightService.withInferenceLock] is acquired inside each turn for ALL
 * [Conversation.sendMessage] calls so the Engine is never used concurrently with the
 * insight flow. The lock is released before the response is emitted to the UI.
 */
class GemmaCoachCoordinator(
    private val serviceHolder: GemmaServiceHolder,
    private val insightCoordinator: AiInsightCoordinator,
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val scope: CoroutineScope,
) : CoachCoordinator {

    private val _state = MutableStateFlow<CoachState>(CoachState.Unavailable)
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    private val mutableHistory = mutableListOf<ChatMessage>()
    private var conversation: Conversation? = null
    private var turnCount = 0
    private var conversationDate: java.time.LocalDate? = null
    private val turnLock = Mutex()

    @Volatile private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    init {
        scope.launch {
            insightCoordinator.state.collect { aiState ->
                val available = aiState is AiInsightState.ModelReady ||
                    aiState is AiInsightState.ModelVerifying ||
                    aiState is AiInsightState.LoadingModel ||
                    aiState is AiInsightState.Generating ||
                    aiState is AiInsightState.Ready ||
                    aiState is AiInsightState.Error
                if (!available) {
                    _state.value = CoachState.Unavailable
                    // Dispatch through turnLock so we wait for any in-progress turn to finish
                    // before closing the Conversation object (Issue 6).
                    scope.launch { turnLock.withLock { clearConversation() } }
                } else if (_state.value == CoachState.Unavailable) {
                    _state.value = if (mutableHistory.isEmpty()) {
                        CoachState.Ready
                    } else {
                        CoachState.Idle(mutableHistory.toList())
                    }
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
        mutableHistory.clear()
        clearConversation()
        turnCount = 0
        conversationDate = null
        if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
    }

    override fun confirmPendingAction() {
        pendingConfirmation?.complete(true)
    }

    override fun cancelPendingAction() {
        pendingConfirmation?.complete(false)
    }

    private suspend fun handleMessage(userText: String) {
        turnLock.withLock {
            // Issue 5: check for a day change BEFORE adding the user's message so the
            // "New day" notification appears above their first message of the new day,
            // not sandwiched between it and the assistant's reply.
            val today = dateProvider.today()
            if (conversationDate != null && conversationDate != today) {
                clearConversation()
                turnCount = 0
                mutableHistory.add(
                    ChatMessage(Role.Assistant, "— New day started. Context has been refreshed. —"),
                )
            }
            conversationDate = today

            mutableHistory.add(ChatMessage(Role.User, userText))
            _state.value = CoachState.Thinking(mutableHistory.toList())
            try {
                val service = serviceHolder.getOrCreateService()
                // Fast path if already initialised; blocks only on first call.
                service.ensureInitialized()

                // Issue 3: notify the user before silently losing conversation context.
                // MAX_TURNS = 20 at 45 s/turn ≈ 15 min of chat — a reasonable boundary.
                if (turnCount >= MAX_TURNS) {
                    mutableHistory.add(
                        ChatMessage(Role.Assistant, "— Context window reset. The coach has started fresh. —"),
                    )
                    clearConversation()
                    turnCount = 0
                }
                val conv =
                    conversation ?: createConversation(service).also { conversation = it }

                // Acquire the shared Engine lock for ALL sendMessage calls in this turn.
                // Released before emitting the final response so the insight flow is not
                // blocked while the UI updates.
                val fullText: String? = service.withInferenceLock {
                    var response = withTimeout(TIMEOUT_MS) {
                        withContext(Dispatchers.IO) { conv.sendMessage(userText) }
                    }

                    var toolIterations = 0
                    // Issue 4: process every tool call in each response before the next
                    // sendMessage, not just the first one.
                    while (toolIterations < MAX_TOOL_ITERATIONS) {
                        val toolCalls = response.toolCalls
                        if (toolCalls.isEmpty()) break
                        toolIterations += toolCalls.size
                        val toolResponses = mutableListOf<Content.ToolResponse>()
                        for (toolCall in toolCalls) {
                            _state.value = CoachState.Thinking(
                                mutableHistory.toList(),
                                toolStatus = toolStatusText(toolCall.name),
                            )
                            val argsMap =
                                toolCall.arguments.mapValues { (_, value) -> value.toString() }
                            if (toolCall.name in WRITE_TOOLS) {
                                val action = PendingCoachAction(
                                    toolName = toolCall.name,
                                    args = argsMap,
                                    displayText = pendingActionDisplayText(toolCall.name, argsMap),
                                )
                                val deferred = CompletableDeferred<Boolean>()
                                pendingConfirmation = deferred
                                _state.value = CoachState.AwaitingConfirmation(
                                    mutableHistory.toList(),
                                    action,
                                )
                                val confirmed = deferred.await()
                                pendingConfirmation = null
                                if (!confirmed) {
                                    toolResponses.add(
                                        Content.ToolResponse(toolCall.name, """{"cancelled":true}"""),
                                    )
                                    continue
                                }
                                _state.value = CoachState.Thinking(
                                    mutableHistory.toList(),
                                    toolStatus = toolStatusText(toolCall.name),
                                )
                            }
                            val result = withContext(Dispatchers.IO) {
                                toolExecutor.execute(toolCall.name, argsMap)
                            }
                            toolResponses.add(Content.ToolResponse(toolCall.name, result))
                        }
                        response = withTimeout(TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                conv.sendMessage(Contents.of(*toolResponses.toTypedArray()))
                            }
                        }
                    }

                    if (toolIterations >= MAX_TOOL_ITERATIONS &&
                        response.toolCalls.isNotEmpty()
                    ) {
                        // Fundamental failure — clear conversation so next turn starts clean.
                        // Keep mutableHistory intact so the user message stays visible (Issue 2).
                        clearConversation()
                        turnCount = 0
                        _state.value = CoachState.Error(
                            mutableHistory.toList(),
                            "Something went wrong — try again.",
                        )
                        return@withInferenceLock null
                    }

                    val text = extractText(response)
                    if (text.isEmpty()) {
                        // Fundamental failure — same policy as above.
                        clearConversation()
                        turnCount = 0
                        _state.value = CoachState.Error(
                            mutableHistory.toList(),
                            "Something went wrong — try again.",
                        )
                        return@withInferenceLock null
                    }
                    text
                }

                // Inference lock released — emit response.
                if (fullText == null) return@withLock

                // Issue 1: the model has already finished; emit the full text at once rather
                // than adding an artificial per-word delay.
                _state.value = CoachState.Responding(mutableHistory.toList(), partial = fullText)
                mutableHistory.add(ChatMessage(Role.Assistant, fullText))
                turnCount++
                _state.value = CoachState.Idle(mutableHistory.toList())
            } catch (e: TimeoutCancellationException) {
                // Issue 2: transient failure — keep the user message visible and leave the
                // Conversation object alive so the next message can continue in context.
                _state.value =
                    CoachState.Error(mutableHistory.toList(), "Took too long — try again.")
            } catch (e: CancellationException) {
                // Genuine scope cancellation (app closing, etc.) — rethrow.
                throw e
            } catch (e: Exception) {
                // Unknown exception — the Conversation object may be in a corrupted or
                // illegal state (e.g. a LiteRT-LM native error). Clear it so the next
                // message starts from a known-good state instead of repeatedly failing.
                // History is kept intact; the user's message stays visible.
                clearConversation()
                turnCount = 0
                _state.value =
                    CoachState.Error(mutableHistory.toList(), "Something went wrong — try again.")
            }
        }
    }

    private suspend fun createConversation(service: GemmaInsightService): Conversation {
        val toolProviders: List<ToolProvider> = COACH_TOOLS.map { tool(it) }
        val prefs = planRepository.preferences.first()
        val today = dateProvider.today()
        val config = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt(prefs, today)),
            tools = toolProviders,
            automaticToolCalling = false,
        )
        return service.createConversation(config)
    }

    private fun extractText(response: Message): String =
        response.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
            .trim()

    private fun buildSystemPrompt(
        prefs: PlanPreferences,
        today: java.time.LocalDate,
    ): String = buildString {
        val yesterday = today.minusDays(1)
        appendLine("You are a nutrition coach in a body recomposition tracking app.")
        val dayName =
            today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        appendLine("Today: $today ($dayName) | Yesterday: $yesterday")
        appendLine()
        appendLine(
            "Plan: ${prefs.targetCalories} kcal | P ${prefs.targetProteinG}g" +
                " | C ${prefs.targetCarbsG}g | F ${prefs.targetFatG}g",
        )
        appendLine()
        // Issue 7: tool definitions are provided via ConversationConfig.tools (formal schemas).
        // Duplicating them as plain text confuses the 2B model into using the wrong call format.
        appendLine("Rules:")
        appendLine("1. Be concise: 1–3 sentences unless detail is asked for.")
        appendLine("2. Always call a tool before quoting any numbers — never guess.")
        appendLine(
            "3. Today = get_today_summary() (no date). " +
                "Yesterday = get_today_summary(date=\"$yesterday\"). " +
                "Named days = compute the date and call get_today_summary.",
        )
        appendLine("4. Before any write tool: state what you'll log and wait for the user to confirm.")
        append("5. Stay on topic: nutrition, body composition, training, recovery only.")
    }

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
        "get_plan" -> "Reading your plan…"
        "log_meal" -> "Logging meal…"
        "log_metric" -> "Saving metric…"
        "update_calorie_target" -> "Updating calorie target…"
        else -> "Running tool…"
    }

    private fun clearConversation() {
        conversation?.close()
        conversation = null
    }

    private fun pendingActionDisplayText(toolName: String, args: Map<String, String>): String =
        when (toolName) {
            "log_meal" -> buildString {
                append("Log ${args["name"]} (${args["calories"]} kcal)")
                val type = args["meal_type"]
                if (!type.isNullOrBlank()) append(" as $type")
                append(" to today's food log")
            }
            "log_metric" -> "Save ${args["metric"]} = ${args["value"]}"
            "update_calorie_target" ->
                "Update daily calorie target to ${args["target_calories"]} kcal"
            else -> toolName
        }

    private companion object {
        private const val TIMEOUT_MS = 45_000L
        private const val MAX_TOOL_ITERATIONS = 5
        private const val MAX_TURNS = 20

        val WRITE_TOOLS = setOf("log_meal", "log_metric", "update_calorie_target")

        /**
         * Tool schemas advertised to the model. Parameter names match the keys read by
         * [CoachToolExecutor]. [SchemaTool.execute] is never invoked because tool calls
         * are dispatched manually through [CoachToolExecutor]; only the schema is used.
         */
        val COACH_TOOLS: List<OpenApiTool> = listOf(
            SchemaTool(
                """{"name":"get_today_summary","description":"Get a specific day's food log, macro totals, and daily metrics. Omit 'date' for today.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"ISO date YYYY-MM-DD. Omit for today."}},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"get_weekly_trends","description":"Get last 7 days calorie history and adherence.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"get_plan","description":"Get the user's current calorie and macro targets.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"log_meal","description":"Add a meal to today's food log.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name or description"},"calories":{"type":"integer","description":"Calories in kcal"},"meal_type":{"type":"string","description":"One of: Breakfast, Lunch, Dinner, Snack. Default: Snack"},"protein_g":{"type":"number","description":"Protein in grams"},"carbs_g":{"type":"number","description":"Carbohydrates in grams"},"fat_g":{"type":"number","description":"Fat in grams"}},"required":["name","calories"]}}""",
            ),
            SchemaTool(
                """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
            ),
            SchemaTool(
                """{"name":"update_calorie_target","description":"Update the daily calorie target. Value must be between 500 and 6000.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer","description":"New daily calorie target in kcal (500–6000)"}},"required":["target_calories"]}}""",
            ),
        )
    }
}

/**
 * Minimal [OpenApiTool] that only advertises a JSON schema. Used with manual tool calling
 * (automaticToolCalling = false), where the engine never calls [execute]; dispatch is
 * handled by [CoachToolExecutor] instead.
 */
private class SchemaTool(private val schemaJson: String) : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = schemaJson
    override fun execute(args: String): String =
        error("SchemaTool.execute should not be called — tool calls are dispatched manually.")
}
