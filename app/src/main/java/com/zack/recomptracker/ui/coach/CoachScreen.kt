package com.zack.recomptracker.ui.coach

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.ChatMessage
import com.zack.recomptracker.ai.CoachState
import com.zack.recomptracker.ai.PendingCoachAction
import com.zack.recomptracker.ai.Role
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.component.MarkdownText
import com.zack.recomptracker.ui.component.ScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

private val suggestions = listOf(
    "How are my calories this week?",
    "Log 150g chicken breast to lunch",
    "Should I change my target?",
)

@Composable
fun CoachScreen(viewModel: CoachViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val history = when (val s = state) {
        is CoachState.Idle -> s.history
        is CoachState.Thinking -> s.history
        is CoachState.Responding -> s.history
        is CoachState.Error -> s.history
        is CoachState.AwaitingConfirmation -> s.history
        else -> emptyList()
    }

    val available = state !is CoachState.Unavailable
    val busy = state is CoachState.Thinking ||
        state is CoachState.Responding ||
        state is CoachState.AwaitingConfirmation
    val canSend = available && !busy && inputText.isNotBlank()
    val canClear = state is CoachState.Idle || state is CoachState.Error

    fun sendCurrent() {
        if (canSend) {
            viewModel.sendMessage(inputText.trim())
            inputText = ""
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header
        ScreenHeader(
            title = "Coach",
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                AiBadge()
                IconButton(
                    onClick = {
                        viewModel.clearHistory()
                        inputText = ""
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    enabled = canClear,
                    modifier = Modifier.semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = "Clear conversation"
                        stateDescription = if (canClear) "Available" else "Disabled"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = if (canClear) appColors.textPrimary.copy(alpha = 0.7f) else appColors.textPrimary.copy(alpha = 0.2f),
                    )
                }
            },
        )

        // Main content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (val s = state) {
                is CoachState.Unavailable -> UnavailableContent()
                is CoachState.Ready -> ReadyContent(onSuggestion = { suggestion ->
                    viewModel.sendMessage(suggestion)
                })
                is CoachState.Idle -> ChatContent(
                    history = s.history,
                    thinkingSteps = emptyList(),
                    partialResponse = null,
                    errorMessage = null,
                )
                is CoachState.Thinking -> ChatContent(
                    history = s.history,
                    thinkingSteps = s.steps,
                    partialResponse = null,
                    errorMessage = null,
                )
                is CoachState.Responding -> ChatContent(
                    history = s.history,
                    thinkingSteps = emptyList(),
                    partialResponse = s.partial,
                    errorMessage = null,
                )
                is CoachState.Error -> ChatContent(
                    history = s.history,
                    thinkingSteps = emptyList(),
                    partialResponse = null,
                    errorMessage = s.message,
                )
                is CoachState.AwaitingConfirmation -> ChatContent(
                    history = s.history,
                    thinkingSteps = emptyList(),
                    partialResponse = null,
                    errorMessage = null,
                )
            }
        }

        val awaitingConfirmation = state as? CoachState.AwaitingConfirmation
        if (awaitingConfirmation != null) {
            ConfirmationBar(
                action = awaitingConfirmation.pendingAction,
                onConfirm = { viewModel.confirmPendingAction() },
                onCancel = { viewModel.cancelPendingAction() },
            )
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = FloatingNavHeight + 8.dp,
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = if (available) "Ask the coach…" else "Download model first",
                        color = appColors.textMuted,
                    )
                },
                enabled = available && !busy,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { sendCurrent() },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = appColors.textPrimary,
                    unfocusedTextColor = appColors.textPrimary,
                    disabledTextColor = appColors.textPrimary.copy(alpha = 0.3f),
                    focusedBorderColor = appColors.cardBorder,
                    unfocusedBorderColor = appColors.cardBorder,
                    disabledBorderColor = appColors.cardBorder.copy(alpha = 0.4f),
                    focusedContainerColor = accent.tintedSurface,
                    unfocusedContainerColor = accent.tintedSurface,
                    disabledContainerColor = accent.tintedSurface.copy(alpha = 0.5f),
                    cursorColor = appColors.textPrimary,
                ),
            )
            IconButton(
                onClick = { sendCurrent() },
                enabled = canSend,
                modifier = Modifier.semantics {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = "Send message"
                    stateDescription = if (canSend) "Ready" else "Disabled"
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (canSend) appColors.textPrimary else appColors.textPrimary.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun UnavailableContent() {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AiInsightCard(borderMode = AiBorderMode.Static) {
            AiBadge()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI Coach",
                color = appColors.textPrimary,
                style = AppType.cardTitle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Download the AI model in More → AI Model to unlock the coach.",
                color = appColors.textMuted,
                style = AppType.body,
            )
        }
    }
}

@Composable
private fun ReadyContent(onSuggestion: (String) -> Unit) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What would you like to know?",
            color = appColors.textMuted,
            style = AppType.cardTitle,
        )
        Spacer(modifier = Modifier.height(16.dp))
        suggestions.forEach { suggestion ->
            FilterChip(
                selected = false,
                onClick = { onSuggestion(suggestion) },
                label = {
                    Text(
                        text = suggestion,
                        color = appColors.textPrimary,
                        style = AppType.body,
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .widthIn(max = 320.dp)
                    .semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = "Suggested prompt"
                        stateDescription = suggestion
                    },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = accent.tintedSurface,
                    selectedContainerColor = accent.tintedSurface,
                    labelColor = appColors.textPrimary,
                ),
                border = BorderStroke(1.dp, accent.tintedBorder),
            )
        }
    }
}

@Composable
private fun ChatContent(
    history: List<ChatMessage>,
    thinkingSteps: List<String>,
    partialResponse: String?,
    errorMessage: String?,
) {
    val listState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val totalItems = history.size +
        (if (thinkingSteps.isNotEmpty() || partialResponse != null) 1 else 0) +
        (if (errorMessage != null) 1 else 0)

    // Key on totalItems so the scroll also fires when the thinking/error item appears,
    // not just when a committed message is added. Target the last item in the list.
    LaunchedEffect(totalItems) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = 8.dp,
        ),
    ) {
        items(
            items = history,
            // Stable per-message id (assigned at construction, never reused) so scrolling only
            // recomposes changed rows instead of re-recording every bubble. contentType lets the
            // list reuse layout nodes within a role (user Surface vs. assistant glass card).
            key = { it.id },
            contentType = { it.role },
        ) { message ->
            // Settled history bubbles use the cheap `lite` glass look-alike: no per-frame
            // drawBackdrop offscreen layer for the ~N assistant bubbles scrolling behind the
            // focal live message. The live streaming item below keeps full glass (lite = false).
            ChatBubble(message = message, lite = true)
        }

        // Single live in-progress item: prefers streaming response, falls back to thinking.
        // Keyed by a stable id so updates don't recreate the card on every token.
        if (partialResponse != null) {
            item(key = "live-response") {
                val liveCols = LocalAppColors.current
                AiInsightCard(
                    borderMode = AiBorderMode.Generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MarkdownText(
                        text = partialResponse,
                        color = liveCols.textPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else if (thinkingSteps.isNotEmpty()) {
            item(key = "live-thinking") {
                // Expanded loading card showing the coach's live process (thinking + tool steps).
                AiInsightCard(
                    borderMode = AiBorderMode.Generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ThinkingProcess(steps = thinkingSteps)
                }
            }
        }

        if (errorMessage != null) {
            item(key = "live-error") {
                AiInsightCard(
                    borderMode = AiBorderMode.Static,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        style = AppType.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationBar(
    action: PendingCoachAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val confirmCols = LocalAppColors.current
        AiInsightCard(
            borderMode = AiBorderMode.Preparing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = action.displayText,
                color = confirmCols.textPrimary,
                style = AppType.body,
            )
        }
        // No bottom padding here — the input row directly below already reserves
        // FloatingNavHeight + 8dp for the floating nav bar, so adding it again
        // would double-pad and push the buttons too far up.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onCancel()
                },
                label = {
                    Text(
                        text = "Cancel",
                        color = androidx.compose.ui.graphics.Color(0xFFEF5350),
                        style = AppType.body,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = accent.tintedSurface,
                    selectedContainerColor = accent.tintedSurface,
                ),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
            )
            FilterChip(
                selected = false,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onConfirm()
                },
                label = {
                    Text(
                        text = "Confirm",
                        color = androidx.compose.ui.graphics.Color(0xFF66BB6A),
                        style = AppType.body,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = accent.tintedSurface,
                    selectedContainerColor = accent.tintedSurface,
                ),
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF66BB6A).copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun ThinkingDots() {
    val accent = LocalAppAccent.current
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200),
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(accent.accentLighter.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

/** The coach's live process log: each thinking/tool step in order, the last one active. */
@Composable
private fun ThinkingProcess(steps: List<String>) {
    val cols = LocalAppColors.current
    val accent = LocalAppAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, step ->
            val active = i == steps.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (active) "›" else "✓",
                    color = if (active) accent.inkLight else accent.inkLight.copy(alpha = 0.6f),
                    style = AppType.body,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = step,
                    color = if (active) cols.textPrimary else cols.textMuted,
                    style = AppType.body.copy(
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    ThinkingDots()
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, lite: Boolean = false) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val isUser = message.role == Role.User

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 4.dp,
                    bottomEnd = 16.dp,
                    bottomStart = 16.dp,
                ),
                color = accent.tintedSurface,
                border = BorderStroke(1.dp, appColors.cardBorder),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.text,
                    color = appColors.textPrimary,
                    style = AppType.body,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        } else {
            AiInsightCard(
                borderMode = AiBorderMode.Static,
                modifier = Modifier.widthIn(max = 300.dp),
                lite = lite,
            ) {
                MarkdownText(
                    text = message.text,
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
