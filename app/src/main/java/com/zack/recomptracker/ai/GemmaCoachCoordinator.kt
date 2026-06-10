package com.zack.recomptracker.ai

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.preferences.displayName
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
 * Tool schemas shared by both the Gemma and cloud coaches. Each entry is a raw JSON object
 * `{"name":..,"description":..,"parameters":..}`. The Gemma path wraps these in SchemaTool;
 * the cloud path sends them as OpenAI `tools` entries.
 */
val COACH_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"get_today_summary","description":"Get a specific day's food log, macro totals, and daily metrics. Omit 'date' for today.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"ISO date YYYY-MM-DD. Omit for today."}},"required":[]}}""",
    """{"name":"get_weekly_trends","description":"Get last 7 days of daily macro totals (calories, protein, carbs, fat) and adherence percent. Use this for weekly trends or any multi-day macro question.","parameters":{"type":"object","properties":{},"required":[]}}""",
    """{"name":"search_food_library","description":"Search your saved food library by name. If the user specified a weight in grams, pass it as 'grams' and the tool returns macros already scaled to that weight — use those directly in log_meal.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Food name only — no quantities or weights"},"grams":{"type":"number","description":"Optional: weight in grams requested by the user. If provided, returned macros are pre-scaled to this weight."}},"required":["query"]}}""",
    """{"name":"log_meal","description":"Add a meal to today's food log. The tool looks up your food library automatically and uses the correct macros. Pass grams if the user specified a weight. If the food is NOT in the library, you MUST also provide calories, protein_g, carbs_g, and fat_g.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name"},"grams":{"type":"number","description":"Optional: weight in grams. Macros are scaled automatically if food is in library."},"meal_type":{"type":"string","description":"One of: Breakfast, Lunch, Dinner, Snack. Default: Snack"},"calories":{"type":"integer","description":"Required only if food is NOT in your library. Omit for library foods."},"protein_g":{"type":"number","description":"Required only if food is NOT in your library."},"carbs_g":{"type":"number","description":"Required only if food is NOT in your library."},"fat_g":{"type":"number","description":"Required only if food is NOT in your library."}},"required":["name"]}}""",
    """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
    """{"name":"update_calorie_target","description":"Update the daily calorie target. Value must be between 500 and 6000.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer","description":"New daily calorie target in kcal (500–6000)"}},"required":["target_calories"]}}""",
)

/** Tool names that mutate user data and therefore require explicit confirmation. */
val COACH_WRITE_TOOLS: Set<String> = setOf("log_meal", "log_metric", "update_calorie_target")

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
    private val userProfileStore: UserProfilePreferencesStore,
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
            _state.value = CoachState.Thinking(mutableHistory.toList(), toolStatus = "Thinking…")
            try {
                val modelPath = serviceHolder.modelFileFor(insightCoordinator.selectedModel.value).absolutePath
                val service = serviceHolder.getOrCreateService(modelPath)
                if (!service.isInitialized) {
                    _state.value = CoachState.Thinking(mutableHistory.toList(), toolStatus = "Loading AI engine…")
                    service.ensureInitialized()
                    _state.value = CoachState.Thinking(mutableHistory.toList(), toolStatus = "Thinking…")
                } else {
                    service.ensureInitialized()
                }

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

                // Acquire the Engine lock per sendMessage call, not for the entire turn.
                // This lets the insight flow run freely while we await user confirmation
                // between tool calls — previously a single withInferenceLock block held
                // the lock across the entire confirmation wait.
                var response = service.withInferenceLock {
                    withTimeout(TIMEOUT_MS) {
                        withContext(Dispatchers.IO) { conv.sendMessage(userText) }
                    }
                }

                Log.d("RecompCoach", "model_resp: toolCalls=${response.toolCalls.size} text='${extractText(response).take(120)}'")

                var toolIterations = 0
                // Issue 4: process every tool call in each response before the next
                // sendMessage, not just the first one.
                while (toolIterations < MAX_TOOL_ITERATIONS) {
                    val toolCalls = response.toolCalls
                    if (toolCalls.isEmpty()) break
                    toolIterations += toolCalls.size
                    val toolResponses = mutableListOf<Content.ToolResponse>()
                    for (toolCall in toolCalls) {
                        Log.d("RecompCoach", "tool_call: name=${toolCall.name} args=${toolCall.arguments}")
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
                            // inferenceLock is NOT held here — the insight flow is free
                            // to run while the user reads and responds to the dialog.
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
                        Log.d("RecompCoach", "tool_result: name=${toolCall.name} result='${result.take(300)}'")
                        toolResponses.add(Content.ToolResponse(toolCall.name, result))
                    }
                    // Re-acquire lock for the next model call.
                    response = service.withInferenceLock {
                        withTimeout(TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                conv.sendMessage(Contents.of(*toolResponses.toTypedArray()))
                            }
                        }
                    }
                }

                if (toolIterations >= MAX_TOOL_ITERATIONS &&
                    response.toolCalls.isNotEmpty()
                ) {
                    // Fundamental failure — clear conversation so next turn starts clean.
                    // Keep mutableHistory intact so the user message stays visible.
                    clearConversation()
                    turnCount = 0
                    _state.value = CoachState.Error(
                        mutableHistory.toList(),
                        "Something went wrong — try again.",
                    )
                    return@withLock
                }

                var fullText = extractText(response)
                if (fullText.isEmpty() && toolIterations > 0) {
                    // After a tool sequence the model sometimes returns no text (e.g. after
                    // search + log_meal). Prompt it once for a one-line confirmation rather
                    // than surfacing an error to the user.
                    try {
                        val nudge = service.withInferenceLock {
                            withTimeout(TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    conv.sendMessage("Confirm what you just did in one sentence.")
                                }
                            }
                        }
                        fullText = extractText(nudge)
                    } catch (_: Exception) { /* fall through to error below */ }
                }
                if (fullText.isEmpty()) {
                    // Fundamental failure — same policy as above.
                    clearConversation()
                    turnCount = 0
                    _state.value = CoachState.Error(
                        mutableHistory.toList(),
                        "Something went wrong — try again.",
                    )
                    return@withLock
                }

                // Safety net: small models often generate planning text ("I need to call X…")
                // before the tool call. That text lands in conversation history, and after the
                // tool result comes back the model echoes it instead of quoting the actual data.
                // If we detect that pattern after at least one tool ran, send a one-shot
                // correction so the model answers from the data it already retrieved.
                if (toolIterations > 0 && fullText.containsEchoPhrase()) {
                    try {
                        val retry = service.withInferenceLock {
                            withTimeout(TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    conv.sendMessage(
                                        "The tool already ran and returned data. " +
                                            "Answer the original question using ONLY the " +
                                            "numbers from the tool result. Do NOT call any tool again.",
                                    )
                                }
                            }
                        }
                        val retryText = extractText(retry)
                        if (retryText.isNotEmpty()) fullText = retryText
                    } catch (_: Exception) {
                        // Retry failed — keep the imperfect original rather than showing an error.
                    }
                }

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
        val profile = userProfileStore.preferences.first()
        val today = dateProvider.today()
        val todaySummary = withContext(Dispatchers.IO) {
            toolExecutor.execute("get_today_summary", emptyMap())
        }
        Log.d("RecompCoach", "snapshot: $todaySummary")
        val config = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt(prefs, profile, today, todaySummary)),
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

    /**
     * Returns true when the model's response looks like it echoed its pre-tool-call planning
     * text ("I need to call X…") instead of answering from the retrieved tool data.
     * Used by the echo-recovery safety net to decide whether to send a correction prompt.
     */
    private fun String.containsEchoPhrase(): Boolean {
        val lower = lowercase()
        return lower.contains("i need to call") ||
            lower.contains("i'll call") ||
            lower.contains("i have to call") ||
            lower.contains("let me call") ||
            lower.contains("i should call") ||
            lower.contains("i will call") ||
            lower.contains("need to use the") ||
            lower.contains("i need to use")
    }

    private fun buildSystemPrompt(
        prefs: PlanPreferences,
        profile: UserProfilePreferences,
        today: java.time.LocalDate,
        todaySummary: String,
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
        val profileParts = buildList {
            profile.goal?.let { add("Goal: ${it.displayName()}") }
            profile.biologicalSex?.let { add("Sex: ${it.displayName()}") }
            profile.ageYears?.let { add("Age: $it") }
            profile.heightCm?.let { add("Height: $it cm") }
            profile.activityLevel?.let { add("Activity: ${it.displayName()}") }
            profile.weeklyGymSessions?.let { add("Gym sessions/week: $it") }
        }
        if (profileParts.isNotEmpty()) {
            appendLine()
            appendLine("=== USER PROFILE ===")
            appendLine(profileParts.joinToString(" | "))
            appendLine("=== END PROFILE ===")
        }
        appendLine()
        appendLine("=== TODAY'S DATA SNAPSHOT (fetched at conversation start) ===")
        appendLine(todaySummary)
        appendLine("=== END SNAPSHOT ===")
        appendLine()
        appendLine("Rules (follow exactly):")
        appendLine(
            "1. The snapshot above contains ONLY today's ($today) data. " +
                "For READ-ONLY questions about TODAY's calories, food logged, macros, weight, " +
                "sleep, or steps: answer directly from the snapshot. " +
                "Call get_today_summary() only if the user just logged something new. " +
                "Do NOT apply this rule to logging requests — use Rule 5 for those.",
        )
        appendLine(
            "2. For any question about YESTERDAY ($yesterday) or any past date: " +
                "you MUST call get_today_summary(date=\"YYYY-MM-DD\"). " +
                "Do NOT use the snapshot — it contains today's data only. " +
                "Example for yesterday: get_today_summary(date=\"$yesterday\").",
        )
        appendLine(
            "3. Call get_weekly_trends() for weekly questions, multi-day macro questions, " +
                "or adherence questions.",
        )
        appendLine(
            "4. After a tool returns, reply in 1–3 sentences using only the numbers from the JSON. " +
                "Do not guess or invent numbers. Do not add information not present in the result.",
        )
        appendLine(
            "5. To log any food: call log_meal(name=..., calories=..., meal_type=...). " +
                "If the user said a weight in grams (e.g. '100g'), also pass grams=.... " +
                "The tool checks your food library automatically — do NOT call search_food_library before logging. " +
                "Use search_food_library only when the user asks to browse or search available foods.",
        )
        append("6. Stay on topic: nutrition, body composition, training, recovery only.")
    }

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
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
                """{"name":"get_weekly_trends","description":"Get last 7 days of daily macro totals (calories, protein, carbs, fat) and adherence percent. Use this for weekly trends or any multi-day macro question.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"search_food_library","description":"Search your saved food library by name. If the user specified a weight in grams, pass it as 'grams' and the tool returns macros already scaled to that weight — use those directly in log_meal.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Food name only — no quantities or weights"},"grams":{"type":"number","description":"Optional: weight in grams requested by the user. If provided, returned macros are pre-scaled to this weight."}},"required":["query"]}}""",
            ),
            SchemaTool(
                """{"name":"log_meal","description":"Add a meal to today's food log. The tool looks up your food library automatically and uses the correct macros. Pass grams if the user specified a weight. If the food is NOT in the library, you MUST also provide calories, protein_g, carbs_g, and fat_g.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name"},"grams":{"type":"number","description":"Optional: weight in grams. Macros are scaled automatically if food is in library."},"meal_type":{"type":"string","description":"One of: Breakfast, Lunch, Dinner, Snack. Default: Snack"},"calories":{"type":"integer","description":"Required only if food is NOT in your library. Omit for library foods."},"protein_g":{"type":"number","description":"Required only if food is NOT in your library."},"carbs_g":{"type":"number","description":"Required only if food is NOT in your library."},"fat_g":{"type":"number","description":"Required only if food is NOT in your library."}},"required":["name"]}}""",
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
