package com.zack.recomptracker.ui.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyHistoryScreen(
    viewModel: BodyHistoryViewModel,
    onEditDay: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-in History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(items) { item ->
                when (item) {
                    is BodyHistoryItem.Logged -> LoggedRow(item, onEditDay)
                    is BodyHistoryItem.Missing -> MissingRow(item, onEditDay)
                }
            }
            item {
                Text(
                    "Showing up to 90 days · Tap any row to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .wrapContentWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoggedRow(item: BodyHistoryItem.Logged, onEdit: (LocalDate) -> Unit) {
    val e: DailyLogEntity = item.entity
    val summary = buildString {
        e.bodyWeightKg?.let { append("$it kg") }
        e.waistCm?.let { if (isNotEmpty()) append(" · "); append("$it cm") }
        e.waistSkinfoldMm?.let { if (isNotEmpty()) append(" · "); append("$it mm") }
    }
    val detail = buildString {
        e.energyScore?.let { append("⚡$it") }
        e.sleepHours?.let { if (isNotEmpty()) append(" · "); append("😴${it}h") }
        if (e.trained) { if (isNotEmpty()) append(" · "); append("🏋️ trained") }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(item.date) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun MissingRow(item: BodyHistoryItem.Missing, onAdd: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd(item.date) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("no entry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "+ Add",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
