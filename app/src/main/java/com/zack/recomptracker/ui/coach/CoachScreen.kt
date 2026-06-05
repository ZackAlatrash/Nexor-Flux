package com.zack.recomptracker.ui.coach

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.ChatMessage
import com.zack.recomptracker.ai.CoachState
import com.zack.recomptracker.ai.Role
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.TintedBorder
import com.zack.recomptracker.ui.theme.TintedSurface

private val suggestions = listOf(
    "How are my calories this week?",
    "Log 150g chicken breast to lunch",
    "Should I change my target?",
)

@Composable
fun CoachScreen(viewModel: CoachViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }

    val history = when (val s = state) {
        is CoachState.Idle -> s.history
        is CoachState.Thinking -> s.history
        is CoachState.Responding -> s.history
        is CoachState.Error -> s.history
        else -> emptyList()
    }

    val available = state !is CoachState.Unavailable
    val busy = state is CoachState.Thinking || state is CoachState.Responding
    val canSend = available && !busy && inputText.isNotBlank()
    val canClear = state is CoachState.Idle || state is CoachState.Error

    fun sendCurrent() {
        if (canSend) {
            viewModel.sendMessage(inputText.trim())
            inputText = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Coach",
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            AiBadge()
            IconButton(
                onClick = {
                    viewModel.clearHistory()
                    inputText = ""
                },
                enabled = canClear,
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Clear conversation",
                    tint = if (canClear) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
                )
            }
        }

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
                    thinkingStatus = null,
                    partialResponse = null,
                    errorMessage = null,
                )
                is CoachState.Thinking -> ChatContent(
                    history = s.history,
                    thinkingStatus = s.toolStatus,
                    partialResponse = null,
                    errorMessage = null,
                )
                is CoachState.Responding -> ChatContent(
                    history = s.history,
                    thinkingStatus = null,
                    partialResponse = s.partial,
                    errorMessage = null,
                )
                is CoachState.Error -> ChatContent(
                    history = s.history,
                    thinkingStatus = null,
                    partialResponse = null,
                    errorMessage = s.message,
                )
            }
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
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (available) "Ask the coach…" else "Download model first",
                        color = TextMuted,
                    )
                },
                enabled = available && !busy,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.White.copy(alpha = 0.3f),
                    focusedBorderColor = CardBorder,
                    unfocusedBorderColor = CardBorder,
                    disabledBorderColor = CardBorder.copy(alpha = 0.4f),
                    focusedContainerColor = TintedSurface,
                    unfocusedContainerColor = TintedSurface,
                    disabledContainerColor = TintedSurface.copy(alpha = 0.5f),
                    cursorColor = Color.White,
                ),
            )
            IconButton(
                onClick = { sendCurrent() },
                enabled = canSend,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun UnavailableContent() {
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
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Download the AI model in More → AI Model to unlock the coach.",
                color = TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ReadyContent(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What would you like to know?",
            color = TextMuted,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        suggestions.forEach { suggestion ->
            FilterChip(
                selected = false,
                onClick = { onSuggestion(suggestion) },
                label = {
                    Text(
                        text = suggestion,
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .widthIn(max = 320.dp),
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = TintedSurface,
                    selectedContainerColor = TintedSurface,
                    labelColor = Color.White,
                ),
                border = BorderStroke(1.dp, TintedBorder),
            )
        }
    }
}

@Composable
private fun ChatContent(
    history: List<ChatMessage>,
    thinkingStatus: String?,
    partialResponse: String?,
    errorMessage: String?,
) {
    val listState = rememberLazyListState()

    val totalItems = history.size +
        (if (thinkingStatus != null || partialResponse != null) 1 else 0) +
        (if (errorMessage != null) 1 else 0)

    LaunchedEffect(totalItems, partialResponse) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(history) { message ->
            ChatBubble(message = message)
        }

        // Thinking indicator
        if (thinkingStatus != null) {
            item {
                AiInsightCard(
                    borderMode = AiBorderMode.Preparing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Thinking…",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    if (!thinkingStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = thinkingStatus,
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // Partial / streaming response
        if (partialResponse != null) {
            item {
                AiInsightCard(
                    borderMode = AiBorderMode.Generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = partialResponse,
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
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
                color = TintedSurface,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        } else {
            AiInsightCard(
                borderMode = AiBorderMode.Static,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
