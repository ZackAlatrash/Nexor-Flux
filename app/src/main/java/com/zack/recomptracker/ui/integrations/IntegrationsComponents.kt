package com.zack.recomptracker.ui.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.domain.foodimport.FoodImportCandidate
import com.zack.recomptracker.domain.foodimport.identity
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.LocalAppColors

internal val HealthConnectGreen = Color(0xFF34D399)

// ── Health Connect Section ────────────────────────────────────────────────────

@Composable
internal fun HealthConnectSection(
    availability: HealthConnectAvailability,
    enabled: Boolean,
    hasPermissions: Boolean,
    syncing: Boolean,
    message: String?,
    messageKind: MessageKind = MessageKind.INFO,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    importingFoods: Boolean,
    onImportFoods: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        // Header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1A34D399))
                    .border(1.dp, Color(0x3334D399), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = HealthConnectGreen,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                val appColors = LocalAppColors.current
                Text(
                    "Health Connect",
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                )
                Text(
                    "Auto-fill steps, weight & sleep",
                    style = AppType.label,
                    color = appColors.textMuted,
                )
            }
        }

        CardDivider()

        when (availability) {
            HealthConnectAvailability.NotInstalled -> {
                HcStatusRow(dotColor = LocalAppColors.current.textMuted, text = "Health Connect isn't installed on this device.")
                LiquidGlassButton(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install Health Connect")
                }
            }

            HealthConnectAvailability.NotSupported -> {
                HcStatusRow(dotColor = LocalAppColors.current.textMuted, text = "Health Connect isn't supported on this device.")
            }

            HealthConnectAvailability.Available -> {
                VioletToggle(
                    label = "Sync automatically",
                    checked = enabled,
                    onCheckedChange = onToggle,
                )

                val (dotColor, statusText) = when {
                    enabled && hasPermissions  -> HealthConnectGreen to "Connected"
                    enabled && !hasPermissions -> Color(0xFFfb7185) to "Permission needed — tap toggle to reconnect"
                    else                       -> LocalAppColors.current.textMuted to "Not connected"
                }
                HcStatusRow(dotColor = dotColor, text = statusText)

                if (enabled && hasPermissions) {
                    LiquidGlassButton(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync now")
                        }
                    }
                }

                LiquidSecondaryButton(
                    text = if (importingFoods) "Scanning food history…" else "Import foods from Health Connect",
                    onClick = onImportFoods,
                    enabled = !importingFoods,
                )
            }
        }

        MessageText(message, messageKind)
    }
}

// ── Historical food review dialog ─────────────────────────────────────────────

@Composable
internal fun HistoricalFoodReviewDialog(
    candidates: List<FoodImportCandidate>,
    selected: Set<com.zack.recomptracker.domain.foodimport.FoodIdentity>,
    onToggle: (FoodImportCandidate) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review historical foods") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Health Connect does not include the original serving weight. Verify each row: selected macros will be treated as the app's 100 g baseline.",
                    style = AppType.cardSubtitle,
                    color = LocalAppColors.current.textMuted,
                    lineHeight = 17.sp,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(candidates.size) { index ->
                        val candidate = candidates[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = candidate.identity() in selected,
                                onCheckedChange = { onToggle(candidate) },
                            )
                            Column {
                                Text(candidate.name, style = AppType.body.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${candidate.calories} kcal · ${candidate.proteinG.toInt()}P ${candidate.carbsG.toInt()}C ${candidate.fatG.toInt()}F",
                                    style = AppType.label,
                                    color = LocalAppColors.current.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = selected.isNotEmpty()) { Text("Import selected") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalAppColors.current.cardBorder),
    )
}

@Composable
internal fun HcStatusRow(dotColor: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(text = text, style = AppType.cardSubtitle, color = LocalAppColors.current.textPrimary.copy(alpha = 0.8f))
    }
}
