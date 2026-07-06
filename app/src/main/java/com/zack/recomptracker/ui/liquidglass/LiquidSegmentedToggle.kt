package com.zack.recomptracker.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Real liquid-glass segmented toggle — a rounded track holding N equal-width segments, with the
 * **selected** segment rendered as a sliding liquid-glass "thumb" built from the same Kyant `backdrop`
 * material as the nav bar's indicator ([com.zack.recomptracker.ui.liquidglass.LiquidBottomTabs]).
 * API-compatible with [com.zack.recomptracker.ui.component.GlassSegmentedToggle] so it is a drop-in
 * replacement.
 *
 * ### How the glass reads (the nav-bar trick)
 * The nav bar's indicator doesn't refract a flat fill — it samples a tinted *copy of the tab labels*
 * underneath it, so the selected label appears to sit **inside** the glass. This toggle does the same:
 *  - a `matchParentSize`, `alpha(0f)` **ghost** row of the labels (coloured [LocalAppAccent.accentLighter])
 *    is recorded into a local [rememberLayerBackdrop];
 *  - the sliding thumb `drawBackdrop`-samples that ghost with a **gentle** `lens` (+ chromatic edge),
 *    a `Highlight.Default` rim and a soft `Shadow` — no heavy blur. (The earlier `blur(8) + lens(24)`
 *    over a flat accent fill on a ~28dp pill just turned to mud.)
 *
 * ### Dialog-safe by design
 * The backdrop is a LOCAL [rememberLayerBackdrop], never [LocalBackdrop]: the primary call site is the
 * Weekly Rebalance offer, a `Dialog` (separate Android window) where `LocalBackdrop` points at the
 * wrong window. Capturing the ghost inside this one composable — with no external `graphicsLayer`
 * between the capture and the thumb's `drawBackdrop` sample — makes the glass work in any window
 * (per the warning in `RebalanceReopenPill.kt`: an intervening isolated layer defeats sampling).
 *
 * ### Fixed height (crash safety)
 * The track has a fixed outer height, so children always get a BOUNDED height constraint even inside a
 * vertically-scrolling parent (the offer Dialog scrolls). Without it the fill-height thumb once received
 * `Constraints.Infinity` and threw ("Can't represent … height of 2147483647 in Constraints").
 *
 * ### Which path is active: LIVE (default)
 * [useLiveBackdrop] gates the two paths and is **true** (the live nav-bar refraction above). Flip it to
 * `false` for the **LITE** fallback — a backdrop-free translucent accent pill under readable labels
 * (`LiteGlassButton` recipe), guaranteed to render in `@Preview` and on API < 31 where blur is a no-op.
 * Both paths spring-animate the thumb's slide, gated by [rememberAnimationsEnabled] for reduce-motion.
 *
 * @param compact thin variant (default) — 32dp tall for the offer dials; `false` = 44dp.
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

    // Track wash — a quiet neutral glass the thumb reads on top of.
    val trackColor = appColors.cardSurface

    // Animate the thumb's slide across segments (fractional index → px offset at layout time).
    val animationsEnabled = rememberAnimationsEnabled()
    val animatedFraction by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = if (animationsEnabled) spring(dampingRatio = 0.75f, stiffness = 400f)
        else spring(stiffness = Float.MAX_VALUE),
        label = "liquidSegmentThumb",
    )

    // An accent-tinted GHOST of the labels is recorded into this backdrop; the sliding glass thumb
    // samples it so the selected label refracts *through* the glass — the nav bar's exact trick (its
    // indicator samples a tinted copy of the tab labels). Sampling real content, not a flat fill, is
    // what makes the thumb read as liquid glass instead of the muddy blurred blob we had before.
    val labelsBackdrop = rememberLayerBackdrop()

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

        // Thumb slide: width = one segment, placed at the animated fractional offset. Placement (not
        // an external graphicsLayer wrapper) so nothing isolates the backdrop capture from the sample.
        val thumbSlide: Modifier = Modifier.layout { measurable, _ ->
            val w = thumbWidthPx.fastRoundToInt()
            val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
            layout(placeable.width, placeable.height) {
                placeable.place((animatedFraction * thumbWidthPx).fastRoundToInt(), 0)
            }
        }

        if (useLiveBackdrop) {
            // ── Layer 1 (bottom): visible labels + tap targets. Unselected read directly; the
            // selected one sits under the glass thumb and is shown by the refracted ghost instead, so
            // it too is drawn muted here (the glass supplies its accent identity).
            Row(Modifier.matchParentSize()) {
                options.forEachIndexed { index, label ->
                    SegmentCell(
                        label = label,
                        selected = index == safeIndex,
                        compact = compact,
                        verticalPadding = segmentVerticalPadding,
                        color = appColors.textMuted,
                        onClick = { onSelect(index) },
                    )
                }
            }

            // ── Layer 2: an invisible accent-tinted copy of the labels, recorded as `labelsBackdrop`
            // for the thumb to refract. alpha(0) hides it on screen; the layer is still captured.
            // clearAndSetSemantics drops this duplicate label row from the a11y tree (nav-bar pattern).
            Row(Modifier.matchParentSize().clearAndSetSemantics {}.alpha(0f).layerBackdrop(labelsBackdrop)) {
                options.forEach { label ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(vertical = segmentVerticalPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = (if (compact) AppType.label else AppType.body)
                                .copy(fontWeight = FontWeight.SemiBold),
                            color = accent.accentLighter,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Layer 3 (top): the sliding liquid-glass thumb — a gentle lens + chromatic edge + a
            // glass rim, refracting the tinted label ghost beneath. The nav-bar indicator recipe,
            // thinner: no heavy blur, a small lens (blur(8)+lens(24) on a ~28dp pill was the mud).
            Box(
                thumbSlide
                    .drawBackdrop(
                        backdrop = labelsBackdrop,
                        shape = { thumbShape },
                        effects = {
                            lens(
                                if (compact) 6f.dp.toPx() else 9f.dp.toPx(),
                                if (compact) 10f.dp.toPx() else 13f.dp.toPx(),
                                chromaticAberration = true,
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.06f)) },
                        // Subtle accent identity — light enough that the refracted label shows through.
                        onDrawSurface = { drawRect(accent.accent.copy(alpha = 0.14f)) },
                    )
                    .border(1.dp, accent.tintedBorder, thumbShape)
                    .fillMaxHeightSafe(),
            )
        } else {
            // LITE: a solid accent-tinted glass pill under readable labels (no backdrop sampling) —
            // the guaranteed-render fallback for previews / API < 31 / if live refraction misbehaves.
            Box(
                thumbSlide
                    .clip(thumbShape)
                    .background(accent.accent.copy(alpha = 0.22f))
                    .border(1.dp, accent.tintedBorder, thumbShape)
                    .fillMaxHeightSafe(),
            )
            Row(Modifier.matchParentSize()) {
                options.forEachIndexed { index, label ->
                    val selected = index == safeIndex
                    SegmentCell(
                        label = label,
                        selected = selected,
                        compact = compact,
                        verticalPadding = segmentVerticalPadding,
                        color = if (selected) accent.inkLight else appColors.textMuted,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * One segment: a centered [label] filling its share of the track width and the full track height, with
 * a transparent tap target. Shared by the live and lite paths so every segment measures and hit-tests
 * identically.
 */
@Composable
private fun RowScope.SegmentCell(
    label: String,
    selected: Boolean,
    compact: Boolean,
    verticalPadding: Dp,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = (if (compact) AppType.label else AppType.body).copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = color,
            textAlign = TextAlign.Center,
        )
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
