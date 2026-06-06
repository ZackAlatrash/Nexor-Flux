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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Drives the multi-turn AI coach conversation on top of LiteRT-LM.
 *
 * Tool calling is handled manually (automaticToolCalling = false): the engine surfaces
 * [com.google.ai.edge.litertlm.ToolCall]s on the returned [Message], this coordinator runs them
 * through [CoachToolExecutor], and feeds the JSON results back as [Content.ToolResponse] until the
 * model produces a plain-text answer.
 */
class RealCoachCoordinator(
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

    init {
        scope.launch {
            insightCoordinator.state.collect { aiState ->
                val available = aiState is AiInsightState.ModelReady ||
                    aiState is AiInsightState.LoadingModel ||
                    aiState is AiInsightState.Generating ||
                    aiState is AiInsightState.Ready ||
                    aiState is AiInsightState.Error
                if (!available) {
                    _state.value = CoachState.Unavailable
                    clearConversation()
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
        mutableHistory.clear()
        clearConversation()
        turnCount = 0
        if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
    }

    private suspend fun handleMessage(userText: String) {
        mutableHistory.add(ChatMessage(Role.User, userText))
        _state.value = CoachState.Thinking(mutableHistory.toList())
        try {
            val service = serviceHolder.getOrCreateService()
            withContext(Dispatchers.IO) { service.initialize() }

            if (turnCount >= MAX_TURNS) {
                clearConversation()
                turnCount = 0
            }
            val conv = conversation ?: createConversation(service).also { conversation = it }

            var response = withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.IO) { conv.sendMessage(userText) }
            }

            var toolIterations = 0
            while (toolIterations < MAX_TOOL_ITERATIONS) {
                val toolCall = response.toolCalls.firstOrNull() ?: break
                toolIterations++
                _state.value = CoachState.Thinking(
                    mutableHistory.toList(),
                    toolStatus = toolStatusText(toolCall.name),
                )
                val argsMap = toolCall.arguments.mapValues { (_, value) -> value.toString() }
                val toolResult = withContext(Dispatchers.IO) {
                    toolExecutor.execute(toolCall.name, argsMap)
                }
                val toolResponse = Contents.of(Content.ToolResponse(toolCall.name, toolResult))
                response = withTimeout(TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { conv.sendMessage(toolResponse) }
                }
            }
            if (toolIterations >= MAX_TOOL_ITERATIONS && response.toolCalls.isNotEmpty()) {
                clearConversation()
                turnCount = 0
                _state.value = CoachState.Error(mutableHistory.toList(), "Something went wrong — try again.")
                return
            }

            val fullText = extractText(response)
            val words = fullText.split(" ")
            val sb = StringBuilder()
            for (word in words) {
                sb.append(if (sb.isEmpty()) word else " $word")
                _state.value = CoachState.Responding(mutableHistory.toList(), partial = sb.toString())
                delay(STREAM_DELAY_MS)
            }
            val finalText = sb.toString()
            mutableHistory.add(ChatMessage(Role.Assistant, finalText))
            turnCount++
            _state.value = CoachState.Idle(mutableHistory.toList())
        } catch (e: TimeoutCancellationException) {
            clearConversation()
            turnCount = 0
            _state.value = CoachState.Error(mutableHistory.toList(), "Took too long — try again.")
        } catch (e: Exception) {
            clearConversation()
            turnCount = 0
            _state.value = CoachState.Error(mutableHistory.toList(), "Something went wrong — try again.")
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

    private fun buildSystemPrompt(prefs: PlanPreferences, today: java.time.LocalDate): String = buildString {
        val yesterday = today.minusDays(1)
        appendLine("You are a nutrition coach in a body recomposition tracking app.")
        val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        appendLine("Today: $today ($dayName) | Yesterday: $yesterday")
        appendLine()
        appendLine("Plan: ${prefs.targetCalories} kcal | P ${prefs.targetProteinG}g | C ${prefs.targetCarbsG}g | F ${prefs.targetFatG}g")
        appendLine()
        appendLine("Tools:")
        appendLine("- get_today_summary(date?) — food log + totals for a day. date=YYYY-MM-DD, default=today")
        appendLine("- get_weekly_trends() — last 7 days of calories")
        appendLine("- get_plan() — current targets")
        appendLine("- log_meal(name, calories) — add food to today's log")
        appendLine("- log_metric(metric, value) — metric: weight_kg|waist_cm|sleep_hours|energy_score|hunger_score|soreness_score")
        appendLine("- update_calorie_target(target_calories) — change daily calorie goal")
        appendLine()
        appendLine("Rules:")
        appendLine("1. Be concise: 1–3 sentences unless detail is asked for.")
        appendLine("2. Always call a tool before quoting any numbers — never guess.")
        appendLine("3. Today = get_today_summary() (no date). Yesterday = get_today_summary(date=\"$yesterday\"). Named days = compute the date and call get_today_summary.")
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

    private companion object {
        private const val TIMEOUT_MS = 45_000L
        private const val MAX_TOOL_ITERATIONS = 5
        private const val MAX_TURNS = 20
        private const val STREAM_DELAY_MS = 35L

        /**
         * Tool schemas advertised to the model. Parameter names match the keys read by
         * [CoachToolExecutor]. [SchemaTool.execute] is never invoked because tool calls are
         * dispatched manually through [CoachToolExecutor]; only the schema is used.
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
                """{"name":"log_meal","description":"Add a meal to today's food log.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Food name or description"},"calories":{"type":"integer","description":"Calories in kcal"}},"required":["name","calories"]}}""",
            ),
            SchemaTool(
                """{"name":"log_metric","description":"Record a body or recovery metric for today.","parameters":{"type":"object","properties":{"metric":{"type":"string","description":"One of: weight_kg, waist_cm, sleep_hours, energy_score, hunger_score, soreness_score"},"value":{"type":"number","description":"The numeric value to record"}},"required":["metric","value"]}}""",
            ),
            SchemaTool(
                """{"name":"update_calorie_target","description":"Update the daily calorie target.","parameters":{"type":"object","properties":{"target_calories":{"type":"integer"}},"required":["target_calories"]}}""",
            ),
        )
    }
}

/**
 * Minimal [OpenApiTool] that only advertises a JSON schema. Used with manual tool calling
 * (automaticToolCalling = false), where the engine never calls [execute]; dispatch is handled
 * by [CoachToolExecutor] instead.
 */
private class SchemaTool(private val schemaJson: String) : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = schemaJson
    override fun execute(args: String): String =
        error("SchemaTool.execute should not be called — tool calls are dispatched manually.")
}
