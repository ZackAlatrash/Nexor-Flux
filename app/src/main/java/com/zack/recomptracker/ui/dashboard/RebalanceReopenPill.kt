package com.zack.recomptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent

/**
 * Re-openable glass pill for a minimized Weekly Rebalance offer — the exact recipe as the Weekly
 * Review pill in `DashboardScreen.kt` (radial-gradient glow halo behind a wide [LiquidGlassButton]).
 *
 * **Gate this with a plain `if` at the call site — do NOT wrap it in `AnimatedVisibility`.** A
 * `graphicsLayer` (which `AnimatedVisibility`, `alpha`, etc. introduce) isolates the layer and
 * defeats [LiquidGlassButton]'s `drawBackdrop` sampling, so the glass paints backdrop-less and
 * near-invisible. The Weekly Review pill it replaces is a plain `if` for the same reason.
 *
 * Must be placed **inside** the dashboard content's own `Box` (where `LocalBackdrop` is live), in a
 * `BottomCenter`-aligned container — the same slot the Weekly Review pill occupies, which it replaces
 * (the two never show at once). `stackedAboveWeeklyReview` is retained for callers that do want it to
 * sit above the review pill, but the dashboard passes `false` so it takes the base slot.
 *
 * @param stackedAboveWeeklyReview pushes this pill up by an extra 60dp so it stacks above the Weekly
 *   Review pill instead of sharing its slot.
 */
@Composable
internal fun RebalanceReopenPill(
    stackedAboveWeeklyReview: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = FloatingNavHeight + 12.dp + if (stackedAboveWeeklyReview) 60.dp else 0.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Accent glow halo behind the pill — mirrors the Weekly Review pill's radial gradient
        // (DashboardScreen.kt), sized ~26dp taller than the 48dp pill so the glow spreads beyond it.
        val glowBrush = remember(accent.accent) {
            Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.40f), Color.Transparent))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(glowBrush),
        )
        LiquidGlassButton(
            onClick = onExpand,
            modifier = Modifier.fillMaxWidth(),
            tint = accent.accent,
            surfaceColor = Color.White.copy(alpha = 0.10f),
        ) {
            Text(
                text = "✦  Weekly Rebalance",
                style = AppType.cardTitle,
                color = accent.onAccent,
            )
        }
    }
}

// ── Preview (dark theme) ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceReopenPill() {
    RebalanceReopenPill(stackedAboveWeeklyReview = false, onExpand = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceReopenPillStacked() {
    RebalanceReopenPill(stackedAboveWeeklyReview = true, onExpand = {})
}
