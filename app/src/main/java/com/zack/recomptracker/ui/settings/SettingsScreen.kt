package com.zack.recomptracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.GemmaInsightService
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionCard
import java.time.LocalDate

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
