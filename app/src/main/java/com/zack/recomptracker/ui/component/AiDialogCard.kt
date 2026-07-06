package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Backdrop-free AI glass card — a dark translucent surface with a top sheen, hairline edge, and the
 * shared [aiEdgeGlow]. Use this (never [AiInsightCard]/[FrostedCard]/[TintedCard]) inside a `Dialog`:
 * a `Dialog` hosts a separate Android window, so components that sample `LocalBackdrop` render
 * incorrectly (or crash) there. This card paints an opaque-enough translucent fill instead of live
 * backdrop refraction, so it renders correctly with no backdrop layer present.
 *
 * Extracted from `WeeklyBriefingOverlay.BriefingGlassCard` — the Weekly Review overlay delegates to
 * this exact recipe so its rendering stays byte-identical.
 *
 * Modifier order matters and mirrors the original: clip → solid translucent surface → top-sheen
 * `drawBehind` → hairline border → [aiEdgeGlow] → optional scroll → padding.
 */
@Composable
fun AiDialogCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    scrollable: Boolean = true,
    maxHeight: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appColors = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius)
    // Neutral dark frosted surface (no accent/purple tint). Backdrop-free, so this is a translucent
    // fill rather than a live-glass refraction; a top sheen + hairline + inner glow give it depth.
    val surface = if (appColors.isDark) Color(0xFF101014).copy(alpha = 0.92f) else Color(0xFFF6F6F8).copy(alpha = 0.95f)
    val hairline = Color.White.copy(alpha = if (appColors.isDark) 0.16f else 0.24f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.14f to Color.Transparent,
                    ),
                )
            }
            .border(0.5.dp, hairline, shape)
            .aiEdgeGlow(borderMode, cornerRadius)
            .then(
                if (scrollable) {
                    Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
            .padding(20.dp),
        content = content,
    )
}
