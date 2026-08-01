package com.zack.recomptracker.shared.format

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replacement for `String.format(Locale.US, "%.Nf", value)` — unavailable on Kotlin/Native.
 *
 * Reproduces `java.util.Formatter` exactly: it rounds the SHORTEST decimal representation of the
 * double (what `toString()` gives), HALF_UP on the single digit past the requested precision.
 * It does NOT round the exact binary value — that is why 1.005 formats as "1.01".
 * Negative zero is preserved ("-0.0"), matching Java. Pinned by GoldenFormatTest.
 */
fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    // 1.0 / -0.0 is -Infinity, which is how negative zero is detected.
    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)

    val shortest = abs(value).toString()
    check(!shortest.contains('e', ignoreCase = true)) {
        "formatFixed cannot handle scientific notation: $value"
    }

    val intPart = shortest.substringBefore('.')
    val fracPart = shortest.substringAfter('.', "")

    // One digit string with `decimals` implied decimal places.
    var digits = intPart + fracPart.take(decimals).padEnd(decimals, '0')
    if (fracPart.length > decimals && fracPart[decimals] >= '5') {
        digits = incrementDigits(digits)
    }

    val body = if (decimals == 0) {
        digits
    } else {
        val cut = digits.length - decimals
        "${digits.substring(0, cut).ifEmpty { "0" }}.${digits.substring(cut)}"
    }
    return if (negative) "-$body" else body
}

/** Adds one to a non-negative decimal digit string, growing it on overflow ("99" -> "100"). */
private fun incrementDigits(s: String): String {
    val chars = s.toCharArray()
    var i = chars.lastIndex
    while (i >= 0) {
        if (chars[i] == '9') {
            chars[i] = '0'
            i--
        } else {
            chars[i]++
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}

/**
 * Signed one-decimal string, e.g. "+0.4" / "-0.2". Mirrors `String.format("%+.1f", v)` followed by
 * the ±0.0 normalisation at the original `CoachDetectorSupport.kt:32`.
 */
fun signed1(value: Double): String {
    val body = formatFixed(value, 1)
    if (body == "0.0" || body == "-0.0") return "0.0"
    return if (body.startsWith("-")) body else "+$body"
}

/** Percent with no decimals, e.g. "83%". */
fun pct(value: Double): String = "${value.roundToInt()}%"

/** Bucket a continuous value to the nearest [step] for a stable dedup key. */
fun bucket(value: Double, step: Double, decimals: Int = 2): String {
    val bucketed = (value / step).roundToInt() * step
    val safe = if (bucketed == 0.0) 0.0 else bucketed
    return formatFixed(safe, decimals)
}

fun bucketInt(value: Int, step: Int): String {
    val safeStep = if (step <= 0) 1 else step
    return ((value.toDouble() / safeStep).roundToInt() * safeStep).toString()
}
