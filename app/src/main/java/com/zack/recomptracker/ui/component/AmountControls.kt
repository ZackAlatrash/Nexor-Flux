package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/** Whether an amount is entered as servings or grams. */
enum class AmountMode { SERVINGS, GRAMS }

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(CornerSmall))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = appColors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun AmountStepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    caption: String,
    suffix: String,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(Icons.Filled.Remove, "Decrease", onMinus)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(accent.tintedSurface)
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerSmall))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.3).sp,
                    ),
                    cursorBrush = SolidColor(accent.accentLighter),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) { innerField() }
                            if (suffix.isNotBlank()) {
                                Text(suffix, style = AppType.body, color = appColors.textMuted)
                            }
                        }
                    },
                )
            }
            if (caption.isNotBlank()) {
                Text(
                    caption,
                    style = AppType.metaLabel,
                    color = appColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        StepButton(Icons.Filled.Add, "Increase", onPlus)
    }
}

@Composable
fun AmountPreviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CornerSmall))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerSmall))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = AppType.statValueSmall, color = appColors.textPrimary)
        Text(label.uppercase(), style = AppType.metaLabel, color = appColors.textMuted)
    }
}
