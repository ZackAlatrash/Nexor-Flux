package com.zack.recomptracker.ui.aicoach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun AiCoachScreen(
    viewModel: AiCoachViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val ambientOrb = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-80).dp, y = (-70).dp)
                .background(ambientOrb),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = appColors.textPrimary,
                        )
                    }
                    Text(
                        text = "AI & Coach",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = appColors.textPrimary,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            if (state.message != null) {
                item { MessageText(state.message) }
            }

            // ── Enable AI master toggle ───────────────────────────────────────
            item {
                TintedCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.tintedSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = accent.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable AI",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary,
                            )
                            Text(
                                text = "Insights & coach chat",
                                fontSize = 11.sp,
                                color = appColors.textMuted,
                            )
                        }
                        VioletToggle(
                            label = "",
                            checked = state.aiInsightsEnabled,
                            onCheckedChange = viewModel::setAiInsights,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
            }

            if (state.aiInsightsEnabled) {
                // ── Engine selection (radio cards) ────────────────────────────
                item { SectionLabel("Engine") }
                item {
                    EngineRadioCard(
                        selected = state.aiBackend == AiBackend.LOCAL,
                        title = "On-device (Gemma)",
                        subtitle = "Private · runs offline on your phone",
                        onClick = { viewModel.setAiBackend(AiBackend.LOCAL) },
                    )
                }
                item {
                    EngineRadioCard(
                        selected = state.aiBackend == AiBackend.CLOUD,
                        title = "Cloud API",
                        subtitle = "Faster & most capable · needs an API key",
                        onClick = { viewModel.setAiBackend(AiBackend.CLOUD) },
                    )
                }

                // ── Contextual config ─────────────────────────────────────────
                when (state.aiBackend) {
                    AiBackend.LOCAL -> {
                        if (aiState != AiInsightState.Disabled) {
                            item {
                                AiModelSection(
                                    aiState = aiState,
                                    selectedModel = selectedModel,
                                    onModelSelect = viewModel::setModel,
                                    onDownload = viewModel::requestModelDownload,
                                    onCancel = viewModel::cancelDownload,
                                    onDelete = viewModel::deleteModel,
                                )
                            }
                        }
                    }

                    AiBackend.CLOUD -> {
                        item {
                            TintedCard {
                                CloudStatusLine(
                                    testingConnection = state.testingConnection,
                                    testConnectionResult = state.testConnectionResult,
                                    cloudHasKey = state.cloudHasKey,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Your logged data is sent to the API you configure. Switch to On-device to keep everything private.",
                                    color = appColors.textMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                SectionLabel("Quick fill")
                                Spacer(Modifier.height(6.dp))
                                ProviderPresetChips(onPick = viewModel::setCloudBaseUrl)
                                Spacer(Modifier.height(12.dp))
                                GlassInputField(
                                    label = "Base URL",
                                    value = state.cloudBaseUrl,
                                    onValueChange = viewModel::setCloudBaseUrl,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                GlassInputField(
                                    label = "Model ID",
                                    value = state.cloudModelId,
                                    onValueChange = viewModel::setCloudModelId,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                var apiKeyInput by remember { mutableStateOf("") }
                                GlassInputField(
                                    label = if (state.cloudHasKey) "API key (saved — type to replace)" else "API key",
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            if (apiKeyInput.isNotBlank()) {
                                                viewModel.setCloudApiKey(apiKeyInput)
                                                apiKeyInput = ""
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Save key", fontSize = 13.sp) }
                                    if (state.cloudHasKey) {
                                        Button(
                                            onClick = { viewModel.clearCloudApiKey() },
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Clear", fontSize = 13.sp) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.testCloudConnection() },
                                    enabled = !state.testingConnection,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (state.testingConnection) "Testing…" else "Test connection",
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
