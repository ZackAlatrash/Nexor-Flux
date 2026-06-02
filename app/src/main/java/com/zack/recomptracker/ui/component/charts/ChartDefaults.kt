package com.zack.recomptracker.ui.component.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChartDefaults {

    object AnimSpec {
        val drawIn      = tween<Float>(durationMillis = 1200, easing = FastOutSlowInEasing)
        val areaFade    = tween<Float>(durationMillis = 600)
        val dotPop      = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
        val barRise     = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
        val ringArc     = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
        val progressBar = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        )
        const val ringStaggerMs = 300L
        const val barStaggerMs  = 60L
    }

    val strokeWidth  = 1.8.dp
    val dotRadius    = 4.5.dp
    val glowRadius   = 11.dp
    val glowHalo     = 16.dp

    const val gridAlpha     = 0.04f
    const val zoneBandAlpha = 0.10f
    const val zoneDashAlpha = 0x50

    object MacroColors {
        val Protein = Color(0xFFa78bfa)
        val Carbs   = Color(0xFF34d399)
        val Fat     = Color(0xFFfb923c)
    }
}
