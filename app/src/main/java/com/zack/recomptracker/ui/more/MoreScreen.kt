package com.zack.recomptracker.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onFoodsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Button(onClick = onFoodsClick, modifier = Modifier.fillMaxWidth()) {
            Text("Foods/Meals")
        }
        OutlinedButton(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
    }
}
