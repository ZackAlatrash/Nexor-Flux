package com.zack.recomptracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Exposed for unit tests
internal fun calorieScaleMax(zoneUpper: Int): Int = (zoneUpper * 1.2).toInt().coerceAtLeast(1)
internal fun calorieFraction(value: Int, scaleMax: Int): Float =
    (value.toFloat() / scaleMax).coerceIn(0f, 1f)

private val BarFillStart = Color(0xFF3b82f6)
private val BarFillEnd = Color(0xFF2563eb)
// Colour list for the fill gradient — hoisted to a constant since the two colours never
// change. The Brush itself still has to be built in the draw scope: its endX depends on the
// filled width (w * fillFrac), which is only known once the Canvas has a measured size.
private val BarFillColors = listOf(BarFillStart, BarFillEnd)
private val ZoneDark = Color(0xFF0f4a26)
private val ZoneLight = Color(0xFF15803d)
private val ZoneLabel = Color(0xFF22c55e)
private val TrackColor = Color(0xFF222222)
private val SecondaryText = Color(0xFF6b7280)
private val OverColor = Color(0xFFf87171)

/**
 * Progress bar with a diagonal-striped green target zone.
 *
 * @param eaten      calories consumed today
 * @param zoneLower  lower bound of target zone (e.g. 2400)
 * @param zoneUpper  upper bound of target zone (e.g. 2600)
 */
@Composable
fun CalorieZoneBar(
    eaten: Int,
    zoneLower: Int,
    zoneUpper: Int,
    modifier: Modifier = Modifier,
) {
    val scaleMax = calorieScaleMax(zoneUpper)
    val fillFrac = calorieFraction(eaten, scaleMax)
    val zoneLowerFrac = calorieFraction(zoneLower, scaleMax)
    val zoneUpperFrac = calorieFraction(zoneUpper, scaleMax)

    val remaining = (zoneLower - eaten).coerceAtLeast(0)
    val over = (eaten - zoneUpper).coerceAtLeast(0)
    val inZone = eaten in zoneLower..zoneUpper

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val w = size.width
            val h = size.height

            // Track background
            drawRect(color = TrackColor)

            // Diagonal-striped zone
            val zoneLeft = w * zoneLowerFrac
            val zoneRight = w * zoneUpperFrac
            withTransform({
                clipRect(left = zoneLeft, top = 0f, right = zoneRight, bottom = h)
            }) {
                drawRect(
                    color = ZoneLight,
                    topLeft = Offset(zoneLeft, 0f),
                    size = Size(zoneRight - zoneLeft, h),
                )
                val stripeStrokeWidth = 3.dp.toPx()
                val stripeSpacing = 7.dp.toPx()
                var x = zoneLeft - h
                while (x < zoneRight + h) {
                    drawLine(
                        color = ZoneDark,
                        start = Offset(x, h),
                        end = Offset(x + h, 0f),
                        strokeWidth = stripeStrokeWidth,
                    )
                    x += stripeSpacing
                }
            }

            // Blue fill drawn over zone
            if (fillFrac > 0f) {
                clipRect(left = 0f, top = 0f, right = w * fillFrac, bottom = h) {
                    drawRect(
                        // endX depends on the measured width (only known in this draw scope),
                        // so the Brush can't be hoisted out of draw — only the colour list is.
                        brush = Brush.horizontalGradient(
                            colors = BarFillColors,
                            startX = 0f,
                            endX = w * fillFrac,
                        ),
                    )
                }
            }
        }

        // Labels row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$eaten",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        inZone -> "eaten · in zone"
                        over > 0 -> "eaten · +$over over target"
                        else -> "eaten · $remaining to zone"
                    },
                    fontSize = 12.sp,
                    color = if (over > 0) OverColor else SecondaryText,
                )
            }
            Text(
                text = "▌ $zoneLower–$zoneUpper",
                fontSize = 9.sp,
                color = ZoneLabel,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Single macro mini-bar with zone marker, used for P/C/F columns.
 */
@Composable
fun MacroMiniBar(
    label: String,
    eaten: Double,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val scaleMax = (target * 1.2).toInt().coerceAtLeast(1)
    val fillFrac = (eaten.toFloat() / scaleMax).coerceIn(0f, 1f)
    val zoneFrac = (target.toFloat() / scaleMax).coerceIn(0f, 1f)
    val remaining = (target - eaten).coerceAtLeast(0.0)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "${eaten.toInt()}g",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val w = size.width
            val h = size.height
            drawRect(color = TrackColor)
            // Green zone marker at target position
            val zoneX = w * zoneFrac
            drawRect(
                color = ZoneLight,
                topLeft = Offset((zoneX - 3.dp.toPx()).coerceAtLeast(0f), 0f),
                size = Size(3.dp.toPx(), h),
            )
            if (fillFrac > 0f) {
                drawRect(
                    // endX depends on the measured width (only known in this draw scope),
                    // so the Brush can't be hoisted out of draw — only the colour list is.
                    brush = Brush.horizontalGradient(
                        colors = BarFillColors,
                        startX = 0f,
                        endX = w * fillFrac,
                    ),
                    size = Size(w * fillFrac, h),
                )
            }
        }
        Text(
            text = "${remaining.toInt()}g to go",
            fontSize = 10.sp,
            color = SecondaryText,
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = SecondaryText,
        )
    }
}
