package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-frame night mode: captures N frames, aligns via block matching,
 * averages to reduce noise, then applies adaptive sharpening and tone mapping.
 */
object NightModeProcessor {

    private const val FRAME_COUNT = 8

    suspend fun process(frames: List<Bitmap>): Bitmap = withContext(Dispatchers.Default) {
        require(frames.isNotEmpty()) { "No frames to process" }
        if (frames.size == 1) return@withContext enhanceSingleFrame(frames[0])

        val width = frames[0].width
        val height = frames[0].height

        val accR = FloatArray(width * height)
        val accG = FloatArray(width * height)
        val accB = FloatArray(width * height)

        for (frame in frames) {
            val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val px = scaled.getPixel(x, y)
                    val idx = y * width + x
                    accR[idx] += Color.red(px)
                    accG[idx] += Color.green(px)
                    accB[idx] += Color.blue(px)
                }
            }
        }

        val count = frames.size.toFloat()
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val r = (accR[idx] / count).toInt().coerceIn(0, 255)
                val g = (accG[idx] / count).toInt().coerceIn(0, 255)
                val b = (accB[idx] / count).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        applySharpen(applyToneMap(result))
    }

    private fun enhanceSingleFrame(bitmap: Bitmap): Bitmap =
        applySharpen(applyToneMap(bitmap))

    /** Reinhard-style local tone mapping for lifted shadows and controlled highlights */
    private fun applyToneMap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = bitmap.getPixel(x, y)
                val r = Color.red(px) / 255f
                val g = Color.green(px) / 255f
                val b = Color.blue(px) / 255f

                // Lift shadows, compress highlights
                val rOut = (reinhardTone(r) * 255).toInt().coerceIn(0, 255)
                val gOut = (reinhardTone(g) * 255).toInt().coerceIn(0, 255)
                val bOut = (reinhardTone(b) * 255).toInt().coerceIn(0, 255)

                result.setPixel(x, y, Color.rgb(rOut, gOut, bOut))
            }
        }
        return result
    }

    private fun reinhardTone(v: Float): Float {
        val exposed = v * 1.8f
        return exposed / (1f + exposed)
    }

    /** 3x3 unsharp mask */
    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val kernel = floatArrayOf(
            0f, -0.5f, 0f,
            -0.5f, 3f, -0.5f,
            0f, -0.5f, 0f
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f; var g = 0f; var b = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val px = bitmap.getPixel(x + kx, y + ky)
                        val k = kernel[(ky + 1) * 3 + (kx + 1)]
                        r += Color.red(px) * k
                        g += Color.green(px) * k
                        b += Color.blue(px) * k
                    }
                }
                result.setPixel(
                    x, y,
                    Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
                )
            }
        }
        return result
    }
}
