package com.zack.recomptracker.ui.liquidglass

import com.zack.recomptracker.ui.theme.LocalAppAccent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

// ── Composition local for tab scale ──────────────────────────────────────────

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

// ── LiquidBottomTab ───────────────────────────────────────────────────────────

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

// ── LiquidBottomTabs ──────────────────────────────────────────────────────────

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val effectiveAccentColor = if (accentColor == Color.Unspecified) LocalAppAccent.current.accentLighter else accentColor
    val isDark = LocalAppColors.current.isDark
    val containerColor =
        if (!isDark) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // Stable state — must NOT use remember(selectedTabIndex) as key because the
        // lambda is recreated on every recomposition when the caller captures an
        // unstable variable (e.g. currentRoute). Using a changing key would create a
        // new MutableIntState each time, breaking the snapshotFlow in the second
        // LaunchedEffect which holds a reference to the old state object.
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        // Drive currentIndex from the external source (e.g. route changes from taps).
        // Reading selectedTabIndex() inside the composable body gives us a stable Int
        // that changes only when the route actually changes, making it a safe
        // LaunchedEffect key.
        val externalIndex = selectedTabIndex()
        LaunchedEffect(externalIndex) {
            if (currentIndex != externalIndex) {
                currentIndex = externalIndex
            }
        }
        // Animate the indicator and notify the caller whenever currentIndex changes.
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // Visible glass row
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // Invisible accent-tinted row (recorded as tabsBackdrop for the indicator)
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(effectiveAccentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // Sliding indicator
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (!isDark) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

// ── LiquidButton ──────────────────────────────────────────────────────────────

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    buttonHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = if (isInteractive) {
                    {
                        val h = size.height
                        val w = size.width
                        // Guard against zero size (first draw during animated enter/exit).
                        if (h > 0f && w > 0f) {
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4f.dp.toPx() / h, progress)

                            val maxOffset = size.minDimension
                            val initialDerivative = 0.05f
                            val off = interactiveHighlight.offset
                            translationX = maxOffset * tanh(initialDerivative * off.x / maxOffset)
                            translationY = maxOffset * tanh(initialDerivative * off.y / maxOffset)

                            val maxDragScale = 4f.dp.toPx() / h
                            val offsetAngle = atan2(off.y, off.x)
                            scaleX = scale + maxDragScale * abs(cos(offsetAngle) * off.x / size.maxDimension) *
                                    (w / h).fastCoerceAtMost(1f)
                            scaleY = scale + maxDragScale * abs(sin(offsetAngle) * off.y / size.maxDimension) *
                                    (h / w).fastCoerceAtMost(1f)
                        }
                    }
                } else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isInteractive) Modifier
                    .then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier)
                else Modifier
            )
            .height(buttonHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ── LiquidSlider ──────────────────────────────────────────────────────────────

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalAppColors.current.isDark
    val accentColor = if (!isDark) Color(0xFF0088FF) else Color(0xFF0091FF)
    val trackColor = if (!isDark) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) onValueChange(targetValue)
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }.collectLatest { v ->
                if (dampedDragAnimation.targetValue != v) dampedDragAnimation.updateValue(v)
            }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val target = (if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                                .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(target)
                            onValueChange(target)
                        }
                    }
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                                if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

// ── LiquidToggle ──────────────────────────────────────────────────────────────

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalAppColors.current.isDark
    val accentColor = if (!isDark) Color(0xFF34C759) else Color(0xFF30D158)
    val trackColor = if (!isDark) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { f ->
            dampedDragAnimation.updateValue(f)
        }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val f = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, f))
                }
                .size(64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val f = dampedDragAnimation.value
                    val padding = 2.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, f)
                        else lerp(-padding, -(padding + dragWidth), f)
                }
                .semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(5f.dp.toPx() * progress, 10f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

// ── Backdrop CompositionLocal ─────────────────────────────────────────────────

private object EmptyBackdrop : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) = Unit
}

// Expose the app-level backdrop so all glass buttons read from it automatically.
// Updating the background source at the app root is enough to change the blur
// content — no plumbing changes needed per screen.
val LocalBackdrop = staticCompositionLocalOf<Backdrop> { EmptyBackdrop }

// ── LiquidGlassButton (flexible base) ────────────────────────────────────────

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = if (LocalAppColors.current.isDark) Color.White.copy(alpha = 0.14f) else LocalAppColors.current.glassPillSurface,
    buttonHeight: Dp = 48.dp,
    /**
     * Cheap look-alike for repeated-in-list call sites. When true, skips the `drawBackdrop` glass
     * layer entirely and draws the same Capsule pill with a translucent fill + hairline border
     * derived from the same [tint]/[surfaceColor] tokens the glass version tints with. The
     * press-scale animation is kept (a plain graphicsLayer transform — cheap). Matches the resting
     * look over the app's static background.
     */
    lite: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (lite) {
        LiteGlassButton(
            onClick = { if (enabled) onClick() },
            modifier = modifier.then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
            enabled = enabled,
            tint = tint,
            surfaceColor = surfaceColor,
            buttonHeight = buttonHeight,
            content = content,
        )
        return
    }
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = LocalBackdrop.current,
        modifier = modifier.then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
        isInteractive = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        buttonHeight = buttonHeight,
        content = content,
    )
}

// ── LiteGlassButton (drawBackdrop-free look-alike for LiquidGlassButton) ──────

// Reproduces the resting glass pill with an opaque/translucent fill instead of a backdrop layer:
//  • tinted (primary) buttons paint the accent at the same 0.75 alpha the glass path fills with,
//    then the surface wash on top — reads as the same violet pill over the app background;
//  • clear (secondary) buttons paint only the surface wash.
// A hairline border (white on dark / black on light, matching the glass rim's subtle edge) and the
// press-scale keep it feeling like the same control at a fraction of the per-frame cost.
@Composable
private fun LiteGlassButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color,
    surfaceColor: Color,
    buttonHeight: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = LocalAppColors.current.isDark
    val hairline = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val pressed = remember { MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.97f else 1f, label = "litePressScale")
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(Capsule())
            .drawBehind {
                if (tint.isSpecified) drawRect(tint.copy(alpha = 0.75f))
                if (surfaceColor.isSpecified) drawRect(surfaceColor)
            }
            .border(1.dp, hairline, Capsule())
            .clickable(
                interactionSource = pressed,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .height(buttonHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ── LiquidPrimaryButton ───────────────────────────────────────────────────────

// Violet-tinted glass pill — visible on dark backgrounds, Apple glass feel.
// The tint drives BlendMode.Hue + a 75 % fill inside LiquidButton so the
// button reads as a solid violet glass pill even without strong backdrop blur.
@Composable
fun LiquidPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lite: Boolean = false,
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        tint = LocalAppAccent.current.accent,
        surfaceColor = Color.White.copy(alpha = 0.08f),
        lite = lite,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LocalAppAccent.current.onAccent)
    }
}

// ── LiquidSecondaryButton ─────────────────────────────────────────────────────

// Clear glass pill for secondary actions — no color tint but enough surface
// opacity to be visible against the dark app background.
@Composable
fun LiquidSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lite: Boolean = false,
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        surfaceColor = if (LocalAppColors.current.isDark) Color.White.copy(alpha = 0.14f) else LocalAppColors.current.glassPillSurface,
        lite = lite,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LocalAppColors.current.textPrimary.copy(alpha = 0.90f))
    }
}

// ── LiquidStepButton ──────────────────────────────────────────────────────────

@Composable
fun LiquidStepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalBackdrop.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val appColors = LocalAppColors.current
    val stepSurface = if (appColors.isDark) Color.White.copy(alpha = 0.18f) else appColors.glassPillSurface

    Box(
        modifier
            .size(32.dp)
            .then(if (!enabled) Modifier.alpha(0.35f) else Modifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                },
                onDrawSurface = { drawRect(stepSurface) }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(if (enabled) highlight.modifier.then(highlight.gestureModifier) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, fontSize = 18.sp, fontWeight = FontWeight.Light, color = LocalAppColors.current.textPrimary)
    }
}

// ── LiquidActionButton ────────────────────────────────────────────────────────

// Compact (not full-width) glass button for inline action pairs, e.g.
// "Add / Cancel" in list rows. isPrimary gives a subtle violet accent.
@Composable
fun LiquidActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    lite: Boolean = false,
) {
    val accent = LocalAppAccent.current
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = if (isPrimary) accent.accent else Color.Unspecified,
        surfaceColor = if (isPrimary) Color.White.copy(alpha = 0.08f)
            else if (LocalAppColors.current.isDark) Color.White.copy(alpha = 0.14f) else LocalAppColors.current.glassPillSurface,
        buttonHeight = if (small) 32.dp else 48.dp,
        lite = lite,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isPrimary) accent.accentLighter else LocalAppColors.current.textPrimary.copy(alpha = 0.85f),
        )
    }
}
