package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StyleProcessor {

    data class StylePoint(val tone: Float, val mood: Float)

    val PRESETS = mapOf(
        "Standard"      to StylePoint(0.0f,  0.0f),
        "Rich Contrast" to StylePoint(0.1f,  0.8f),
        "Vibrant"       to StylePoint(0.2f,  1.0f),
        "Warm"          to StylePoint(0.9f,  0.3f),
        "Cool"          to StylePoint(-0.8f, 0.2f),
        "Muted"         to StylePoint(0.0f, -0.8f),
        "Cinematic"     to StylePoint(-0.3f,-0.5f),
        "Golden Hour"   to StylePoint(1.0f,  0.5f)
    )

    suspend fun apply(bitmap: Bitmap, style: StylePoint): Bitmap =
        withContext(Dispatchers.Default) {
            // Standard: não aplica nenhum processamento extra
            if (style.tone == 0f && style.mood == 0f) return@withContext bitmap
            val lut = buildLUT(style)
            applyLUT(bitmap, lut)
        }

   private fun buildLUT(style: StylePoint): Array<IntArray> {
    val tone = style.tone
    val mood = style.mood

    val rLUT = IntArray(256)
    val gLUT = IntArray(256)
    val bLUT = IntArray(256)

    for (i in 0..255) {
        val v = i / 255f

        // Tone: temperatura de cor apenas
        // Positivo = quente (vermelho/amarelo), negativo = frio (azul/ciano)
        val warmR = if (tone > 0) tone * 0.08f else 0f
        val warmB = if (tone < 0) -tone * 0.08f else 0f
        val warmG = tone * 0.02f

        // Mood: só muting (negativo) — sem boost de saturação
        val mutedPull = if (mood < 0) -mood * 0.30f else 0f

        var r = (v + warmR).coerceIn(0f, 1f)
        var g = (v + warmG).coerceIn(0f, 1f)
        var b = (v + warmB).coerceIn(0f, 1f)

        if (mutedPull > 0f) {
            val lum = r * 0.299f + g * 0.587f + b * 0.114f
            r = (r + (lum - r) * mutedPull).coerceIn(0f, 1f)
            g = (g + (lum - g) * mutedPull).coerceIn(0f, 1f)
            b = (b + (lum - b) * mutedPull).coerceIn(0f, 1f)
        }

        rLUT[i] = (r * 255).toInt().coerceIn(0, 255)
        gLUT[i] = (g * 255).toInt().coerceIn(0, 255)
        bLUT[i] = (b * 255).toInt().coerceIn(0, 255)
    }

    return arrayOf(rLUT, gLUT, bLUT)
}

    private fun applyLUT(bitmap: Bitmap, lut: Array<IntArray>): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
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
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
