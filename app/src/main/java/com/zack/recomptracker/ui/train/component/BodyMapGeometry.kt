package com.zack.recomptracker.ui.train.component

import kotlin.math.min

/**
 * Pure geometry for drawing a vector silhouette "fit-center" into a canvas and mapping taps back.
 * Screen = content * scale + (dx, dy). Inverse: content = (screen - d) / scale.
 */
object BodyMapGeometry {
    data class FitTransform(val scale: Float, val dx: Float, val dy: Float) {
        fun toContentX(screenX: Float): Float = (screenX - dx) / scale
        fun toContentY(screenY: Float): Float = (screenY - dy) / scale
    }

    fun fit(
        contentLeft: Float, contentTop: Float, contentRight: Float, contentBottom: Float,
        canvasW: Float, canvasH: Float,
    ): FitTransform {
        val cw = (contentRight - contentLeft).coerceAtLeast(0.0001f)
        val ch = (contentBottom - contentTop).coerceAtLeast(0.0001f)
        val scale = min(canvasW / cw, canvasH / ch)
        val dx = (canvasW - cw * scale) / 2f - contentLeft * scale
        val dy = (canvasH - ch * scale) / 2f - contentTop * scale
        return FitTransform(scale, dx, dy)
    }
}
