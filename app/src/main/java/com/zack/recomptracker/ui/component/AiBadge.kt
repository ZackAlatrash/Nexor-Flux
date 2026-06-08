package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.LocalAppAccent

@Composable
fun AiBadge(modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✦ AI",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = accent.accentLight.copy(alpha = 0.75f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun AiBadgePreview() {
    AiBadge()
}
