package com.zack.recomptracker.ui.train

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.FrostedCard

@Composable
fun TrainHomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        FrostedCard { Text("Train") }
    }
}
