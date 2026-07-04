package com.zack.recomptracker.ui.train.component

import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlin.math.ceil

private val FaintBody = Color.White.copy(alpha = 0.13f)

/**
 * Two full body silhouettes (front + back) side by side. Muscles are shaded by [intensities] — a
 * per-category ABSOLUTE weekly set count fed through [heatColor] (undertrained → green → overtrained
 * red): 0 sets reads faint. When [intensities] is empty the original faint look is preserved. The
 * slugs for [selected] are always filled solid accent (tap-to-select). Tapping a muscle reports its
 * [MuscleCategory] via [onMuscleTap] (no-op for non-muscle regions). Reuses the shared MuscleArt path data.
 */
@Composable
fun BodyMap(
    selected: MuscleCategory?,
    onMuscleTap: (MuscleCategory) -> Unit,
    modifier: Modifier = Modifier,
    intensities: Map<MuscleCategory, Float> = emptyMap(),
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    val (front, back) = remember(context) {
        MuscleArt.load(context)
        MuscleArt.front() to MuscleArt.back()
    }
    val highlight = selected?.let { highlightFor(it) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyFigure(
            label = "FRONT", paths = front, highlightSlugs = highlight?.front.orEmpty(),
            tint = accent.accent, faintColor = FaintBody, labelColor = appColors.textMuted,
            intensities = intensities, onMuscleTap = onMuscleTap, modifier = Modifier.weight(1f),
        )
        BodyFigure(
            label = "BACK", paths = back, highlightSlugs = highlight?.back.orEmpty(),
            tint = accent.accent, faintColor = FaintBody, labelColor = appColors.textMuted,
            intensities = intensities, onMuscleTap = onMuscleTap, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyFigure(
    label: String,
    paths: List<MuscleArt.MusclePath>,
    highlightSlugs: Set<String>,
    tint: Color,
    faintColor: Color,
    labelColor: Color,
    intensities: Map<MuscleCategory, Float>,
    onMuscleTap: (MuscleCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Union bounds of all paths = the whole silhouette extent.
    val bounds = remember(paths) {
        if (paths.isEmpty()) RectF(0f, 0f, 1f, 1f) else {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            paths.forEach {
                val bb = it.path.getBounds()
                l = minOf(l, bb.left); t = minOf(t, bb.top); r = maxOf(r, bb.right); b = maxOf(b, bb.bottom)
            }
            RectF(l, t, r, b)
        }
    }
    val aspect = (bounds.width() / bounds.height()).coerceIn(0.3f, 1.0f)
    // Keep the tap callback current without re-keying pointerInput (which would restart the
    // gesture detector every time the selected highlight changes — the detector doesn't use it).
    val currentOnMuscleTap by rememberUpdatedState(onMuscleTap)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .pointerInput(paths) {
                    detectTapGestures { offset ->
                        val t = BodyMapGeometry.fit(bounds.left, bounds.top, bounds.right, bounds.bottom, size.width.toFloat(), size.height.toFloat())
                        val slug = hitSlug(paths, t.toContentX(offset.x), t.toContentY(offset.y))
                        slug?.let { categoryForSlug(it) }?.let(currentOnMuscleTap)
                    }
                },
        ) {
            val t = BodyMapGeometry.fit(bounds.left, bounds.top, bounds.right, bounds.bottom, size.width, size.height)
            drawFigure(paths, highlightSlugs, tint, faintColor, intensities, t)
        }
        Text(text = label, style = AppType.metaLabel, color = labelColor, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun DrawScope.drawFigure(
    paths: List<MuscleArt.MusclePath>,
    highlightSlugs: Set<String>,
    tint: Color,
    faintColor: Color,
    intensities: Map<MuscleCategory, Float>,
    t: BodyMapGeometry.FitTransform,
) {
    translate(left = t.dx, top = t.dy) {
        scale(t.scale, t.scale, pivot = Offset.Zero) {
            paths.forEach { mp ->
                val heat = categoryForSlug(mp.slug)?.let { intensities[it] } ?: 0f
                val color = when {
                    mp.slug in highlightSlugs -> tint // tapped → solid accent (distinct from the green heat)
                    heat <= 0f -> faintColor // untrained this week → uncolored, like the base silhouette
                    else -> heatColor(heat).copy(alpha = 0.9f) // trained → light→deep green by volume
                }
                drawPath(mp.path, color)
            }
        }
    }
}

/** Point-in-path test in path coordinate space. Returns the first slug whose region contains (x,y). */
private fun hitSlug(paths: List<MuscleArt.MusclePath>, x: Float, y: Float): String? {
    paths.forEach { mp ->
        val ap = mp.path.asAndroidPath()
        val bb = RectF()
        ap.computeBounds(bb, true)
        if (x < bb.left || x > bb.right || y < bb.top || y > bb.bottom) return@forEach
        val region = Region()
        val clip = Region(bb.left.toInt(), bb.top.toInt(), ceil(bb.right).toInt(), ceil(bb.bottom).toInt())
        region.setPath(ap, clip)
        if (region.contains(x.toInt(), y.toInt())) return mp.slug
    }
    return null
}
