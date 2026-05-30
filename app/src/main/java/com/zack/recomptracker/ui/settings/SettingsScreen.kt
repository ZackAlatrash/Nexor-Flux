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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { grantedPerms ->
        viewModel.onPermissionsResult(grantedPerms)
    }

    LaunchedEffect(state.pendingHcPermissionRequest) {
        if (state.pendingHcPermissionRequest) {
            hcPermissionLauncher.launch(viewModel.hcRequiredPermissions)
            viewModel.onHcPermissionRequestConsumed()
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
            }
        }
        item {
            HealthConnectSection(
                availability = state.healthConnectAvailability,
                enabled = state.healthConnectEnabled,
                hasPermissions = state.healthConnectHasPermissions,
                syncing = state.healthConnectSyncing,
                onToggle = viewModel::onHealthConnectToggled,
                onSyncNow = viewModel::syncNow,
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
}

@Composable
private fun HealthConnectSection(
    availability: HealthConnectAvailability,
    enabled: Boolean,
    hasPermissions: Boolean,
    syncing: Boolean,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
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
            }
        }
    }
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
                onToggle = {}, onSyncNow = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = true, hasPermissions = true, syncing = true,
                onToggle = {}, onSyncNow = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = true, hasPermissions = false, syncing = false,
                onToggle = {}, onSyncNow = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.Available,
                enabled = false, hasPermissions = false, syncing = false,
                onToggle = {}, onSyncNow = {}, onInstall = {},
            )
            HealthConnectSection(
                availability = HealthConnectAvailability.NotInstalled,
                enabled = false, hasPermissions = false, syncing = false,
                onToggle = {}, onSyncNow = {}, onInstall = {},
            )
        }
    }
}
