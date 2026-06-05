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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.zack.recomptracker.ai.AiInsightState
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
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500
import java.time.LocalDate

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    onStatsClick: () -> Unit,
    onChartsClick: () -> Unit,
    onPlanClick: () -> Unit,
    onProgressClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                    Brush.radialGradient(listOf(Color(0x268B5CF6), Color.Transparent)),
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
                        showDivider = false,
                    ) {
                        Switch(
                            checked = state.aiInsightsEnabled,
                            onCheckedChange = viewModel::setAiInsights,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Violet500,
                                uncheckedThumbColor = Color(0x80FFFFFF),
                                uncheckedTrackColor = Color(0x1AFFFFFF),
                                uncheckedBorderColor = Color(0x26FFFFFF),
                            ),
                        )
                    }
                }
            }

            // AI model card — only when AI Insights is on and model needs attention
            if (aiState != AiInsightState.Disabled) {
                item {
                    AiModelSection(
                        aiState = aiState,
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
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    when (aiState) {
        AiInsightState.Disabled -> Unit

        AiInsightState.ModelMissing -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiModelHeader()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Download the on-device model to get AI explanations on the Stats screen.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 17.sp,
                )
                Text(
                    "~2.6 GB · Wi-Fi recommended",
                    fontSize = 10.sp,
                    color = TextFaint,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text("Download Model", fontSize = 13.sp)
                }
            }
        }

        is AiInsightState.Downloading -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiModelHeader()
                Spacer(Modifier.height(8.dp))
                val progress = aiState.progress
                if (progress != null) {
                    Text(
                        "${"%.1f".format(progress * 2.6f)} GB of 2.6 GB",
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
        }

        AiInsightState.DownloadFailed -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiModelHeader()
                Spacer(Modifier.height(6.dp))
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
        }

        AiInsightState.ModelReady,
        AiInsightState.LoadingModel,
        is AiInsightState.Generating,
        is AiInsightState.Ready,
        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiModelHeader()
                Spacer(Modifier.height(6.dp))
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
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFF4ADE80)),
                        )
                        Text("Model ready · 2.6 GB", fontSize = 12.sp, color = TextMuted)
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
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x248B5CF6))
            .border(1.dp, Color(0x388B5CF6), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 16.sp)
    }
}

// ── Font Picker ───────────────────────────────────────────────────────────────

@Composable
private fun FontPicker(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("default" to "Default", "space" to "Space", "jakarta" to "Jakarta").forEach { (key, label) ->
            val isActive = selected == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isActive) Color(0x388B5CF6) else Color(0x0FFFFFFF))
                    .border(
                        1.dp,
                        if (isActive) Color(0x598B5CF6) else Color(0x14FFFFFF),
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
                    color = if (isActive) Violet300 else Color(0x59FFFFFF),
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
    val alpha = if (enabled) 1f else 0.5f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CornerCard))
            .background(if (isPrimary) Color(0x1F8B5CF6) else CardSurface)
            .border(
                1.dp,
                if (isPrimary) Color(0x388B5CF6) else CardBorder,
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
            color = if (isPrimary) Violet300.copy(alpha = alpha) else Color(0x73FFFFFF),
        )
    }
}
