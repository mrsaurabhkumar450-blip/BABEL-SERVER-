package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ArcReactorCore
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGlowBlue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorVisualizer(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    isActive: Boolean = false,
    isListening: Boolean = false,
    isSpeaking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorPulse")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening || isSpeaking) 3000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening || isSpeaking) 4000 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isListening || isSpeaking) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 400 else if (isSpeaking) 600 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val activeColor = when {
        isListening -> JarvisCyan
        isSpeaking -> JarvisGlowBlue
        isActive -> JarvisElectricBlue
        else -> JarvisElectricBlue
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val baseRadius = (size.toPx() / 2f) * 0.85f

            // 1. Outer Glow Pulse Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.35f * pulse), Color.Transparent),
                    center = center,
                    radius = baseRadius * pulse
                ),
                radius = baseRadius * pulse,
                center = center
            )

            // 2. Outer Segmented Ring
            val numSegments = 12
            val segmentAngle = 360f / numSegments
            for (i in 0 until numSegments) {
                val startAngle = i * segmentAngle + rotation
                drawArc(
                    color = activeColor.copy(alpha = 0.7f),
                    startAngle = startAngle,
                    sweepAngle = segmentAngle * 0.6f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Middle Counter-Rotating Ring
            val midRadius = baseRadius * 0.72f
            val numInnerTicks = 8
            val tickAngle = 360f / numInnerTicks
            for (i in 0 until numInnerTicks) {
                val startAngle = i * tickAngle + counterRotation
                drawArc(
                    color = JarvisElectricBlue.copy(alpha = 0.9f),
                    startAngle = startAngle,
                    sweepAngle = tickAngle * 0.45f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Inner Ring with Spokes
            val coreRadius = baseRadius * 0.45f * pulse
            drawCircle(
                color = activeColor.copy(alpha = 0.5f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            for (i in 0 until 6) {
                val angleRad = Math.toRadians((i * 60 + rotation).toDouble())
                val start = Offset(
                    (center.x + (coreRadius * 0.4f * cos(angleRad))).toFloat(),
                    (center.y + (coreRadius * 0.4f * sin(angleRad))).toFloat()
                )
                val end = Offset(
                    (center.x + (coreRadius * 0.95f * cos(angleRad))).toFloat(),
                    (center.y + (coreRadius * 0.95f * sin(angleRad))).toFloat()
                )
                drawLine(
                    color = JarvisCyan.copy(alpha = 0.8f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Central Reactor Core Orb
            val centerOrbRadius = coreRadius * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ArcReactorCore,
                        activeColor,
                        activeColor.copy(alpha = 0.1f)
                    ),
                    center = center,
                    radius = centerOrbRadius
                ),
                radius = centerOrbRadius,
                center = center
            )
        }
    }
}
