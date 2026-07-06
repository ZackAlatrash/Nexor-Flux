package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent

/**
 * Re-openable glass pill for a minimized Weekly Rebalance offer — same recipe as the Weekly Review
 * pill in `DashboardScreen.kt` (radial-gradient glow halo behind a wide [LiquidGlassButton]).
 * `LiquidGlassButton` is safe here (unlike inside [RebalanceOfferOverlay]'s `Dialog`): this pill
 * lives directly in the dashboard's window, so `LocalBackdrop` is present.
 *
 * The caller places this inside a `Box`/`Modifier.fillMaxSize()` region aligned so this composable's
 * own [Modifier.align]-free content reads correctly — in practice the dashboard's
 * `Alignment.BottomCenter`-aligned floating-pill `Box`, mirroring the Weekly Review pill's call site.
 *
 * @param stackedAboveWeeklyReview when both pills are visible at once, pushes this pill up by an
 *   extra 60dp so it stacks above the Weekly Review pill instead of overlapping it.
 */
@Composable
internal fun RebalanceReopenPill(
    visible: Boolean,
    stackedAboveWeeklyReview: Boolean,
    onExpand: () -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val enter = if (animationsEnabled) {
        slideInVertically(animationSpec = tween(220)) { it / 2 } + fadeIn(animationSpec = tween(220))
    } else {
        fadeIn(animationSpec = tween(0))
    }
    val exit = if (animationsEnabled) {
        slideOutVertically(animationSpec = tween(180)) { it / 2 } + fadeOut(animationSpec = tween(180))
    } else {
        fadeOut(animationSpec = tween(0))
    }

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        val accent = LocalAppAccent.current
        Box(
            modifier = Modifier
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
}

// ── Preview (dark theme) ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceReopenPill() {
    RebalanceReopenPill(visible = true, stackedAboveWeeklyReview = false, onExpand = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceReopenPillStacked() {
    RebalanceReopenPill(visible = true, stackedAboveWeeklyReview = true, onExpand = {})
}
