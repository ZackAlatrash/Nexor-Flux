package com.zack.recomptracker.ui.train.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val RevealWidth = 92.dp

/** Fraction of RevealWidth past which the Remove button becomes tappable. */
private const val TapEnableFraction = 0.95f

/** Pixel epsilon below which the reveal panel is not drawn (avoids a 0-width Box). */
private const val RevealEpsilonPx = 0.5f

/**
 * Wraps [content] with a trailing swipe-to-reveal Remove action.
 *
 * Swipe left -> red Remove button is revealed; tap it (only when fully open) -> [onRemove].
 * Tapping the row while open closes it. When [enabled] is false the row is static (used
 * for the last-set guard — an exercise must keep at least one set).
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

    Box(modifier.clipToBounds()) {
        val revealed = -offsetX.value
        if (revealed > RevealEpsilonPx) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { revealed.toDp() })
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(ErrorRed)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = revealed >= revealPx * TapEnableFraction,
                        onClick = {
                            scope.launch {
                                offsetX.animateTo(0f)
                                onRemove()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        ) {
            content()
            if (-offsetX.value > RevealEpsilonPx) {
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
