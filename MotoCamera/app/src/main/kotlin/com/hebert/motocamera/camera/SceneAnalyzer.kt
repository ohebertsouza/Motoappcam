package com.hebert.motocamera.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.hebert.motocamera.processing.CaptureMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SceneInfo(
    val avgLuminance: Float,
    val contrastRatio: Float,
    val suggestedMode: CaptureMode,
    val label: String
)

class SceneAnalyzer : ImageAnalysis.Analyzer {

    private val _scene = MutableStateFlow(SceneInfo(128f, 1f, CaptureMode.AUTO, "Auto"))
    val scene: StateFlow<SceneInfo> = _scene.asStateFlow()

    private var frameCount = 0

    override fun analyze(image: ImageProxy) {
        frameCount++
        if (frameCount % 15 != 0) { image.close(); return }

        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height

        var sum = 0L
        var count = 0
        var minL = 255; var maxL = 0

        val step = 8
        for (row in 0 until h step step) {
            for (col in 0 until w step step) {
                val idx = row * rowStride + col * pixelStride
                if (idx < buffer.limit()) {
                    val lum = buffer.get(idx).toInt() and 0xFF
                    sum += lum
                    count++
                    if (lum < minL) minL = lum
                    if (lum > maxL) maxL = lum
                }
            }
        }

        image.close()
        if (count == 0) return

        val avg = sum.toFloat() / count
        val contrast = if (minL > 0) maxL.toFloat() / minL else maxL.toFloat()

        val (mode, label) = when {
            avg < 40f -> CaptureMode.NIGHT to "Noite"
            avg < 80f && contrast < 3f -> CaptureMode.NIGHT to "Noite"
            avg < 80f && contrast > 4f -> CaptureMode.HDR to "HDR"
            contrast > 6f -> CaptureMode.HDR to "HDR"
            else -> CaptureMode.AUTO to "Auto"
        }

        _scene.value = SceneInfo(avg, contrast, mode, label)
    }
}
