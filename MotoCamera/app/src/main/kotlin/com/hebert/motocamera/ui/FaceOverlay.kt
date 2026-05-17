package com.hebert.motocamera.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val faceDetector = FaceDetection.getClient(
    FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
)

@Composable
fun FaceOverlay(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }

    LaunchedEffect(bitmap) {
        if (bitmap == null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val image = InputImage.fromBitmap(bitmap, 0)
            faceDetector.process(image)
                .addOnSuccessListener { result -> faces = result }
                .addOnFailureListener { faces = emptyList() }
        }
    }

    if (faces.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width
        val scaleY = size.height

        faces.forEach { face ->
            val box = face.boundingBox
            val left = box.left.toFloat() / 1000f * scaleX
            val top = box.top.toFloat() / 1000f * scaleY
            val width = box.width().toFloat() / 1000f * scaleX
            val height = box.height().toFloat() / 1000f * scaleY
            val cornerLen = minOf(width, height) * 0.18f

            val strokePx = 2.dp.toPx()
            val color = Color.White

            // Corner brackets instead of full rectangle — cleaner look
            listOf(
                Pair(Offset(left, top), Pair(Offset(left + cornerLen, top), Offset(left, top + cornerLen))),
                Pair(Offset(left + width, top), Pair(Offset(left + width - cornerLen, top), Offset(left + width, top + cornerLen))),
                Pair(Offset(left, top + height), Pair(Offset(left + cornerLen, top + height), Offset(left, top + height - cornerLen))),
                Pair(Offset(left + width, top + height), Pair(Offset(left + width - cornerLen, top + height), Offset(left + width, top + height - cornerLen)))
            ).forEach { (corner, lines) ->
                drawLine(color, corner, lines.first, strokeWidth = strokePx)
                drawLine(color, corner, lines.second, strokeWidth = strokePx)
            }

            // Sorriso indicator
            val smile = face.smilingProbability ?: 0f
            if (smile > 0.7f) {
                drawCircle(
                    color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                    radius = 6.dp.toPx(),
                    center = Offset(left + width / 2f, top - 12.dp.toPx())
                )
            }
        }
    }
}
