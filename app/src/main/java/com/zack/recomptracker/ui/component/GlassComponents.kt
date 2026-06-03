package com.zack.recomptracker.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.FeaturedBorder
import com.zack.recomptracker.ui.theme.FeaturedSurface
import com.zack.recomptracker.ui.theme.FrostedBorder
import com.zack.recomptracker.ui.theme.FrostedSurface
import com.zack.recomptracker.ui.theme.FrostedSurfaceFallback
import com.zack.recomptracker.ui.theme.NavLogEnd
import com.zack.recomptracker.ui.theme.NavLogStart
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet400
import com.zack.recomptracker.ui.theme.Violet500

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
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FrostedSurface
    } else {
        FrostedSurfaceFallback
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .drawBehind {
                // Dark frosted fill
                drawRect(color = surface)
                // Top-edge catchlight — the glass shimmer
                val shimmerY = 1.dp.toPx() / 2f
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x4CFFFFFF),
                            Color(0x4CFFFFFF),
                            Color.Transparent,
                        ),
                        startX = size.width * 0.12f,
                        endX   = size.width * 0.88f,
                    ),
                    start       = Offset(0f, shimmerY),
                    end         = Offset(size.width, shimmerY),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(1.dp, FrostedBorder, RoundedCornerShape(CornerCard))
            .padding(16.dp),
        content = content,
    )
}

// ── Tinted Card (reserved — AI features only, zero call sites) ────────────────

@Composable
fun TintedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .drawBehind {
                drawRect(color = FeaturedSurface)
                val shimmerY = 1.dp.toPx() / 2f
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            // violet shimmer — intentional for the branded AI tier
                            FeaturedBorder,
                            FeaturedBorder,
                            Color.Transparent,
                        ),
                        startX = size.width * 0.10f,
                        endX   = size.width * 0.90f,
                    ),
                    start       = Offset(0f, shimmerY),
                    end         = Offset(size.width, shimmerY),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(1.dp, FeaturedBorder, RoundedCornerShape(CornerCard))
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1F8B5CF6))
            .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Violet400,
        )
    }
}

// ── Violet Gradient Button ────────────────────────────────────────────────────

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(NavLogStart, NavLogEnd),
                ),
            )
            .drawBehind {
                // Button glow
                drawRect(color = Color(0x00000000))
            }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
    }
}

// Clickable version using Modifier.clickable isn't available here without
// additional setup — the caller wraps GlassButton in a clickable Box or uses
// a Button with custom colors instead. We expose a helper for that:

@Composable
fun GlassButtonClickable(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(NavLogStart, NavLogEnd)),
                    RoundedCornerShape(12.dp),
                )
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
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
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Color(0x8C8B5CF6) else Color(0x1AFFFFFF)
    val bgColor = if (focused) Color(0x148B5CF6) else Color(0x40000000)

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
            cursorBrush = SolidColor(Violet300),
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x248B5CF6))
                    .border(1.dp, Color(0x388B5CF6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = value.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Violet400,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Violet300,
                activeTrackColor = Violet400,
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
                checkedTrackColor = Violet500,
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
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Color(0x8C8B5CF6) else Color(0x1AFFFFFF)
    val bgColor = if (focused) Color(0x148B5CF6) else Color(0x40000000)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = false,
        minLines = minLines,
        textStyle = TextStyle(
            fontSize = 13.sp,
            color = Color.White,
        ),
        cursorBrush = SolidColor(Violet300),
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
            StepButton(symbol = "−", enabled = value > range.first) {
                onValueChange((value - 1).coerceAtLeast(range.first))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x248B5CF6))
                    .border(1.dp, Color(0x388B5CF6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Violet400,
                )
            }
            StepButton(symbol = "+", enabled = value < range.last) {
                onValueChange((value + 1).coerceAtMost(range.last))
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 16.sp,
            color = if (enabled) Color(0x80FFFFFF) else Color(0x28FFFFFF),
            lineHeight = 16.sp,
        )
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
    Box(
        modifier = modifier
            .size(size.dp)
            .offset(x = offsetX.dp, y = offsetY.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x338B5CF6), Color.Transparent),
                ),
            ),
    )
}
