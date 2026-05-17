package com.hebert.motocamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tintAlpha: Float = 0.14f,
    borderAlpha: Float = 0.28f,
    dark: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val base = if (dark) Color.Black else Color.White
    val border = if (dark) Color.White else Color.Black

    Box(
        modifier = modifier
            .shadow(elevation = 0.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        base.copy(alpha = tintAlpha + 0.06f),
                        base.copy(alpha = tintAlpha)
                    )
                )
            )
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        border.copy(alpha = borderAlpha),
                        border.copy(alpha = borderAlpha * 0.4f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        content = content
    )
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    tintAlpha: Float = 0.18f,
    borderAlpha: Float = 0.30f,
    content: @Composable BoxScope.() -> Unit
) {
    GlassPanel(
        modifier = modifier,
        cornerRadius = 100.dp,
        tintAlpha = tintAlpha,
        borderAlpha = borderAlpha,
        content = content
    )
}
