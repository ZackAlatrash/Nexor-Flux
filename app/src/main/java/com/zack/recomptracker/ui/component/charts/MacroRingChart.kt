package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.TextMuted
import kotlinx.coroutines.delay

internal fun macroSweepAngles(
    proteinKcal: Float,
    carbsKcal: Float,
    fatKcal: Float,
): Triple<Float, Float, Float> {
    val total = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1f)
    val gapDeg = 2f
    val proteinSweep = (proteinKcal / total * 360f - gapDeg).coerceAtLeast(0f)
    val carbsSweep   = (carbsKcal   / total * 360f - gapDeg).coerceAtLeast(0f)
    val fatSweep     = (fatKcal     / total * 360f - gapDeg).coerceAtLeast(0f)
    return Triple(proteinSweep, carbsSweep, fatSweep)
}

@Composable
fun MacroRingChart(
    proteinKcal: Float,
    carbsKcal: Float,
    fatKcal: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
) {
    val (targetProtein, targetCarbs, targetFat) = remember(proteinKcal, carbsKcal, fatKcal) {
        macroSweepAngles(proteinKcal, carbsKcal, fatKcal)
    }

    var proteinTarget by remember { mutableFloatStateOf(0f) }
    var carbsTarget   by remember { mutableFloatStateOf(0f) }
    var fatTarget     by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(proteinKcal, carbsKcal, fatKcal) {
        proteinTarget = targetProtein
        delay(ChartDefaults.AnimSpec.ringStaggerMs)
        carbsTarget = targetCarbs
        delay(ChartDefaults.AnimSpec.ringStaggerMs)
        fatTarget = targetFat
    }

    val proteinSweep by animateFloatAsState(targetValue = proteinTarget, animationSpec = ChartDefaults.AnimSpec.ringArc, label = "proteinArc")
    val carbsSweep   by animateFloatAsState(targetValue = carbsTarget,   animationSpec = ChartDefaults.AnimSpec.ringArc, label = "carbsArc")
    val fatSweep     by animateFloatAsState(targetValue = fatTarget,     animationSpec = ChartDefaults.AnimSpec.ringArc, label = "fatArc")

    val totalKcal = (proteinKcal + carbsKcal + fatKcal).toInt()

    Box(
        modifier         = modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke  = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset   = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Track ring
            drawArc(
                color      = Color(0xFF1a1a2e),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = strokeWidth.toPx()),
            )

            // Protein arc (starts at top = -90°)
            var currentAngle = -90f
            if (proteinSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Protein,
                    startAngle = currentAngle,
                    sweepAngle = proteinSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
                currentAngle += proteinSweep + 2f
            }

            // Carbs arc
            if (carbsSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Carbs,
                    startAngle = currentAngle,
                    sweepAngle = carbsSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
                currentAngle += carbsSweep + 2f
            }

            // Fat arc
            if (fatSweep > 0f) {
                drawArc(
                    color      = ChartDefaults.MacroColors.Fat,
                    startAngle = currentAngle,
                    sweepAngle = fatSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
            }
        }

        // Center labels
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text          = "$totalKcal",
                fontSize      = 20.sp,
                fontWeight    = FontWeight.Black,
                color         = Color.White,
                letterSpacing = (-0.5).sp,
                lineHeight    = 20.sp,
            )
            Text(
                text     = "kcal",
                fontSize = 9.sp,
                color    = TextMuted,
            )
        }
    }
}
