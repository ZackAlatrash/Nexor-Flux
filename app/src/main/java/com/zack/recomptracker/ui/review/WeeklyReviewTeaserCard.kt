package com.zack.recomptracker.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted

/**
 * Dashboard entry point for the weekly briefing — a liquid-glass [AiInsightCard] teaser that matches
 * the app's other AI surfaces. When [showBadge] is true (a fresh, unseen review for this week) the
 * card wears the `Ready` rim, which flashes on appear and acts as the Monday nudge — no separate
 * Material badge.
 */
@Composable
fun WeeklyReviewTeaserCard(
    showBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    AiInsightCard(
        borderMode = if (showBadge) AiBorderMode.Ready else AiBorderMode.Static,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WEEKLY REVIEW",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextFaint,
                letterSpacing = 0.14.sp,
            )
            AiBadge()
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showBadge) "Your week is ready" else "Your weekly AI breakdown",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (showBadge) "Tap to see this week's verdict" else "Tap for an AI read of your week",
                    fontSize = 12.5.sp,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(text = "›", fontSize = 22.sp, color = accent.accentLight)
        }
    }
}
