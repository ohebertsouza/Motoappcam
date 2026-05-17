package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Photographic Styles 2.0
 * X axis: Tone (-1 = cool/teal, +1 = warm/golden)
 * Y axis: Mood (-1 = muted/filmic, +1 = vibrant/vivid)
 */
object StyleProcessor {

    data class StylePoint(val tone: Float, val mood: Float) // both in [-1, 1]

    val PRESETS = mapOf(
        "Standard" to StylePoint(0f, 0f),
        "Rich Contrast" to StylePoint(0.1f, 0.8f),
        "Vibrant" to StylePoint(0.2f, 1.0f),
        "Warm" to StylePoint(0.9f, 0.3f),
        "Cool" to StylePoint(-0.8f, 0.2f),
        "Muted" to StylePoint(0f, -0.8f),
        "Cinematic" to StylePoint(-0.3f, -0.5f),
        "Golden Hour" to StylePoint(1.0f, 0.5f),
    )

    suspend fun apply(bitmap: Bitmap, style: StylePoint): Bitmap =
        withContext(Dispatchers.Default) {
            val lut = buildLUT(style)
            applyLUT(bitmap, lut)
        }

    /** Build a per-channel LUT from the style point */
    private fun buildLUT(style: StylePoint): Array<IntArray> {
        val tone = style.tone    // [-1, 1]: negative=cool, positive=warm
        val mood = style.mood    // [-1, 1]: negative=muted, positive=vivid

        val rLUT = IntArray(256)
        val gLUT = IntArray(256)
        val bLUT = IntArray(256)

        for (i in 0..255) {
            val v = i / 255f

            // Warm: boost red/yellow, cool: boost blue/teal
            val warmR = if (tone > 0) tone * 0.12f else 0f
            val warmB = if (tone < 0) -tone * 0.12f else 0f
            val warmG = if (tone > 0) tone * 0.04f else if (tone < 0) -tone * 0.04f else 0f

            // Mood: vivid = saturation boost (push from mid), muted = desaturate
            val midPush = if (mood > 0) mood * 0.15f * (v - 0.5f) else 0f
            val muted = if (mood < 0) (-mood) * 0.3f else 0f

            val r = (v + warmR + midPush).coerceIn(0f, 1f)
            val g = (v + warmG + midPush).coerceIn(0f, 1f)
            val b = (v + warmB - midPush * 0.5f).coerceIn(0f, 1f)

            // Apply muting (push towards luminance mid)
            val lum = r * 0.299f + g * 0.587f + b * 0.114f
            rLUT[i] = ((r + (lum - r) * muted) * 255).toInt().coerceIn(0, 255)
            gLUT[i] = ((g + (lum - g) * muted) * 255).toInt().coerceIn(0, 255)
            bLUT[i] = ((b + (lum - b) * muted) * 255).toInt().coerceIn(0, 255)
        }

        return arrayOf(rLUT, gLUT, bLUT)
    }

    private fun applyLUT(bitmap: Bitmap, lut: Array<IntArray>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val px = pixels[i]
            pixels[i] = Color.argb(
                Color.alpha(px),
                lut[0][Color.red(px)],
                lut[1][Color.green(px)],
                lut[2][Color.blue(px)]
            )
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
