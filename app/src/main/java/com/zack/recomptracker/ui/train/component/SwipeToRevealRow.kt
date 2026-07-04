package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.ErrorRed
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of the trailing reveal area (the red pill plus its breathing room). */
private val RevealWidth = 96.dp

/** Fixed width of the red liquid-glass Remove pill. */
private val RemoveButtonWidth = 72.dp

/**
 * Wraps [content] with a trailing swipe-to-reveal Remove action.
 *
 * The row content is pinned at the left edge and a **fixed-size** red liquid-glass Remove pill
 * is placed immediately to its right, just off-screen. Swiping left slides both together (via a
 * layout-phase placement, so no per-frame recomposition), bringing the pill into view. The row
 * keeps its own (frosted) material untouched — nothing is layered over or behind it, so there is
 * no bleed-through, no material change, and at rest the content sits exactly at x=0 with the set
 * number fully visible. Tap the pill to remove; tap the open row to close it. When [enabled] is
 * false the row is static — used for the last-set guard (an exercise must keep at least one set).
 */
@Composable
fun SwipeToRevealRow(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { RevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    // derivedStateOf so these only recompose when the boolean flips — the per-frame offset is
    // read in the layout/placement phase below, never during composition.
    val isOpen by remember { derivedStateOf { offsetX.value <= -1f } }
    // Whether the row is revealing at all (mid-drag or settled open). Gates composition of the
    // Remove pill below: at rest (offset == 0) the pill slot is skipped entirely, so ~24 hidden
    // glass pills across a session's set rows never enter composition until actually swiped.
    val isRevealing by remember { derivedStateOf { offsetX.value != 0f } }

    Layout(
        modifier = modifier
            .clipToBounds()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                    }
                },
                onDragStopped = {
                    val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                    scope.launch { offsetX.animateTo(target) }
                },
            ),
        content = {
            // Slot 0 — the row content, plus a tap-to-close overlay while open.
            Box {
                content()
                if (isOpen) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { scope.launch { offsetX.animateTo(0f) } },
                            ),
                    )
                }
            }
            // Slot 1 — the fixed-size Remove pill, composed only while revealing. The reveal
            // distance is driven by the constant [RevealWidth] in the layout phase below (never
            // by measuring this slot), so omitting it at rest doesn't change anchor positions.
            if (isRevealing) {
                Box(contentAlignment = Alignment.Center) {
                    LiquidGlassButton(
                        onClick = {
                            scope.launch {
                                offsetX.animateTo(0f)
                                onRemove()
                            }
                        },
                        enabled = isOpen,
                        tint = ErrorRed,
                        surfaceColor = Color.White.copy(alpha = 0.08f),
                        buttonHeight = 40.dp,
                        // Revealed behind a row mid-drag — backdrop glass there is invisible in
                        // practice, so the cheap look-alike is indistinguishable and far cheaper.
                        lite = true,
                        modifier = Modifier.width(RemoveButtonWidth),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints)
        val rowHeight = contentPlaceable.height
        val pillWidthPx = with(density) { RevealWidth.roundToPx() }
        // Slot 1 (the pill) is only present in [measurables] while isRevealing is true. Measure/
        // place it when it exists; the reveal distance itself (revealPx, used for the drag clamp
        // and offsetX math above) is always RevealWidth regardless — never derived from this
        // measurement — so the reveal anchor is identical whether or not the pill is composed.
        val pillPlaceable = measurables.getOrNull(1)?.measure(Constraints.fixed(pillWidthPx, rowHeight))

        layout(contentPlaceable.width, rowHeight) {
            val dx = offsetX.value.roundToInt()
            contentPlaceable.placeRelative(dx, 0)
            pillPlaceable?.placeRelative(contentPlaceable.width + dx, 0)
        }
    }
}
