package com.hebert.motocamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebert.motocamera.processing.StyleProcessor

@Composable
fun StylePad(
    style: StyleProcessor.StylePoint,
    onStyleChange: (StyleProcessor.StylePoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var padSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Column(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PadLabel("FRIO", Color(0xFF64B5F6))
            PadLabel("PAD DE ESTILO", Color.White)
            PadLabel("QUENTE", Color(0xFFFFB74D))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (change.position.y / size.height).coerceIn(0f, 1f)
                            onStyleChange(StyleProcessor.StylePoint(
                                tone = x * 2f - 1f,
                                mood = 1f - (y * 2f)
                            ))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val x = (offset.x / size.width).coerceIn(0f, 1f)
                            val y = (offset.y / size.height).coerceIn(0f, 1f)
                            onStyleChange(StyleProcessor.StylePoint(
                                tone = x * 2f - 1f,
                                mood = 1f - (y * 2f)
                            ))
                        }
                    }
            ) {
                // Background gradient: horizontal = warm/cool, vertical = vivid/muted
                val horizontalBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),  // deep cool/teal
                        Color(0xFF263238),  // neutral dark
                        Color(0xFF4E342E),  // warm/golden dark
                    )
                )
                drawRect(brush = horizontalBrush, size = size)

                // Overlay vertical gradient for mood
                val verticalBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x80E8F5E9),  // vivid top
                        Color.Transparent,
                        Color(0x80212121),  // muted bottom
                    )
                )
                drawRect(brush = verticalBrush, size = size)

                // Grid lines
                val gridColor = Color.White.copy(alpha = 0.08f)
                for (i in 1 until 4) {
                    val x = size.width * i / 4f
                    val y = size.height * i / 4f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }

                // Cross-hair center
                drawLine(
                    Color.White.copy(alpha = 0.2f),
                    Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1f
                )
                drawLine(
                    Color.White.copy(alpha = 0.2f),
                    Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f
                )

                // Current position indicator
                val px = (style.tone + 1f) / 2f * size.width
                val py = (1f - (style.mood + 1f) / 2f) * size.height
                val pos = Offset(px, py)

                // Outer glow ring
                drawCircle(Color.White.copy(alpha = 0.15f), 28f, pos)
                // Ring
                drawCircle(Color.Transparent, 16f, pos)
                drawCircle(Color.White, 16f, pos, style = Stroke(width = 2f))
                // Inner dot
                drawCircle(Color.White, 5f, pos)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PadLabel("APAGADO", Color(0xFF9E9E9E))
            PadLabel("VÍVIDO", Color(0xFF81C784))
        }
    }
}

@Composable
private fun PadLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp
    )
}
