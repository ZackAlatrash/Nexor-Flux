package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/** Top corner radius for the app's centred dialogs — between a card (16) and a sheet (26). */
private val DialogCorner = 24.dp

/**
 * App-themed centred alert/confirm dialog — the glass counterpart to a Material `AlertDialog`.
 *
 * A `Dialog` runs in its own window, so the Kyant backdrop (`LocalBackdrop`) that [FrostedCard]
 * samples is not available here; instead this paints the SAME solid accent-tinted panel as
 * [GlassBottomSheet] (so a dialog and a sheet read as one surface family), plus [FrostedCard]'s
 * hairline border and top sheen for glass character. The centred-modal interaction (scrim,
 * tap-outside / back to dismiss, confirm + cancel) is unchanged from the Material dialog it replaces.
 *
 * Use [body] for a plain message, or [content] for fields/pickers (a `ColumnScope` slot). Provide
 * both a [confirmLabel] and (optionally) a [dismissLabel]; when [dismissLabel] is null the confirm
 * button fills the width (single-action). [isDestructive] paints the confirm button in the app's
 * destructive red ([ErrorRed], matching the swipe-to-delete pill). [confirmEnabled] gates the
 * confirm action (e.g. an empty required field).
 */
@Composable
fun GlassAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissLabel: String? = "Cancel",
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    // Same solid accent-tinted panel as GlassBottomSheet — dark-tinted in dark mode, light-tinted in
    // light mode — so the dialog surface matches the sheet surface across every accent theme.
    val panelColor = lerp(
        if (appColors.isDark) Color.Black else Color.White,
        accent.accent,
        if (appColors.isDark) 0.16f else 0.10f,
    )
    val shape = RoundedCornerShape(DialogCorner)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp)
                .clip(shape)
                .background(panelColor)
                .border(1.dp, appColors.frostedBorder, shape)
                // Top sheen hairline — the same glass-edge character FrostedCard draws.
                .drawBehind {
                    val y = 1.dp.toPx() / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.14f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = AppType.screenTitleCompact,
                color = appColors.textPrimary,
            )

            if (body != null) {
                Spacer(Modifier.height(10.dp))
                Text(text = body, style = AppType.body, color = appColors.textSecondary)
            }

            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissLabel != null) {
                    LiquidActionButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        isPrimary = false,
                    )
                }
                if (isDestructive) {
                    LiquidGlassButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = confirmEnabled,
                        tint = ErrorRed,
                    ) {
                        Text(
                            text = confirmLabel,
                            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                } else {
                    LiquidActionButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isPrimary = true,
                        enabled = confirmEnabled,
                    )
                }
            }
        }
    }
}
