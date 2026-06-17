package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
 * The row and a **fixed-size** red liquid-glass Remove pill live in one horizontal strip: the
 * pill sits just off-screen to the right of the content. Swiping left slides the whole strip,
 * bringing the pill into view — the row keeps its own (frosted) material untouched and nothing
 * is layered behind it, so there is no bleed-through and no material change. Tap the pill to
 * remove; tap the open row to close it. When [enabled] is false the row is static — used for
 * the last-set guard (an exercise must keep at least one set).
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
    // derivedStateOf so this only recomposes when the boolean flips — the per-frame offset is
    // read in the layout phase (offset { }) below, never during composition.
    val isOpen by remember { derivedStateOf { offsetX.value <= -1f } }

    BoxWithConstraints(modifier.clipToBounds()) {
        val rowWidth = maxWidth

        Row(
            modifier = Modifier
                // Strip = row width + the trailing reveal area; the extra hangs off the right
                // edge and is clipped until the strip is dragged left.
                .requiredWidth(rowWidth + RevealWidth)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(rowWidth)) {
                content()
                if (isOpen) {
                    // Absorb taps on the open row → close instead of hitting inner clickables.
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

            // Trailing reveal area holding the fixed-size red liquid-glass pill.
            Box(
                modifier = Modifier
                    .width(RevealWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
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
    }
}
