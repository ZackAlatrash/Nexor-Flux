package com.zack.recomptracker.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Small liquid-glass icon button (nav-bar material: vibrancy + blur over a neutral pill). The icon
 * is a vector centred in the pill. If [onClick] is null the button is purely decorative and the
 * parent owns the tap (used by [AiExpandToggle], where the whole header row is clickable).
 *
 * Shared across the AI cards so every glass control looks identical.
 */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    rotation: Float = 0f,
) {
    val backdrop = LocalBackdrop.current
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val surface = if (appColors.isDark) Color.White.copy(alpha = 0.14f) else appColors.glassPillSurface
    Box(
        modifier = Modifier
            .size(30.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent.inkLight,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}

/**
 * The AI cards' expand/collapse affordance: a glass chevron that points down when [collapsed] and
 * rotates 180° up when expanded. Decorative (the parent header row owns the tap).
 */
@Composable
internal fun AiExpandToggle(collapsed: Boolean) {
    val rot by animateFloatAsState(if (collapsed) 0f else 180f, tween(400), label = "chev")
    GlassIconButton(
        icon = Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        onClick = null,
        rotation = rot,
    )
}
