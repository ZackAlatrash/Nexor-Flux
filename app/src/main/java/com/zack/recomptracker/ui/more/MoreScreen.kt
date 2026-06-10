package com.zack.recomptracker.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.ModelVariant
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.LocalAppAccent
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.LocalDate

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    onStatsClick: () -> Unit,
    onChartsClick: () -> Unit,
    onPlanClick: () -> Unit,
    onProgressClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAppAccent.current
    val moreOrbBrush = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportToUri(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFromUri(context, uri)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    moreOrbBrush,
                ),
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
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "More",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            if (state.message != null) {
                item { MessageText(state.message) }
            }

            // ── Insights ──────────────────────────────────────────────────────
            item { SectionLabel("Insights") }
            item {
                MenuCard {
                    MenuRow(
                        emoji = "📊",
                        title = "Stats",
                        detail = "Verdict, targets and trend summary",
                        onClick = onStatsClick,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "📈",
                        title = "Charts",
                        detail = "Weight, waist, nutrition and lifts",
                        onClick = onChartsClick,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "📉",
                        title = "Progress",
                        detail = "Trends and adherence history",
                        onClick = onProgressClick,
                        showDivider = false,
                    )
                }
            }

            // ── Planning ──────────────────────────────────────────────────────
            item { SectionLabel("Planning") }
            item {
                MenuCard {
                    MenuRow(
                        emoji = "🎯",
                        title = "Plan",
                        detail = "Targets, zones and review cadence",
                        onClick = onPlanClick,
                        showDivider = false,
                    )
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            item { SectionLabel("Appearance") }
            item {
                MenuCard {
                    SettingRow(
                        emoji = "🔤",
                        title = "Font",
                        detail = "Display typeface",
                        showDivider = false,
                    ) {
                        FontPicker(
                            selected = state.selectedFont,
                            onSelect = viewModel::setFont,
                        )
                    }
                }
            }

            // ── App ───────────────────────────────────────────────────────────
            item { SectionLabel("App") }
            item {
                MenuCard {
                    MenuRow(
                        emoji = "⚙️",
                        title = "Settings",
                        detail = "Profile, backup, food catalog and more",
                        onClick = onSettingsClick,
                        showDivider = true,
                    )
                    SettingRow(
                        emoji = "❤️",
                        title = "Health Connect",
                        detail = "Sync steps and heart rate",
                        showDivider = true,
                    ) {
                        if (state.healthConnectConnected) {
                            HealthConnectedBadge()
                        } else {
                            Text("Disconnected", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                    SettingRow(
                        emoji = "✨",
                        title = "AI Insights",
                        detail = "Experimental on-device analysis",
                        showDivider = true,
                    ) {
                        Switch(
                            checked = state.aiInsightsEnabled,
                            onCheckedChange = viewModel::setAiInsights,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.accent,
                                uncheckedThumbColor = Color(0x80FFFFFF),
                                uncheckedTrackColor = Color(0x1AFFFFFF),
                                uncheckedBorderColor = Color(0x26FFFFFF),
                            ),
                        )
                    }
                    SettingRow(
                        emoji = "☁️",
                        title = "AI Backend",
                        detail = if (state.aiBackend == AiBackend.CLOUD) "Cloud model (API key)" else "On-device Gemma",
                        showDivider = false,
                    ) {
                        Switch(
                            checked = state.aiBackend == AiBackend.CLOUD,
                            onCheckedChange = { useCloud ->
                                viewModel.setAiBackend(if (useCloud) AiBackend.CLOUD else AiBackend.LOCAL)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.accent,
                                uncheckedThumbColor = Color(0x80FFFFFF),
                                uncheckedTrackColor = Color(0x1AFFFFFF),
                                uncheckedBorderColor = Color(0x26FFFFFF),
                            ),
                        )
                    }
                }
            }

            // Cloud config fields — only when cloud backend is selected
            if (state.aiBackend == AiBackend.CLOUD) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CornerCard))
                            .background(CardSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "In cloud mode your logged data is sent to the API you configure. On-device Gemma keeps everything private.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        OutlinedTextField(
                            value = state.cloudBaseUrl,
                            onValueChange = viewModel::setCloudBaseUrl,
                            label = { Text("Base URL (e.g. https://openrouter.ai/api/v1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        OutlinedTextField(
                            value = state.cloudModelId,
                            onValueChange = viewModel::setCloudModelId,
                            label = { Text("Model ID (e.g. anthropic/claude-3.5-sonnet)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        var apiKeyInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text(if (state.cloudHasKey) "API key (saved — type to replace)" else "API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    viewModel.setCloudApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                }
                            }) { Text("Save key") }
                            Button(
                                onClick = { viewModel.testCloudConnection() },
                                enabled = !state.testingConnection,
                            ) {
                                Text(if (state.testingConnection) "Testing…" else "Test connection")
                            }
                        }
                        state.testConnectionResult?.let {
                            Text(
                                text = it,
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }

            // AI model card — only when AI Insights is on
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

            // ── Data ─────────────────────────────────────────────────────────
            item { SectionLabel("Data") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DataActionCard(
                        emoji = "📤",
                        label = "Export backup",
                        isPrimary = true,
                        enabled = !state.busy,
                        onClick = {
                            exportLauncher.launch("recomp-backup-${LocalDate.now()}.json")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DataActionCard(
                        emoji = "📥",
                        label = "Import backup",
                        isPrimary = false,
                        enabled = !state.busy,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── AI Model Section ──────────────────────────────────────────────────────────

@Composable
private fun AiModelSection(
    aiState: AiInsightState,
    selectedModel: ModelVariant,
    onModelSelect: (ModelVariant) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    AiInsightCard(borderMode = AiBorderMode.Static) {
        AiModelHeader()
        Spacer(Modifier.height(10.dp))

        // Model selector — disabled while a download/verify is in progress for the other model.
        val canSwitch = aiState !is AiInsightState.Downloading && aiState != AiInsightState.ModelVerifying
        ModelVariantSelector(
            selected = selectedModel,
            onSelect = onModelSelect,
            enabled = canSwitch,
        )

        when (aiState) {
            AiInsightState.Disabled -> Unit

            AiInsightState.ModelMissing -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Download the on-device model to get AI explanations on the Stats screen.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 17.sp,
                )
                Text(
                    "~${selectedModel.displaySizeGb} · Wi-Fi recommended",
                    fontSize = 10.sp,
                    color = TextFaint,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text("Download Model", fontSize = 13.sp)
                }
            }

            is AiInsightState.Downloading -> {
                Spacer(Modifier.height(10.dp))
                val progress = aiState.progress
                if (progress != null) {
                    Text(
                        "${"%.1f".format(progress * selectedModel.displaySizeGbFloat)} GB of ${selectedModel.displaySizeGb}",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Downloading…", fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }

            AiInsightState.DownloadFailed -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Download failed — check your connection and try again.",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text("Retry Download", fontSize = 13.sp)
                }
            }

            AiInsightState.ModelVerifying -> {
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Verifying download…", fontSize = 12.sp, color = TextMuted)
                }
            }

            AiInsightState.ModelReady,
            AiInsightState.LoadingModel,
            is AiInsightState.Generating,
            is AiInsightState.Ready,
            is AiInsightState.Error -> {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80)),
                        )
                        Text(
                            "Model ready · ${selectedModel.displaySizeGb}",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp, color = Color(0xFFFC8181))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelVariantSelector(
    selected: ModelVariant,
    onSelect: (ModelVariant) -> Unit,
    enabled: Boolean,
) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModelVariant.entries.forEach { variant ->
            val isActive = selected == variant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) accent.accent.copy(alpha = 0.16f) else Color(0x0FFFFFFF))
                    .border(
                        1.dp,
                        if (isActive) accent.accent.copy(alpha = 0.35f) else Color(0x14FFFFFF),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled && !isActive,
                        onClick = { onSelect(variant) },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = variant.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) accent.accentLighter else Color.White.copy(alpha = if (enabled) 0.5f else 0.3f),
                    )
                    Text(
                        text = variant.displaySizeGb,
                        fontSize = 10.sp,
                        color = if (isActive) accent.accentLight else TextFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiModelHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AI MODEL",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextFaint,
            letterSpacing = 0.14.sp,
        )
        AiBadge()
    }
}

// ── Menu Card ─────────────────────────────────────────────────────────────────

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard)),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(
    emoji: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuIcon(emoji)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(detail, fontSize = 11.sp, color = TextMuted)
            }
            Text("›", fontSize = 14.sp, color = Color(0x33FFFFFF))
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x0DFFFFFF)),
            )
        }
    }
}

// ── Settings Row ──────────────────────────────────────────────────────────────

@Composable
private fun SettingRow(
    emoji: String,
    title: String,
    detail: String,
    showDivider: Boolean,
    control: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuIcon(emoji)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(detail, fontSize = 11.sp, color = TextMuted)
            }
            control()
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x0DFFFFFF)),
            )
        }
    }
}

// ── Menu Icon ─────────────────────────────────────────────────────────────────

@Composable
private fun MenuIcon(emoji: String) {
    val accent = LocalAppAccent.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 16.sp)
    }
}

// ── Font Picker ───────────────────────────────────────────────────────────────

@Composable
private fun FontPicker(selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAppAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("default" to "Default", "space" to "Space", "jakarta" to "Jakarta").forEach { (key, label) ->
            val isActive = selected == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isActive) accent.accent.copy(alpha = 0.22f) else Color(0x0FFFFFFF))
                    .border(
                        1.dp,
                        if (isActive) accent.accent.copy(alpha = 0.35f) else Color(0x14FFFFFF),
                        RoundedCornerShape(7.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(key) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) accent.accentLighter else Color(0x59FFFFFF),
                )
            }
        }
    }
}

// ── Health Connect Badge ──────────────────────────────────────────────────────

@Composable
private fun HealthConnectedBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1F34D399))
            .border(1.dp, Color(0x4034D399), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF34D399)),
        )
        Text("Connected", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
    }
}

// ── Data Action Cards ─────────────────────────────────────────────────────────

@Composable
private fun DataActionCard(
    emoji: String,
    label: String,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val alpha = if (enabled) 1f else 0.5f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CornerCard))
            .background(if (isPrimary) accent.tintedSurface else CardSurface)
            .border(
                1.dp,
                if (isPrimary) accent.tintedBorder else CardBorder,
                RoundedCornerShape(CornerCard),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) accent.accentLighter.copy(alpha = alpha) else Color(0x73FFFFFF),
        )
    }
}
