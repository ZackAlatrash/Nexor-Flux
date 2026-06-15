package com.zack.recomptracker.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.LocalAppColors

enum class AiBorderMode {
    Preparing,
    Generating,
    Ready,
    Static,
}

/**
 * Liquid-glass AI card: exact nav-bar glass recipe (vibrancy + blur + lens) with an [aiEdgeGlow]
 * halo layer sitting BEHIND the glass. Supports a [collapsed] pill shape (Capsule) in addition to
 * the default rounded-card shape.
 */
@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val cornerDp = if (collapsed) CornerPill else CornerCard
    val isDark = LocalAppColors.current.isDark
    val containerColor =
        if (isDark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.40f)
    val shape: Shape = if (collapsed) Capsule() else RoundedCornerShape(cornerDp)
    val hairline = Color.White.copy(alpha = if (isDark) 0.16f else 0.24f)

    // Glass layer — exact nav-bar recipe, with a top sheen + hairline for glass character and the
    // inner edge glow drawn on top (clipped inside the shape so it reads as light inside the glass).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { if (collapsed) Capsule() else RoundedRectangle(cornerDp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                onDrawSurface = {
                    drawRect(containerColor)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.14f),
                            0.16f to Color.Transparent,
                        ),
                    )
                },
            )
            .border(0.5.dp, hairline, shape)
            .aiEdgeGlow(borderMode, cornerDp)
            .padding(contentPadding),
        content = content,
    )
}


@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewCollapsedPill() {
    AiInsightCard(borderMode = AiBorderMode.Static, collapsed = true, contentPadding = 12.dp) {
        androidx.compose.material3.Text("You're 24g under protein today", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewStatic() {
    AiInsightCard(borderMode = AiBorderMode.Static) {
        androidx.compose.material3.Text("Static iridescent rim", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewPreparing() {
    AiInsightCard(borderMode = AiBorderMode.Preparing) {
        androidx.compose.material3.Text("Preparing model…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewGenerating() {
    AiInsightCard(borderMode = AiBorderMode.Generating) {
        androidx.compose.material3.Text("Your weight has been trending down…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewReady() {
    AiInsightCard(borderMode = AiBorderMode.Ready) {
        androidx.compose.material3.Text("Weight, waist, and performance are all stable.", color = Color.White)
    }
}
