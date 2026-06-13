package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.liquidglass.LiquidStepButton
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.FrostedBorder
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted

// ── Neutral Card (workhorse — list rows, menus, form containers) ──────────────

@Composable
fun NeutralCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}

// ── Frosted Card (M3 Expressive — primary data cards, featured charts) ────────

@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    var cardWidth by remember { mutableIntStateOf(0) }
    val shimmerBrush = remember(cardWidth) {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color(0x33FFFFFF), Color.Transparent),
            startX = cardWidth * 0.12f,
            endX   = cardWidth * 0.88f,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .onSizeChanged { cardWidth = it.width }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(CornerCard) },
                effects = {
                    vibrancy()
                    blur(12f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color(0x33000000))
                    val shimmerY = 1.dp.toPx() / 2f
                    drawLine(
                        brush = shimmerBrush,
                        start       = Offset(0f, shimmerY),
                        end         = Offset(size.width, shimmerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            )
            .border(1.dp, FrostedBorder, RoundedCornerShape(CornerCard))
            .padding(contentPadding),
        content = content,
    )
}

// ── Tinted Card (reserved — AI features only, zero call sites) ────────────────

@Composable
fun TintedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val accent = LocalAppAccent.current
    var cardWidth by remember { mutableIntStateOf(0) }
    val shimmerBrush = remember(cardWidth, accent.tintedBorder) {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, accent.tintedBorder, accent.tintedBorder, Color.Transparent),
            startX = cardWidth * 0.10f,
            endX   = cardWidth * 0.90f,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .onSizeChanged { cardWidth = it.width }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(CornerCard) },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(accent.tintedSurface)
                    val shimmerY = 1.dp.toPx() / 2f
                    drawLine(
                        brush = shimmerBrush,
                        start       = Offset(0f, shimmerY),
                        end         = Offset(size.width, shimmerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            )
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}

// ── Section Label ─────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = TextFaint,
        letterSpacing = 0.14.sp,
        modifier = modifier,
    )
}

// ── Violet Badge ──────────────────────────────────────────────────────────────

@Composable
fun VioletBadge(text: String, modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    BadgePill(
        text = text,
        bg = accent.accent.copy(alpha = 0.12f),
        border = accent.accent.copy(alpha = 0.20f),
        textColor = accent.accentLight,
        modifier = modifier,
    )
}

/**
 * Status-colored variant of [VioletBadge] used for the Trends verdict pills.
 * Keeps the same shape / padding / typography as the default violet badge; only the
 * color trio changes per [PillStatus].
 */
@Composable
fun VioletBadge(status: PillStatus, text: String, modifier: Modifier = Modifier) {
    when (status) {
        PillStatus.NEUTRAL -> VioletBadge(text = text, modifier = modifier)
        PillStatus.GOOD -> {
            val green = Color(0xFF86efac)
            BadgePill(
                text = text,
                bg = green.copy(alpha = 0.12f),
                border = green.copy(alpha = 0.28f),
                textColor = green,
                modifier = modifier,
            )
        }
        PillStatus.CAUTION -> {
            val amber = Color(0xFFfbbf24)
            BadgePill(
                text = text,
                bg = amber.copy(alpha = 0.12f),
                border = amber.copy(alpha = 0.28f),
                textColor = amber,
                modifier = modifier,
            )
        }
        PillStatus.OFF_TRACK -> BadgePill(
            text = text,
            bg = ErrorRed.copy(alpha = 0.12f),
            border = ErrorRed.copy(alpha = 0.28f),
            textColor = ErrorRed,
            modifier = modifier,
        )
    }
}

@Composable
private fun BadgePill(
    text: String,
    bg: Color,
    border: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}


// ── Glass Input Field ─────────────────────────────────────────────────────────

@Composable
fun GlassInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val accent = LocalAppAccent.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.55f) else Color(0x1AFFFFFF)
    val bgColor = if (focused) accent.tintedSurface else Color(0x40000000)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x59FFFFFF),
            letterSpacing = 0.10.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
            ),
            cursorBrush = SolidColor(accent.accentLighter),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { innerField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(modifier = Modifier.weight(1f)) { innerField() }
                    if (unit != null) {
                        Text(
                            text = unit,
                            fontSize = 11.sp,
                            color = Color(0x4CFFFFFF),
                        )
                    }
                }
            },
        )
    }
}

// ── Violet Slider ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VioletSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..10,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xA6FFFFFF),
            )
            val accent = LocalAppAccent.current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = value.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent.accentLight,
                )
            }
        }
        val accent = LocalAppAccent.current
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accent.accentLighter,
                activeTrackColor = accent.accentLight,
                inactiveTrackColor = Color(0x12FFFFFF),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${range.first}", fontSize = 8.sp, color = Color(0x2EFFFFFF))
            Text("5", fontSize = 8.sp, color = Color(0x2EFFFFFF))
            Text("${range.last}", fontSize = 8.sp, color = Color(0x2EFFFFFF))
        }
    }
}

// ── Violet Toggle ─────────────────────────────────────────────────────────────

@Composable
fun VioletToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xBFFFFFFF),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LocalAppAccent.current.accent,
                uncheckedThumbColor = Color(0x80FFFFFF),
                uncheckedTrackColor = Color(0x1AFFFFFF),
                uncheckedBorderColor = Color(0x26FFFFFF),
            ),
        )
    }
}

// ── Glass Textarea ────────────────────────────────────────────────────────────

@Composable
fun GlassTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
) {
    val accent = LocalAppAccent.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) accent.accent.copy(alpha = 0.55f) else Color(0x1AFFFFFF)
    val bgColor = if (focused) accent.tintedSurface else Color(0x40000000)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = false,
        minLines = minLines,
        textStyle = TextStyle(
            fontSize = 13.sp,
            color = Color.White,
        ),
        cursorBrush = SolidColor(accent.accentLighter),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { innerField ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = Color(0x40FFFFFF))
                }
                innerField()
            }
        },
    )
}

// ── Score Stepper (compact +/- selector for 1–10 scores) ─────────────────────

@Composable
fun ScoreStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..10,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xA6FFFFFF),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidStepButton(
                symbol = "−",
                enabled = value > range.first,
                onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
            )
            val accent = LocalAppAccent.current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.tintedBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent.accentLight,
                )
            }
            LiquidStepButton(
                symbol = "+",
                enabled = value < range.last,
                onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
            )
        }
    }
}


// ── Ambient Orb Helper ────────────────────────────────────────────────────────

@Composable
fun AmbientOrb(
    offsetX: Int,
    offsetY: Int,
    size: Int = 300,
    modifier: Modifier = Modifier,
) {
    val orbBrush = Brush.radialGradient(
        listOf(LocalAppAccent.current.accent.copy(alpha = 0.20f), Color.Transparent)
    )
    Box(
        modifier = modifier
            .size(size.dp)
            .offset(x = offsetX.dp, y = offsetY.dp)
            .background(orbBrush),
    )
}
