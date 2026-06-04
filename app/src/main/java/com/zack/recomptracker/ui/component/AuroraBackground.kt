package com.zack.recomptracker.ui.component

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.withFrameNanos
import kotlin.math.cos
import kotlin.math.sin

private data class AuroraPool(
    val homeX: Float,          // resting X as fraction of screen width  (0..1)
    val homeY: Float,          // resting Y as fraction of screen height (0..1)
    val orbitRadiusX: Float,   // orbit amplitude as fraction of screen width
    val orbitRadiusY: Float,   // orbit amplitude as fraction of screen height
    val freqX: Float,          // horizontal orbit frequency (cycles/sec)
    val freqY: Float,          // vertical orbit frequency  (cycles/sec)
    val phase: Float,          // phase offset in radians — keeps pools out of sync
    val radiusFraction: Float, // gradient radius as fraction of screen width
    val color: Color,
    val tiltMultiplier: Float, // parallax weight — different depths appear to move differently
)

private val POOLS = listOf(
    AuroraPool(0.30f, 0.25f, 0.18f, 0.14f, 0.19f, 0.23f, 0.00f, 0.85f, Color(0x737C3AED), 0.8f),
    AuroraPool(0.70f, 0.70f, 0.15f, 0.18f, 0.14f, 0.17f, 2.09f, 0.75f, Color(0x664F46E5), 1.0f),
    AuroraPool(0.50f, 0.50f, 0.14f, 0.12f, 0.26f, 0.21f, 4.19f, 0.65f, Color(0x596D28D9), 1.2f),
)

// How much the raw rotation-vector reading maps to a pool offset fraction
private const val TILT_SCALE = 0.30f
// Maximum tilt displacement in either direction (fraction of screen dimension)
private const val MAX_TILT = 0.10f

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    // ── Time accumulation (delta-time, wraps at 1000 to preserve float precision) ──
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    t = (t + dt) % 1000f
                }
                lastNanos = nanos
            }
        }
    }

    // ── Gyroscope tilt (raw sensor → smoothed spring) ──
    var rawTiltX by remember { mutableFloatStateOf(0f) }
    var rawTiltY by remember { mutableFloatStateOf(0f) }
    val tiltX by animateFloatAsState(
        targetValue = rawTiltX,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f),
        label = "tiltX",
    )
    val tiltY by animateFloatAsState(
        targetValue = rawTiltY,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f),
        label = "tiltY",
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // event.values[0] = sin(θ/2)*x  →  maps to left/right roll
                // event.values[1] = sin(θ/2)*y  →  maps to forward/back pitch
                rawTiltX = (event.values[0] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
                rawTiltY = (event.values[1] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE  -> sm?.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sm?.unregisterListener(listener)
        }
    }

    // ── Canvas drawing ──
    Canvas(modifier = modifier.fillMaxSize()) {
        for (pool in POOLS) {
            val cx = (pool.homeX
                    + sin(t * pool.freqX + pool.phase) * pool.orbitRadiusX
                    + tiltX * pool.tiltMultiplier) * size.width
            val cy = (pool.homeY
                    + cos(t * pool.freqY + pool.phase) * pool.orbitRadiusY
                    + tiltY * pool.tiltMultiplier) * size.height
            val radius = pool.radiusFraction * size.width
            val center = Offset(cx, cy)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pool.color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Screen,
            )
        }
    }
}
