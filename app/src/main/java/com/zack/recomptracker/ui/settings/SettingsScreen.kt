package com.zack.recomptracker.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.displayName
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.AccentThemePicker
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.ScoreStepper
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AccentTheme
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.integrations.CardDivider
import com.zack.recomptracker.ui.integrations.HealthConnectSection
import com.zack.recomptracker.ui.integrations.HistoricalFoodReviewDialog
import com.zack.recomptracker.ui.integrations.SettingsCard
import java.time.LocalDate

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val accentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAppAccent.current
    val ambientOrb1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }
    val ambientOrb2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }

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
    val nevoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importNevoFromUri(context, uri) }
    val samsungFoodExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.scanSamsungHealthFoodExport(context, uri) }
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { viewModel.onPermissionsResult() }
    val nutritionPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { viewModel.onNutritionPermissionsResult() }

    var showResetLogsConfirm by remember { mutableStateOf(false) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var showRemoveNevoConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showClearFoodLibraryConfirm by remember { mutableStateOf(false) }

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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "Settings",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                    if (state.message != null) {
                        Spacer(Modifier.height(4.dp))
                        MessageText(state.message, state.messageKind)
                    }
                }
            }

            item { SectionLabel("My Profile") }
            item {
                UserProfileSection(profile = profile, onProfileChange = viewModel::saveProfile)
            }

            item { SectionLabel("Backup") }
            item {
                SettingsCard {
                    LiquidPrimaryButton(
                        text = "Export JSON backup",
                        onClick = { exportLauncher.launch("recomp-tracker-${LocalDate.now()}.json") },
                        enabled = !state.busy,
                    )
                    LiquidSecondaryButton(
                        text = "Import JSON backup",
                        onClick = { showImportConfirm = true },
                        enabled = !state.busy,
                    )
                    CardDivider()
                    LiquidSecondaryButton(
                        text = "Export personal foods",
                        onClick = { personalFoodsExportLauncher.launch("recomp-tracker-personal-foods-${LocalDate.now()}.json") },
                        enabled = !state.busy,
                    )
                    LiquidSecondaryButton(
                        text = "Import personal foods",
                        onClick = { personalFoodsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        enabled = !state.busy,
                    )
                }
            }

            item { SectionLabel("Dutch food catalog") }
            item {
                SettingsCard {
                    Text(
                        "Import an official NEVO CSV export downloaded after accepting RIVM's conditions.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 17.sp,
                    )
                    LiquidPrimaryButton(
                        text = if (state.nevoSourceVersion == null) "Import NEVO CSV" else "Replace NEVO CSV",
                        onClick = { nevoImportLauncher.launch(arrayOf("text/csv", "text/*", "*/*")) },
                        enabled = !state.busy,
                    )
                    if (state.nevoSourceVersion != null) {
                        Text(
                            "Based on NEVO online v${state.nevoSourceVersion}, RIVM, Bilthoven",
                            fontSize = 11.sp,
                            color = TextFaint,
                        )
                        LiquidSecondaryButton(
                            text = "Remove NEVO catalog",
                            onClick = { showRemoveNevoConfirm = true },
                            enabled = !state.busy,
                        )
                    }
                }
            }

            item { SectionLabel("Samsung Health import") }
            item {
                SettingsCard {
                    Text(
                        "In Samsung Health: Profile → Settings → Download personal data. " +
                            "Pick the ZIP file or extract and choose \"com.samsung.health.food_info.T.csv\". " +
                            "Foods are normalised to per-100 g and added to your personal library.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 17.sp,
                    )
                    LiquidGlassButton(
                        onClick = { samsungFoodExportLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.historicalFoodBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.historicalFoodBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Pick food_info CSV")
                    }
                }
            }

            item { SectionLabel("Integrations") }
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

            item { SectionLabel("Appearance") }
            item {
                AccentThemePicker(
                    selected = accentTheme,
                    onSelect = viewModel::setAccentTheme,
                )
            }

            item { SectionLabel("Danger zone") }
            item {
                SettingsCard {
                    LiquidSecondaryButton(
                        text = "Clear food library",
                        onClick = { showClearFoodLibraryConfirm = true },
                        enabled = !state.busy,
                    )
                    LiquidSecondaryButton(
                        text = "Reset logs only",
                        onClick = { showResetLogsConfirm = true },
                        enabled = !state.busy,
                    )
                    LiquidSecondaryButton(
                        text = "Reset all local data",
                        onClick = { showResetAllConfirm = true },
                        enabled = !state.busy,
                    )
                }
            }
        }
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

// ── User Profile Section ──────────────────────────────────────────────────────

@Composable
private fun UserProfileSection(
    profile: UserProfilePreferences,
    onProfileChange: (UserProfilePreferences) -> Unit,
) {
    FrostedCard {
        // ── Goal ─────────────────────────────────────────────────────────────
        SectionLabel("Goal")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FitnessGoal.entries.forEach { g ->
                ProfileOptionRow(
                    label = g.displayName(),
                    subtitle = g.shortDesc(),
                    selected = profile.goal == g,
                    onClick = {
                        onProfileChange(profile.copy(goal = if (profile.goal == g) null else g))
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Biological sex ────────────────────────────────────────────────────
        SectionLabel("Biological sex")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BiologicalSex.entries.forEach { s ->
                SexChip(
                    label = s.displayName(),
                    selected = profile.biologicalSex == s,
                    onClick = {
                        onProfileChange(profile.copy(biologicalSex = if (profile.biologicalSex == s) null else s))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Activity level ────────────────────────────────────────────────────
        SectionLabel("Activity level")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActivityLevel.entries.forEach { a ->
                ProfileOptionRow(
                    label = a.displayName(),
                    subtitle = a.shortDesc(),
                    selected = profile.activityLevel == a,
                    onClick = {
                        onProfileChange(profile.copy(activityLevel = if (profile.activityLevel == a) null else a))
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Gym sessions ──────────────────────────────────────────────────────
        ScoreStepper(
            label = "Gym sessions / week",
            value = profile.weeklyGymSessions ?: 0,
            onValueChange = { onProfileChange(profile.copy(weeklyGymSessions = it)) },
            range = 0..7,
        )

        Spacer(Modifier.height(16.dp))

        // ── Height ────────────────────────────────────────────────────────────
        GlassInputField(
            label = "Height",
            value = profile.heightCm?.toString() ?: "",
            onValueChange = { raw ->
                onProfileChange(profile.copy(heightCm = raw.filter { it.isDigit() }.take(3).toIntOrNull()))
            },
            unit = "cm",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun FitnessGoal.shortDesc(): String = when (this) {
    FitnessGoal.AGGRESSIVE_CUT  -> "~500+ kcal deficit"
    FitnessGoal.MODERATE_CUT    -> "~300–500 kcal deficit"
    FitnessGoal.MINI_CUT        -> "~150–200 kcal deficit"
    FitnessGoal.RECOMP          -> "Maintain and recompose"
    FitnessGoal.LEAN_BULK       -> "~100–200 kcal surplus"
    FitnessGoal.MODERATE_BULK   -> "~300–500 kcal surplus"
    FitnessGoal.AGGRESSIVE_BULK -> "~500+ kcal surplus"
}

private fun ActivityLevel.shortDesc(): String = when (this) {
    ActivityLevel.SEDENTARY          -> "Desk job, little exercise"
    ActivityLevel.LIGHTLY_ACTIVE     -> "1–3 workout days / week"
    ActivityLevel.MODERATELY_ACTIVE  -> "3–5 workout days / week"
    ActivityLevel.VERY_ACTIVE        -> "6–7 hard workout days / week"
}

// ── Profile sub-components ────────────────────────────────────────────────────

@Composable
private fun ProfileOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val bgColor = if (selected) accent.accent.copy(alpha = 0.10f) else Color.Transparent
    val borderColor = if (selected) accent.accent.copy(alpha = 0.30f) else Color(0x0DFFFFFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent.accentLight),
            )
        }
    }
}

@Composable
private fun SexChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val bgColor = if (selected) accent.accent.copy(alpha = 0.10f) else Color.Transparent
    val borderColor = if (selected) accent.accent.copy(alpha = 0.30f) else Color(0x12FFFFFF)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerSmall))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) accent.accentLighter else Color.White.copy(alpha = 0.5f),
        )
    }
}
