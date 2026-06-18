package com.zack.recomptracker.ui.train.component

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

private val FaintBody = Color.White.copy(alpha = 0.13f)
private const val CROP_PAD = 1.55f

/**
 * Draws the worked muscle highlighted on a faint body silhouette, auto-cropped to
 * that muscle. [tint] colours the worked muscle (use the theme accent). When the
 * muscles can't be mapped or the art is unavailable, renders a generic dumbbell.
 */
@Composable
fun MuscleGroupIcon(
    primaryMuscles: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = remember(primaryMuscles) { muscleTargetFor(primaryMuscles) }

    val paths = remember(target) {
        if (target == null) {
            emptyList()
        } else {
            MuscleArt.load(context)
            if (target.view == MuscleView.FRONT) MuscleArt.front() else MuscleArt.back()
        }
    }

    val crop = remember(paths, target) {
        if (target == null || paths.isEmpty()) return@remember null
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        paths.filter { it.slug in target.slugs }.forEach {
            val bb = it.path.getBounds()
            l = minOf(l, bb.left); t = minOf(t, bb.top)
            r = maxOf(r, bb.right); b = maxOf(b, bb.bottom)
        }
        if (l > r) return@remember null
        val side = max(r - l, b - t) * CROP_PAD
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }

    if (target == null || crop == null) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
        return
    }

    Canvas(modifier) {
        val scale = size.minDimension / crop.width
        clipRect(0f, 0f, size.width, size.height) {
            withTransform({
                scale(scale, scale, pivot = Offset.Zero)
                translate(-crop.left, -crop.top)
            }) {
                paths.forEach { mp ->
                    drawPath(mp.path, if (mp.slug in target.slugs) tint else FaintBody)
                }
            }
        }
    }
}
