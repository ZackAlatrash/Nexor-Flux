package com.zack.recomptracker.ai

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val scope: CoroutineScope,
) : CoachCoordinator {

    private val _state = MutableStateFlow<CoachState>(CoachState.Unavailable)
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    private val mutableHistory = mutableListOf<ChatMessage>()
    private var conversation: Conversation? = null

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
        if (_state.value != CoachState.Unavailable) _state.value = CoachState.Ready
    }

    private suspend fun handleMessage(userText: String) {
        mutableHistory.add(ChatMessage(Role.User, userText))
        _state.value = CoachState.Thinking(mutableHistory.toList())
        try {
            val service = serviceHolder.getOrCreateService()
            withContext(Dispatchers.IO) { service.initialize() }
            val conv = conversation ?: createConversation(service).also { conversation = it }

            var response = withContext(Dispatchers.IO) { conv.sendMessage(userText) }

            // Manual tool-call loop: keep resolving tool calls until the model replies with text.
            while (true) {
                val toolCall = response.toolCalls.firstOrNull() ?: break
                _state.value = CoachState.Thinking(
                    mutableHistory.toList(),
                    toolStatus = toolStatusText(toolCall.name),
                )
                val argsMap = toolCall.arguments.mapValues { (_, value) -> value.toString() }
                val toolResult = withContext(Dispatchers.IO) {
                    toolExecutor.execute(toolCall.name, argsMap)
                }
                val toolResponse = Contents.of(Content.ToolResponse(toolCall.name, toolResult))
                response = withContext(Dispatchers.IO) { conv.sendMessage(toolResponse) }
            }

            // Stream the final text word-by-word so the UI can animate it.
            val fullText = extractText(response)
            val words = fullText.split(" ")
            val sb = StringBuilder()
            for (word in words) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(word)
                _state.value = CoachState.Responding(mutableHistory.toList(), partial = sb.toString())
            }
            val finalText = sb.toString()
            mutableHistory.add(ChatMessage(Role.Assistant, finalText))
            _state.value = CoachState.Idle(mutableHistory.toList())
        } catch (e: Exception) {
            _state.value = CoachState.Error(mutableHistory.toList(), "Something went wrong — try again.")
        }
    }

    private suspend fun createConversation(service: GemmaInsightService): Conversation {
        val toolProviders: List<ToolProvider> = COACH_TOOLS.map { tool(it) }
        val prefs = planRepository.preferences.first()
        val config = ConversationConfig(
            systemInstruction = Contents.of(buildSystemPrompt(prefs)),
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

    private fun buildSystemPrompt(prefs: PlanPreferences): String = buildString {
        appendLine("You are a personal nutrition and fitness coach embedded in a body recomposition tracking app.")
        appendLine()
        appendLine("Current plan:")
        appendLine("- Calorie target: ${prefs.targetCalories} kcal/day")
        appendLine("- Protein: ${prefs.targetProteinG}g | Carbs: ${prefs.targetCarbsG}g | Fat: ${prefs.targetFatG}g")
        appendLine()
        appendLine("Rules:")
        appendLine("1. Be concise: 1-3 sentences per response unless the user explicitly asks for detail.")
        appendLine("2. Use tools when asked for specific numbers (weight, today's food, trends) — do not guess.")
        appendLine("3. Before calling any write tool (log_meal, log_daily_metrics, update_calorie_target),")
        appendLine("   tell the user what you are about to write and wait for their confirmation.")
        appendLine("4. Never fabricate data. If unsure, say so and offer to fetch it with a tool.")
        append("5. Stay focused on nutrition, body composition, training, and recovery.")
    }

    private fun toolStatusText(name: String): String = when (name) {
        "get_today_summary" -> "Reading your food log…"
        "get_weekly_trends" -> "Reading your weekly trends…"
        "get_plan" -> "Reading your plan…"
        "log_meal" -> "Logging meal…"
        "log_daily_metrics" -> "Saving daily metrics…"
        "update_calorie_target" -> "Updating calorie target…"
        else -> "Running tool…"
    }

    private fun clearConversation() {
        conversation?.close()
        conversation = null
    }

    private companion object {
        /**
         * Tool schemas advertised to the model. Parameter names match the keys read by
         * [CoachToolExecutor]. [SchemaTool.execute] is never invoked because tool calls are
         * dispatched manually through [CoachToolExecutor]; only the schema is used.
         */
        val COACH_TOOLS: List<OpenApiTool> = listOf(
            SchemaTool(
                """{"name":"get_today_summary","description":"Get today's food log, macro totals, and daily metrics.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"get_weekly_trends","description":"Get last 7 days calorie history and adherence.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"get_plan","description":"Get the user's current calorie and macro targets.","parameters":{"type":"object","properties":{},"required":[]}}""",
            ),
            SchemaTool(
                """{"name":"log_meal","description":"Add a meal to today's food log.","parameters":{"type":"object","properties":{"name":{"type":"string"},"calories":{"type":"integer"},"protein_g":{"type":"number"},"carbs_g":{"type":"number"},"fat_g":{"type":"number"},"meal_type":{"type":"string"}},"required":["name","calories"]}}""",
            ),
            SchemaTool(
                """{"name":"log_daily_metrics","description":"Record today's body metrics (weight, waist, sleep, energy, hunger, soreness).","parameters":{"type":"object","properties":{"weight_kg":{"type":"number"},"waist_cm":{"type":"number"},"sleep_hours":{"type":"number"},"energy_score":{"type":"integer"},"hunger_score":{"type":"integer"},"soreness_score":{"type":"integer"}},"required":[]}}""",
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
