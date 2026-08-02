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
 *
 * `toString()` switches to scientific notation outside `[1e-3, 1e7)` where `%.Nf` never does, so
 * [plainDecimalString] normalises that away first — see its doc for the one known divergence.
 */
fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    // 1.0 / -0.0 is -Infinity, which is how negative zero is detected.
    val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)

    val shortest = plainDecimalString(abs(value))

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

/**
 * Expands `Double.toString()`'s scientific notation into a plain decimal string, so [formatFixed]
 * always has positional digits to round. Kotlin emits E-notation for `|v| < 1e-3` and
 * `|v| >= 1e7`; JVM `String.format("%.Nf", …)` never does, so we normalise before rounding.
 *
 * This is not a theoretical case: `TrendCalculator.trendPerWeek` is an OLS slope, and
 * floating-point cancellation on a flat series yields residues like `1.66E-15`, which the coach's
 * "flat trend" detectors then format.
 *
 * Input must be non-negative (the caller strips the sign first). The expansion only moves the
 * decimal point of the shortest representation, so [formatFixed] keeps rounding exactly the digits
 * `java.util.Formatter` rounds.
 *
 * Large values were the suspected risk and turned out not to be one: `java.util.Formatter` does
 * NOT print the exact binary value for `%f` either — it pads the same shortest-representation
 * digits. Checked on JDK 21 up to `Double.MAX_VALUE` (e.g. `%.0f` of 1.0E23 is
 * `100000000000000000000000`, not the exact `99999999999999991611392`), zero divergences. See the
 * ADDENDUM in `docs/ios-port/phases/phase-0-golden-corpus.txt`.
 */
private fun plainDecimalString(nonNegative: Double): String {
    val s = nonNegative.toString()
    val eIdx = s.indexOfFirst { it == 'e' || it == 'E' }
    if (eIdx < 0) return s

    val mantissa = s.substring(0, eIdx)
    // toInt() tolerates a leading '+', which some Kotlin targets emit for positive exponents.
    val exponent = s.substring(eIdx + 1).toInt()
    val mInt = mantissa.substringBefore('.')
    val mFrac = mantissa.substringAfter('.', "")
    val digits = mInt + mFrac
    val pointPos = mInt.length + exponent

    return when {
        pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
        pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length)
        else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
    }
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

/**
 * Thousands-grouped integer, e.g. "9,200" — replacement for `String.format(Locale.US, "%,d", v)`,
 * which is unavailable on Kotlin/Native. Always uses the US comma separator (the rest of the app
 * pins `Locale.US` for this format explicitly), so output is locale-independent.
 */
fun groupedInt(value: Int): String {
    val negative = value < 0
    // Build from the absolute value as a string so Int.MIN_VALUE (which has no positive Int) is safe.
    val digits = if (negative) value.toString().substring(1) else value.toString()
    val out = StringBuilder(digits.length + digits.length / 3 + 1)
    if (negative) out.append('-')
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return out.toString()
}

fun bucketInt(value: Int, step: Int): String {
    val safeStep = if (step <= 0) 1 else step
    return ((value.toDouble() / safeStep).roundToInt() * safeStep).toString()
}
