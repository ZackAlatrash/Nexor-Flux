package com.zack.recomptracker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.GemmaInsightService
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.domain.foodimport.FoodImportCandidate
import com.zack.recomptracker.domain.foodimport.identity
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.ToggleRow
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import java.time.LocalDate

private val HealthConnectGreen = Color(0xFF34D399)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    val personalFoodsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportPersonalFoodsToUri(context, uri)
    }
    val personalFoodsImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importPersonalFoodsFromUri(context, uri)
    }
    val nevoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importNevoFromUri(context, uri)
    }
    val samsungFoodExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.scanSamsungHealthFoodExport(context, uri)
    }
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) {
        // The returned granted-set is unreliable; the ViewModel re-queries the
        // authoritative permission state, so the callback payload is ignored.
        viewModel.onPermissionsResult()
    }
    val nutritionPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) {
        viewModel.onNutritionPermissionsResult()
    }

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

    val gemma = GemmaInsightService().availability()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Local-only controls")
                MessageText(state.message)
            }
        }
        item {
            SectionCard("Backup") {
                Button(
                    onClick = { exportLauncher.launch("recomp-tracker-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export JSON backup")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import JSON backup")
                }
                OutlinedButton(
                    onClick = { personalFoodsExportLauncher.launch("recomp-tracker-personal-foods-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export personal foods JSON")
                }
                OutlinedButton(
                    onClick = { personalFoodsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import personal foods JSON")
                }
            }
        }
        item {
            SectionCard("Dutch food catalog") {
                Text("Import an official NEVO CSV export downloaded after accepting RIVM's conditions.")
                Button(
                    onClick = { nevoImportLauncher.launch(arrayOf("text/csv", "text/*", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.nevoSourceVersion == null) "Import NEVO CSV" else "Replace NEVO CSV")
                }
                if (state.nevoSourceVersion != null) {
                    Text(
                        "Based on data from NEVO online version ${state.nevoSourceVersion}, RIVM, Bilthoven",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::removeNevoCatalog,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove NEVO catalog")
                    }
                }
            }
        }
        item {
            SectionCard("Samsung Health food import") {
                Text(
                    "In Samsung Health: Profile → Settings → Download personal data. " +
                        "Once downloaded, pick the ZIP file directly — or extract it and pick " +
                        "\"com.samsung.health.food_info.T.csv\". " +
                        "Foods are normalised to per-100 g and added to your personal library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
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
        item {
            HealthConnectSection(
                availability = state.healthConnectAvailability,
                enabled = state.healthConnectEnabled,
                hasPermissions = state.healthConnectHasPermissions,
                syncing = state.healthConnectSyncing,
                message = state.healthConnectMessage,
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
        item {
            SectionCard("Reset") {
                OutlinedButton(
                    onClick = viewModel::resetLogsOnly,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset logs only")
                }
                OutlinedButton(
                    onClick = viewModel::resetEverything,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset all local data")
                }
            }
        }
        item {
            SectionCard("Gemma status") {
                Text(if (gemma.available) "Available" else "Not enabled")
                Text(gemma.reason)
            }
        }
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

@Composable
private fun HealthConnectSection(
    availability: HealthConnectAvailability,
    enabled: Boolean,
    hasPermissions: Boolean,
    syncing: Boolean,
    message: String?,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    importingFoods: Boolean,
    onImportFoods: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    "Health Connect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Auto-fill steps, weight & sleep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (availability) {
            HealthConnectAvailability.NotInstalled -> {
                StatusRow(
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "Health Connect isn't installed on this device.",
                )
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install Health Connect")
                }
            }

            HealthConnectAvailability.NotSupported -> {
                StatusRow(
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "Health Connect isn't supported on this device.",
                )
            }

            HealthConnectAvailability.Available -> {
                ToggleRow(
                    label = "Sync automatically",
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
                val (dotColor, statusText) = when {
                    enabled && hasPermissions ->
                        HealthConnectGreen to "Connected"
                    enabled && !hasPermissions ->
                        MaterialTheme.colorScheme.error to "Permission needed — tap to reconnect"
                    else ->
                        MaterialTheme.colorScheme.onSurfaceVariant to "Not connected"
                }
                StatusRow(dotColor = dotColor, text = statusText)
                if (enabled && hasPermissions) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync now")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onImportFoods,
                    enabled = !importingFoods,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (importingFoods) "Scanning food history…" else "Import foods from Health Connect")
                }
            }
        }

        MessageText(message)
    }
}

@Composable
private fun HistoricalFoodReviewDialog(
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
                    "Health Connect does not include the original serving weight. Verify each row: selected macros will be treated as the app's 100g baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Text(candidate.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${candidate.calories} kcal · ${candidate.proteinG.toInt()}P ${candidate.carbsG.toInt()}C ${candidate.fatG.toInt()}F",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = selected.isNotEmpty()) {
                Text("Import selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StatusRow(
    dotColor: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HealthConnectSectionPreview() {
    RecompTrackerTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = true, hasPermissions = true, syncing = false,
                message = "Synced from Health Connect.",
                importingFoods = false,
                onToggle = {}, onSyncNow = {}, onImportFoods = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = true, hasPermissions = true, syncing = true,
                message = "Connected. Syncing…",
                importingFoods = false,
                onToggle = {}, onSyncNow = {}, onImportFoods = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = true, hasPermissions = false, syncing = false,
                message = "Permission wasn't granted. Tap the toggle to try again.",
                importingFoods = false,
                onToggle = {}, onSyncNow = {}, onImportFoods = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = false, hasPermissions = false, syncing = false,
                message = null,
                importingFoods = false,
                onToggle = {}, onSyncNow = {}, onImportFoods = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.NotInstalled,
                enabled = false, hasPermissions = false, syncing = false,
                message = null,
                importingFoods = false,
                onToggle = {}, onSyncNow = {}, onImportFoods = {}, onInstall = {},
            )
        }
    }
}
