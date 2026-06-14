package com.zack.recomptracker.ui.integrations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SettingRow
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.theme.LocalAppColors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun IntegrationsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = com.zack.recomptracker.ui.theme.LocalAppAccent.current

    val ambientOrb1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }
    val ambientOrb2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }

    // ── Activity-result launchers (HC + Samsung + NEVO only) ──────────────────
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { viewModel.onPermissionsResult() }
    val nutritionPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { viewModel.onNutritionPermissionsResult() }
    val samsungFoodExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.scanSamsungHealthFoodExport(context, uri) }
    val nevoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importNevoFromUri(context, uri) }

    var showRemoveNevoConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.pendingHcPermissionRequest) {
        if (state.pendingHcPermissionRequest) {
            hcPermissionLauncher.launch(viewModel.hcRequiredPermissions)
            viewModel.onHcPermissionRequestConsumed()
        }
    }
    LaunchedEffect(state.pendingNutritionPermissionRequest) {
        if (state.pendingNutritionPermissionRequest) {
            nutritionPermissionLauncher.launch(viewModel.nutritionPermission)
            viewModel.onNutritionPermissionRequestConsumed()
        }
    }

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
                    val appColors = LocalAppColors.current
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
                        text = "Integrations",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = appColors.textPrimary,
                        letterSpacing = (-0.8).sp,
                    )
                }
            }

            // ── Health sync ───────────────────────────────────────────────────
            item { SectionLabel("Health sync") }
            item {
                HealthConnectSection(
                    availability = state.healthConnectAvailability,
                    enabled = state.healthConnectEnabled,
                    hasPermissions = state.healthConnectHasPermissions,
                    syncing = state.healthConnectSyncing,
                    message = state.healthConnectMessage,
                    messageKind = state.healthConnectMessageKind,
                    onToggle = viewModel::onHealthConnectToggled,
                    onSyncNow = viewModel::syncNow,
                    importingFoods = state.historicalFoodBusy,
                    onImportFoods = viewModel::startHistoricalFoodImport,
                    onInstall = {
                        val marketUri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
                        } catch (e: android.content.ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                )
            }

            // ── Food sources ──────────────────────────────────────────────────
            item { SectionLabel("Food sources") }
            item {
                SettingsCard {
                    // Samsung Health
                    SettingRow(
                        icon = Icons.Rounded.Smartphone,
                        title = "Samsung Health",
                        detail = "Import food log from a data export",
                        showDivider = true,
                    ) {
                        LiquidGlassButton(
                            onClick = { samsungFoodExportLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.historicalFoodBusy,
                        ) {
                            if (state.historicalFoodBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Pick CSV")
                        }
                    }

                    // NEVO catalog
                    SettingRow(
                        icon = Icons.Rounded.Restaurant,
                        title = "NEVO catalog",
                        detail = state.nevoSourceVersion
                            ?.let { "Dutch food database · v$it" }
                            ?: "Not imported",
                        showDivider = false,
                    ) {
                        Column(
                            modifier = Modifier.width(112.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LiquidPrimaryButton(
                                text = if (state.nevoSourceVersion == null) "Import" else "Replace",
                                onClick = { nevoImportLauncher.launch(arrayOf("text/csv", "text/*", "*/*")) },
                                enabled = !state.busy,
                            )
                            if (state.nevoSourceVersion != null) {
                                LiquidSecondaryButton(
                                    text = "Remove",
                                    onClick = { showRemoveNevoConfirm = true },
                                    enabled = !state.busy,
                                )
                            }
                        }
                    }
                }
            }

            if (state.message != null) {
                item { MessageText(state.message, state.messageKind) }
            }
        }
    }

    if (showRemoveNevoConfirm) {
        ConfirmDialog(
            title = "Remove NEVO catalog?",
            body = "The imported NEVO foods will be removed. You can re-import the CSV at any time.",
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = { viewModel.removeNevoCatalog(); showRemoveNevoConfirm = false },
            onDismiss = { showRemoveNevoConfirm = false },
        )
    }

    if (state.historicalFoodCandidates.isNotEmpty()) {
        HistoricalFoodReviewDialog(
            candidates = state.historicalFoodCandidates,
            selected = state.selectedHistoricalFoodIdentities,
            onToggle = viewModel::toggleHistoricalFoodCandidate,
            onDismiss = viewModel::dismissHistoricalFoodReview,
            onImport = viewModel::importSelectedHistoricalFoods,
        )
    }
}
