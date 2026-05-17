package com.hebert.motocamera.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun FocusRing(x: Float, y: Float) {
    val density = LocalDensity.current

    var animTarget by remember { mutableStateOf(0f) }

    val alpha by animateFloatAsState(
        targetValue = animTarget,
        animationSpec = tween(durationMillis = 1600, delayMillis = 400, easing = FastOutLinearInEasing),
        label = "focus_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (animTarget == 0f) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "focus_scale"
    )

    LaunchedEffect(x, y) {
        animTarget = 0f
        kotlinx.coroutines.delay(50)
        animTarget = 1f
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val ringSize = with(density) { 72.dp.toPx() } * scale
        val strokePx = with(density) { 1.5.dp.toPx() }
        val cx = x * size.width
        val cy = y * size.height
        val left = cx - ringSize / 2f
        val top = cy - ringSize / 2f
        val ringAlpha = (1f - alpha).coerceIn(0f, 1f)

        drawRect(
            color = Color(0xFFFFD600).copy(alpha = ringAlpha),
            topLeft = Offset(left, top),
            size = Size(ringSize, ringSize),
            style = Stroke(width = strokePx)
        )

        val cl = with(density) { 14.dp.toPx() }
        val corners = listOf(
            Triple(Offset(left, top), Offset(left + cl, top), Offset(left, top + cl)),
            Triple(Offset(left + ringSize, top), Offset(left + ringSize - cl, top), Offset(left + ringSize, top + cl)),
            Triple(Offset(left, top + ringSize), Offset(left + cl, top + ringSize), Offset(left, top + ringSize - cl)),
            Triple(Offset(left + ringSize, top + ringSize), Offset(left + ringSize - cl, top + ringSize), Offset(left + ringSize, top + ringSize - cl))
        )
        val cornerAlpha = (1f - alpha * 0.5f).coerceIn(0f, 1f)
        corners.forEach { (corner, p1, p2) ->
            drawLine(Color.White.copy(alpha = cornerAlpha), corner, p1, strokeWidth = strokePx * 1.5f)
            drawLine(Color.White.copy(alpha = cornerAlpha), corner, p2, strokeWidth = strokePx * 1.5f)
        }

        drawCircle(
            color = Color(0xFFFFD600).copy(alpha = ringAlpha),
            radius = with(density) { 2.dp.toPx() },
            center = Offset(cx, cy)
        )
    }
}
