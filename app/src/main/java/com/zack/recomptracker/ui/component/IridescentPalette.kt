package com.zack.recomptracker.ui.component

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Base full-spectrum iridescent stops for the AI card rim. First == last so the sweep wraps
 * seamlessly. The card animates these by hue rotation (see [hueShifted]) — the geometry never
 * rotates, so the colour flows in place rather than spinning.
 */
val IridescentStops: List<Color> = listOf(
    Color(0xFF8B5CF6),
    Color(0xFFFF6EC7),
    Color(0xFF6EC1FF),
    Color(0xFF6EFFD8),
    Color(0xFFFFB86C),
    Color(0xFFFF6EC7),
    Color(0xFF8B5CF6),
)

/**
 * Pure HSV hue rotation on 0..1 sRGB components. [degrees] wraps mod 360. Returns [r, g, b].
 * Saturationless inputs (grays) are returned unchanged.
 */
fun hueRotatedRgb(r: Float, g: Float, b: Float, degrees: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val v = max
    val s = if (max <= 0f) 0f else delta / max
    var h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    if (h < 0f) h += 360f
    h = (h + degrees) % 360f
    if (h < 0f) h += 360f
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

/** Returns this colour with its hue rotated by [degrees], preserving alpha. */
fun Color.hueShifted(degrees: Float): Color {
    val out = hueRotatedRgb(red, green, blue, degrees)
    return Color(out[0], out[1], out[2], alpha)
}
