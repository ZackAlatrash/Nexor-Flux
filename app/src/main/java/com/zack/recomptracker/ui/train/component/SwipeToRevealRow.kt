package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How far the row slides left to fully reveal the Remove action. */
private val RevealDistance = 96.dp

/** Fixed width of the red liquid-glass Remove pill (never changes with drag distance). */
private val RemoveButtonWidth = 72.dp

/**
 * Wraps [content] with a trailing swipe-to-reveal Remove action.
 *
 * A **fixed-size** red liquid-glass Remove pill sits docked at the trailing edge, behind the
 * row. The row foreground is opaque and slides left to uncover it (it never resizes the pill).
 * Tap the pill to remove; tap the open row to close it. When [enabled] is false the row is
 * static — used for the last-set guard (an exercise must keep at least one set).
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

    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { RevealDistance.toPx() }
    val offsetX = remember { Animatable(0f) }
    // derivedStateOf so this only recomposes when the boolean flips — the per-frame
    // offset is read in the layout phase below, never in composition.
    val isOpen by remember { derivedStateOf { offsetX.value <= -1f } }

    Box(modifier.clipToBounds()) {
        // Fixed-size red liquid-glass Remove pill, docked at the trailing edge. Always laid
        // out behind the row at a constant size; revealed as the opaque row slides over it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 12.dp),
            contentAlignment = Alignment.CenterEnd,
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

        // Foreground row — opaque so the pill stays hidden until swiped. Offset is applied in
        // the layout phase (offset { }) to avoid recomposing on every animation frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(CornerSmall))
                .background(appColors.frostedSurfaceFallback)
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
        ) {
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
    }
}
