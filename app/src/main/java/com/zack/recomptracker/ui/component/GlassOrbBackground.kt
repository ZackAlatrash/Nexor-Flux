package com.zack.recomptracker.ui.component

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zack.recomptracker.ui.theme.AccentTheme
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.backgroundRes

// How much the raw rotation-vector reading maps to a tilt fraction
private const val TILT_SCALE = 0.28f
// Maximum tilt displacement in either direction
private const val MAX_TILT = 0.09f
// Image is scaled up so parallax movement never exposes edges
private const val IMAGE_SCALE = 1.14f
// Max parallax shift in dp at full tilt
private const val PARALLAX_DP = 24f
// Minimum tilt delta that triggers a state update — prevents 60 Hz recompositions from tiny noise
private const val TILT_THRESHOLD = 0.004f

@Composable
fun GlassOrbBackground(
    accentTheme: AccentTheme,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    var rawTiltX by remember { mutableFloatStateOf(0f) }
    var rawTiltY by remember { mutableFloatStateOf(0f) }

    val tiltX by animateFloatAsState(
        targetValue = rawTiltX,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.75f),
        label = "tiltX",
    )
    val tiltY by animateFloatAsState(
        targetValue = rawTiltY,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.75f),
        label = "tiltY",
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val newX = (event.values[0] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
                val newY = (event.values[1] * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
                if (abs(newX - rawTiltX) > TILT_THRESHOLD || abs(newY - rawTiltY) > TILT_THRESHOLD) {
                    rawTiltX = newX
                    rawTiltY = newY
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
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

    val density = LocalDensity.current
    val parallaxPx = remember(density) { with(density) { PARALLAX_DP.dp.toPx() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Image(
            painter = painterResource(accentTheme.backgroundRes(darkMode)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    scaleX = IMAGE_SCALE
                    scaleY = IMAGE_SCALE
                    translationX = (tiltX / MAX_TILT) * parallaxPx
                    translationY = (tiltY / MAX_TILT) * parallaxPx
                },
        )
        // Mode-aware scrim for card readability (dark veil in dark mode, light veil in light).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAppColors.current.scrim),
        )
    }
}
