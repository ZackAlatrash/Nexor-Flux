package com.zack.recomptracker.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Real liquid-glass segmented toggle — a rounded track holding N equal-width segments, with the
 * **selected** segment rendered as a sliding liquid-glass "thumb" in the same Kyant `backdrop`
 * material the nav bar uses (vibrancy + blur + lens refraction). API-compatible with
 * [com.zack.recomptracker.ui.component.GlassSegmentedToggle] so it is a drop-in replacement.
 *
 * ### Dialog-safe by design
 * This component creates its OWN [rememberLayerBackdrop] internally rather than reading
 * [LocalBackdrop]. That matters because its primary call site is the Weekly Rebalance offer, which
 * lives in a `Dialog` — a separate Android window where `LocalBackdrop` points at the wrong window
 * (it's only provided once, at the app-content root in `RecompApp.kt`). By capturing the track's own
 * tinted surface as the backdrop and sampling it for the thumb, the glass works in any window.
 *
 * The `layerBackdrop` capture (the track surface) and the `drawBackdrop` sample (the thumb) are kept
 * **inside this one composable** with no external `graphicsLayer` between them, per the warning in
 * `RebalanceReopenPill.kt`: an intervening isolated layer defeats backdrop sampling. The thumb's
 * slide is applied via the thumb's own `graphicsLayer { translationX }`, which is part of the
 * `drawBackdrop` chain (like the nav-bar indicator's `layerBlock`), not a wrapper around it.
 *
 * ### Which path is active: LITE (default)
 * [useLiveBackdrop] gates the two rendering paths and defaults to **false** (the lite path):
 *  - **LITE (active):** the blessed backdrop-free glass thumb from `LiteGlassButton` — a translucent
 *    accent-tinted fill + rounded capsule + hairline border. Guaranteed to render as "glass"
 *    everywhere: inside the Dialog, in `@Preview`, and on API < 31 where blur is a no-op. This is the
 *    safe default because the offer Dialog deliberately avoids live backdrops (see
 *    `RebalanceOfferOverlay.kt`) and live refraction can't be visually verified from a headless build.
 *  - **LIVE (flip [useLiveBackdrop] = true):** the real self-contained Kyant refraction described
 *    above (`vibrancy(); blur(8dp); lens(24dp, 24dp)`), mirroring the nav-bar indicator. Prefer this
 *    once verified on-device; it is a one-flag change.
 *
 * Both paths animate the thumb sliding to the selected segment (spring on its x-offset), gated by
 * [rememberAnimationsEnabled] for reduce-motion.
 *
 * @param compact thin variant (default) — ~30–32dp tall for the offer dials; `false` ≈ 40dp.
 */
@Composable
fun LiquidSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    // Real self-contained Kyant backdrop refraction (the app's nav-bar liquid-glass material), per the
    // user's explicit ask. If it ever renders wrong on a device, flip to false for the lite tinted-glass
    // fallback — see the KDoc "Which path is active" section.
    val useLiveBackdrop = true

    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val isDark = appColors.isDark

    val count = options.size.coerceAtLeast(1)
    val safeIndex = selectedIndex.coerceIn(0, count - 1)

    // Thin knob: segment vertical inset + track inset. compact ≈ 30–32dp, regular ≈ 40dp.
    val segmentVerticalPadding: Dp = if (compact) 5.dp else 8.dp
    val trackPadding: Dp = if (compact) 2.dp else 3.dp
    val trackShape = RoundedCornerShape(if (compact) 11.dp else 13.dp)
    val thumbRadius: Dp = if (compact) 9.dp else 10.dp
    val thumbShape = RoundedCornerShape(thumbRadius)

    // Fixed outer height so the toggle is self-sizing and parent-independent. Its primary call site
    // (the Weekly Rebalance offer) lives inside a vertically-scrolling Dialog, which measures children
    // with an UNBOUNDED height. Without a fixed height the fill-height thumb would receive that
    // infinite height and Constraints packing would throw. A segmented toggle has a standard height
    // anyway, so this is the correct model, not a workaround. compact ≈ 32dp, regular ≈ 44dp.
    val trackHeight: Dp = if (compact) 32.dp else 44.dp

    // Track wash — a quiet neutral glass so the accent thumb reads on top of it.
    val trackColor = appColors.cardSurface
    // Accent-tinted container the thumb paints (matches the flat toggle's selected pill intent).
    val thumbSurface = accent.accent.copy(alpha = 0.22f)
    val hairline = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    // Animate the thumb's slide across segments (fractional index → px offset at layout time).
    val animationsEnabled = rememberAnimationsEnabled()
    val targetFraction = safeIndex.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (animationsEnabled) spring(dampingRatio = 0.75f, stiffness = 400f)
        else spring(stiffness = Float.MAX_VALUE),
        label = "liquidSegmentThumb",
    )

    // The tinted track background is captured into this backdrop; the thumb samples it (live path).
    val backdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(trackShape)
            .background(trackColor)
            .border(1.dp, appColors.cardBorder, trackShape)
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Segment width in px (track inner width already excludes trackPadding via BoxWithConstraints).
        val thumbWidthPx = constraints.maxWidth.toFloat() / count

        // ── Layer 1: the tinted surface, captured as `backdrop` for the live thumb to sample.
        // Painted only on the live path (the lite thumb draws its own fill and needs no capture).
        // No external graphicsLayer sits between this capture and the thumb's drawBackdrop below.
        if (useLiveBackdrop) {
            Box(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
                    .background(thumbSurface),
            )
        }

        // ── Layer 2: the sliding thumb. translationX is applied on the thumb's OWN graphicsLayer,
        // which is part of the drawBackdrop chain (live) / a cheap transform (lite) — never a
        // wrapper isolating the capture from the sample.
        val thumbSlide: Modifier = Modifier
            .layout { measurable, _ ->
                // Width = one segment; height = full track inner height (fill).
                val w = thumbWidthPx.fastRoundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minWidth = w, maxWidth = w),
                )
                layout(placeable.width, placeable.height) {
                    placeable.place((animatedFraction * thumbWidthPx).fastRoundToInt(), 0)
                }
            }

        if (useLiveBackdrop) {
            Box(
                thumbSlide
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { thumbShape },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                        onDrawSurface = {
                            drawRect(thumbSurface)
                        },
                    )
                    .border(1.dp, accent.tintedBorder, thumbShape)
                    .fillMaxHeightSafe(),
            )
        } else {
            // LITE thumb — the blessed backdrop-free glass fill (LiteGlassButton recipe): translucent
            // accent tint + rounded capsule + hairline. Guaranteed glass in the Dialog and previews.
            Box(
                thumbSlide
                    .clip(thumbShape)
                    .background(thumbSurface)
                    .border(1.dp, accent.tintedBorder, thumbShape)
                    .fillMaxHeightSafe(),
            )
        }

        // ── Layer 3: the labels, above the thumb. Each segment is selectable; the selected one
        // reads in accent ink + heavier weight, mirroring the flat GlassSegmentedToggle.
        Row(Modifier.matchParentSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == safeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(thumbShape)
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = segmentVerticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = (if (compact) AppType.label else AppType.body).copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (selected) accent.inkLight else appColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Fills the parent's height without pulling in `fillMaxHeight` at every call site — the thumb must
 * match the track's inner height so the glass pill spans the full segment vertically.
 */
private fun Modifier.fillMaxHeightSafe(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        // Only pin to the parent height when it is actually BOUNDED. Inside a vertically-scrolling
        // container maxHeight is Constraints.Infinity (Int.MAX_VALUE); copying that into a fixed
        // constraint overflows Constraints packing and throws
        // ("Can't represent a width of … and height of 2147483647 in Constraints"). When unbounded,
        // fall back to the child's natural height rather than crash. The track's fixed height means
        // this normally takes the bounded branch; the guard is defense-in-depth.
        val placeable = if (constraints.hasBoundedHeight) {
            val h = constraints.maxHeight
            measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        } else {
            measurable.measure(constraints)
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    },
)

// ── Preview (dark theme) ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818, widthDp = 360)
@Composable
private fun PreviewLiquidSegmentedToggle() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        Text("compact = true", style = AppType.metaLabel, color = Color.White)
        LiquidSegmentedToggle(
            options = listOf("Eat less", "Balanced", "Move more"),
            selectedIndex = 0,
            onSelect = {},
            compact = true,
        )
        LiquidSegmentedToggle(
            options = listOf("Eat less", "Balanced", "Move more"),
            selectedIndex = 1,
            onSelect = {},
            compact = true,
        )
        Text("compact = false", style = AppType.metaLabel, color = Color.White)
        LiquidSegmentedToggle(
            options = listOf("Eat less", "Balanced", "Move more"),
            selectedIndex = 2,
            onSelect = {},
            compact = false,
        )
    }
}
