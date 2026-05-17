package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.pow

/**
 * Mertens exposure fusion: blends under/normal/over-exposed frames
 * using per-pixel weights (contrast + saturation + well-exposedness).
 */
object HDRProcessor {

    suspend fun process(underExp: Bitmap, normal: Bitmap, overExp: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            val frames = listOf(underExp, normal, overExp)
            val width = normal.width
            val height = normal.height

            val pixels = frames.map { bm ->
                Array(height) { y -> IntArray(width) { x -> bm.getPixel(x, y) } }
            }

            val weights = frames.mapIndexed { i, _ ->
                computeWeights(pixels[i], width, height)
            }

            // Normalize weights per pixel
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val totalW = weights.sumOf { it[y][x].toDouble() }.toFloat().coerceAtLeast(1e-6f)
                    var rAcc = 0f; var gAcc = 0f; var bAcc = 0f

                    for (i in frames.indices) {
                        val w = weights[i][y][x] / totalW
                        val px = pixels[i][y][x]
                        rAcc += Color.red(px) * w
                        gAcc += Color.green(px) * w
                        bAcc += Color.blue(px) * w
                    }

                    result.setPixel(
                        x, y,
                        Color.rgb(
                            rAcc.toInt().coerceIn(0, 255),
                            gAcc.toInt().coerceIn(0, 255),
                            bAcc.toInt().coerceIn(0, 255)
                        )
                    )
                }
            }

            applyHDRToneMap(result)
        }

    private fun computeWeights(pixels: Array<IntArray>, width: Int, height: Int): Array<FloatArray> {
        val w = Array(height) { FloatArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = pixels[y][x]
                val r = Color.red(px) / 255f
                val g = Color.green(px) / 255f
                val b = Color.blue(px) / 255f

                val wellExp = gaussianWeight(r) * gaussianWeight(g) * gaussianWeight(b)

                val mean = (r + g + b) / 3f
                val saturation = maxOf(r, g, b) - minOf(r, g, b)

                val contrast = if (y in 1 until height - 1 && x in 1 until width - 1) {
                    val neighbors = listOf(pixels[y - 1][x], pixels[y + 1][x], pixels[y][x - 1], pixels[y][x + 1])
                    val lum = { c: Int -> (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) / 255f }
                    neighbors.sumOf { (lum(it) - mean).toDouble().pow(2.0) }.toFloat()
                } else 0f

                w[y][x] = (wellExp + 0.01f) * (saturation + 0.01f) * (contrast + 0.01f)
            }
        }

        return w
    }

    private fun gaussianWeight(v: Float, mu: Float = 0.5f, sigma: Float = 0.2f): Float =
        exp(-((v - mu).pow(2) / (2 * sigma.pow(2)))).toFloat()

    /** S-curve tone mapping for punchy HDR look */
    private fun applyHDRToneMap(bitmap: Bitmap): Bitmap {
        val lut = IntArray(256) { i ->
            val v = i / 255f
            val mapped = sCurve(v)
            (mapped * 255).toInt().coerceIn(0, 255)
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                result.setPixel(
                    x, y,
                    Color.rgb(lut[Color.red(px)], lut[Color.green(px)], lut[Color.blue(px)])
                )
            }
        }
        return result
    }

    private fun sCurve(v: Float): Float {
        return if (v < 0.5f) {
            2f * v * v
        } else {
            1f - (-2f * v + 2f).pow(2) / 2f
        }
    }
}
