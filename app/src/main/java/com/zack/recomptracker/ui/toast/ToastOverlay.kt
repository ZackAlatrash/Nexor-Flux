package com.zack.recomptracker.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import kotlinx.coroutines.delay

@Composable
fun ToastOverlay(modifier: Modifier = Modifier) {
    val controller = LocalToastController.current
    var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.messages.collect { message ->
            if (visible) {
                visible = false
                delay(200L)
            }
            currentToast = message
            visible = true
            delay(3000L)
            visible = false
            delay(300L)
            currentToast = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = FloatingNavHeight + 8.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
            exit  = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180)),
        ) {
            currentToast?.let { toast ->
                ToastItem(toast = toast, onDismiss = { visible = false })
            }
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastMessage,
    onDismiss: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val bgColor = when (toast.type) {
        ToastType.Success -> accent.accent.copy(alpha = 0.22f)
        ToastType.Error   -> Color(0x2EFB7185)
        ToastType.Info    -> Color(0x14FFFFFF)
    }
    val borderColor = when (toast.type) {
        ToastType.Success -> accent.accent.copy(alpha = 0.45f)
        ToastType.Error   -> Color(0x66FB7185)
        ToastType.Info    -> Color(0x26FFFFFF)
    }
    val iconText = when (toast.type) {
        ToastType.Success -> "✓"
        ToastType.Error   -> "✕"
        ToastType.Info    -> "ℹ"
    }
    val iconTint = when (toast.type) {
        ToastType.Success -> accent.accentLighter
        ToastType.Error   -> ErrorRed
        ToastType.Info    -> Color(0x99FFFFFF)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerPill))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(CornerPill))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = iconText,
            color = iconTint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = toast.text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (toast.actionLabel != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = toast.actionLabel,
                color = accent.accentLighter,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    toast.onAction?.invoke()
                    onDismiss()
                },
            )
        }
    }
}
