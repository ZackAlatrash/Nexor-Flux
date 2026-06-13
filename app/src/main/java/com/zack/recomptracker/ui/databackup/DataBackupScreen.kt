package com.zack.recomptracker.ui.databackup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.integrations.SettingsCard
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextMuted
import java.time.LocalDate

@Composable
fun DataBackupScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAppAccent.current

    val ambientOrb1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }
    val ambientOrb2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }

    // ── Activity-result launchers (full backup + personal foods) ──────────────
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportToUri(context, uri) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFromUri(context, uri) }
    val personalFoodsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportPersonalFoodsToUri(context, uri) }
    val personalFoodsImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importPersonalFoodsFromUri(context, uri) }

    var showImportConfirm by remember { mutableStateOf(false) }
    var showClearFoodLibraryConfirm by remember { mutableStateOf(false) }
    var showResetLogsConfirm by remember { mutableStateOf(false) }
    var showResetAllConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(ambientOrb1),
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = (-180).dp)
                .background(ambientOrb2),
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
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "Data & Backup",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            if (state.message != null) {
                item { MessageText(state.message, state.messageKind) }
            }

            // ── Full backup ───────────────────────────────────────────────────
            item { SectionLabel("Full backup") }
            item {
                SettingsCard {
                    DataRow(
                        icon = Icons.Rounded.Upload,
                        title = "Export backup",
                        detail = "Save all data to a JSON file",
                        showDivider = true,
                        onClick = { exportLauncher.launch("recomp-tracker-${LocalDate.now()}.json") },
                        enabled = !state.busy,
                    )
                    DataRow(
                        icon = Icons.Rounded.Download,
                        title = "Import backup",
                        detail = "Restore everything from a JSON file",
                        showDivider = false,
                        onClick = { showImportConfirm = true },
                        enabled = !state.busy,
                    )
                }
            }

            // ── Personal foods ────────────────────────────────────────────────
            item { SectionLabel("Personal foods") }
            item {
                SettingsCard {
                    DataRow(
                        icon = Icons.Rounded.RestaurantMenu,
                        title = "Export personal foods",
                        detail = "Save your food library to JSON",
                        showDivider = true,
                        onClick = {
                            personalFoodsExportLauncher.launch(
                                "recomp-tracker-personal-foods-${LocalDate.now()}.json",
                            )
                        },
                        enabled = !state.busy,
                    )
                    DataRow(
                        icon = Icons.Rounded.FileOpen,
                        title = "Import personal foods",
                        detail = "Add foods from a JSON or CSV file",
                        showDivider = false,
                        onClick = {
                            personalFoodsImportLauncher.launch(
                                arrayOf("application/json", "text/*", "*/*"),
                            )
                        },
                        enabled = !state.busy,
                    )
                }
            }

            // ── Danger zone ───────────────────────────────────────────────────
            item { SectionLabel("Danger zone") }
            item {
                DangerCard {
                    DataRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Clear food library",
                        detail = "Delete personal foods; keep logs & plan",
                        showDivider = true,
                        onClick = { showClearFoodLibraryConfirm = true },
                        enabled = !state.busy,
                        danger = true,
                    )
                    DataRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = "Reset logs only",
                        detail = "Delete all food & body log entries",
                        showDivider = true,
                        onClick = { showResetLogsConfirm = true },
                        enabled = !state.busy,
                        danger = true,
                    )
                    DataRow(
                        icon = Icons.Rounded.WarningAmber,
                        title = "Reset all local data",
                        detail = "Permanently delete everything",
                        showDivider = false,
                        onClick = { showResetAllConfirm = true },
                        enabled = !state.busy,
                        danger = true,
                    )
                }
            }
        }
    }

    if (showImportConfirm) {
        ConfirmDialog(
            title = "Import backup?",
            body = "This will replace all your current data with the contents of the backup file.",
            confirmLabel = "Import",
            isDestructive = false,
            onConfirm = {
                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                showImportConfirm = false
            },
            onDismiss = { showImportConfirm = false },
        )
    }
    if (showClearFoodLibraryConfirm) {
        ConfirmDialog(
            title = "Clear food library?",
            body = "All foods in your personal library (Samsung Health imports and manually added foods) will be deleted. Your logs, plan, and the NEVO catalog are kept. This cannot be undone.",
            confirmLabel = "Clear",
            isDestructive = true,
            onConfirm = { viewModel.clearFoodLibrary(); showClearFoodLibraryConfirm = false },
            onDismiss = { showClearFoodLibraryConfirm = false },
        )
    }
    if (showResetLogsConfirm) {
        ConfirmDialog(
            title = "Reset logs?",
            body = "All food and body log entries will be deleted. Your plan, foods, and meals are kept.",
            confirmLabel = "Reset",
            isDestructive = true,
            onConfirm = { viewModel.resetLogsOnly(); showResetLogsConfirm = false },
            onDismiss = { showResetLogsConfirm = false },
        )
    }
    if (showResetAllConfirm) {
        ConfirmDialog(
            title = "Delete everything?",
            body = "All data will be permanently deleted — logs, plan, foods, and meals. This cannot be undone.",
            confirmLabel = "Delete everything",
            isDestructive = true,
            onConfirm = { viewModel.resetEverything(); showResetAllConfirm = false },
            onDismiss = { showResetAllConfirm = false },
        )
    }
}

// ── Danger zone card (red-tinted) ─────────────────────────────────────────────

@Composable
private fun DangerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(ErrorRed.copy(alpha = 0.06f))
            .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(CornerCard))
            .padding(vertical = 4.dp),
        content = content,
    )
}

/**
 * Uniform tappable row used across every section. The whole row launches [onClick].
 *
 * - Normal variant ([danger] = false): violet-tinted icon box, white title, muted chevron.
 * - Danger variant ([danger] = true): [ErrorRed] icon box, title, and chevron.
 */
@Composable
private fun DataRow(
    icon: ImageVector,
    title: String,
    detail: String,
    showDivider: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    danger: Boolean = false,
) {
    val accent = LocalAppAccent.current
    val iconTint = if (danger) ErrorRed else accent.accent
    val titleColor = if (danger) ErrorRed else Color.White
    val chevronColor = if (danger) ErrorRed else Color(0x33FFFFFF)
    val dividerColor = if (danger) ErrorRed.copy(alpha = 0.12f) else Color(0x0DFFFFFF)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.10f))
                    .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text(detail, fontSize = 11.sp, color = TextMuted)
            }
            Text(text = "›", fontSize = 18.sp, color = chevronColor)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )
        }
    }
}
