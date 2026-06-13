package com.zack.recomptracker.ui.aicoach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.ModelVariant
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted

// ── AI Model Section ──────────────────────────────────────────────────────────

@Composable
internal fun AiModelSection(
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
                Button(onClick = onDownload) {
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
                TextButton(onClick = onCancel) {
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
                Button(onClick = onDownload) {
                    Text("Retry Download", fontSize = 13.sp)
                }
            }

            AiInsightState.ModelVerifying -> {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
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
                    TextButton(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp, color = Color(0xFFFC8181))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModelVariantSelector(
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

// ── AI Engine Selector (On-device / Cloud) — segmented, used by MoreScreen ────────

@Composable
internal fun AiEngineSelector(backend: AiBackend, onSelect: (AiBackend) -> Unit) {
    val accent = LocalAppAccent.current
    val options = listOf(
        Triple(AiBackend.LOCAL, "On-device", "Private · Gemma"),
        Triple(AiBackend.CLOUD, "Cloud", "API key · most capable"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, title, subtitle) ->
            val isActive = backend == value
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
                        enabled = !isActive,
                        onClick = { onSelect(value) },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) accent.accentLighter else Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = if (isActive) accent.accentLight else TextFaint,
                    )
                }
            }
        }
    }
}

// ── Engine radio card (descriptive — used by the AI & Coach screen) ──────────────

@Composable
internal fun EngineRadioCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.tintedSurface else Color(0x0FFFFFFF))
            .border(
                1.dp,
                if (selected) accent.tintedBorder else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !selected,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Radio indicator
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (selected) accent.accentLight else Color(0x40FFFFFF),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent.accentLight),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
    }
}

// ── Cloud provider quick-fill presets ────────────────────────────────────────────

private val CLOUD_PRESETS = listOf(
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "OpenAI" to "https://api.openai.com/v1",
    "Groq" to "https://api.groq.com/openai/v1",
    "NVIDIA" to "https://integrate.api.nvidia.com/v1",
)

@Composable
internal fun ProviderPresetChips(onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CLOUD_PRESETS.forEach { (label, url) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x0FFFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(7.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(url) },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0x99FFFFFF))
            }
        }
    }
}

// ── Cloud connection status line ─────────────────────────────────────────────────

@Composable
internal fun CloudStatusLine(
    testingConnection: Boolean,
    testConnectionResult: String?,
    cloudHasKey: Boolean,
) {
    val ok = testConnectionResult == "Connection OK"
    val (dot, label) = when {
        testingConnection -> Color(0xFFFBBF24) to "Testing…"
        ok -> Color(0xFF4ADE80) to "Connected"
        testConnectionResult != null -> Color(0xFFFC8181) to testConnectionResult
        cloudHasKey -> Color(0x66FFFFFF) to "Configured · not tested"
        else -> Color(0x66FFFFFF) to "Not configured"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label, fontSize = 12.sp, color = dot.copy(alpha = 0.95f), lineHeight = 16.sp)
    }
}

@Composable
internal fun AiModelHeader() {
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
