package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color

object HDRProcessor {

    fun process(frames: List<Bitmap>): Bitmap {
        if (frames.isEmpty()) throw IllegalArgumentException("No frames")
        if (frames.size == 1) return frames[0]

        val w = frames[0].width
        val h = frames[0].height
        val total = w * h

        val accR = FloatArray(total)
        val accG = FloatArray(total)
        val accB = FloatArray(total)
        val accW = FloatArray(total)

        val px = IntArray(total)

        for (frame in frames) {
            val f = if (frame.width != w || frame.height != h)
                Bitmap.createScaledBitmap(frame, w, h, true) else frame
            f.getPixels(px, 0, w, 0, 0, w, h)

            for (i in 0 until total) {
                val r = Color.red(px[i]) / 255f
                val g = Color.green(px[i]) / 255f
                val b = Color.blue(px[i]) / 255f

                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                // Well-exposedness weight (peaks at 0.5)
                val we = gaussian(lum, 0.5f, 0.2f)

                // Saturation weight
                val mean = (r + g + b) / 3f
                val sat = kotlin.math.sqrt(
                    ((r - mean) * (r - mean) + (g - mean) * (g - mean) + (b - mean) * (b - mean)) / 3f
                )

                val w = (we * (sat + 0.01f)).coerceAtLeast(1e-6f)

                accR[i] += r * w
                accG[i] += g * w
                accB[i] += b * w
                accW[i] += w
            }
        }

        val lut = buildSCurveLut()
        val result = IntArray(total)

        for (i in 0 until total) {
            val wt = accW[i].coerceAtLeast(1e-6f)
            val ri = (accR[i] / wt * 255f).toInt().coerceIn(0, 255)
            val gi = (accG[i] / wt * 255f).toInt().coerceIn(0, 255)
            val bi = (accB[i] / wt * 255f).toInt().coerceIn(0, 255)
            result[i] = Color.rgb(lut[ri], lut[gi], lut[bi])
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    private fun gaussian(x: Float, mu: Float, sigma: Float): Float {
        val d = (x - mu) / sigma
        return kotlin.math.exp(-0.5f * d * d)
    }

    private fun buildSCurveLut(): IntArray {
        val lut = IntArray(256)
        for (i in 0..255) {
            val t = i / 255f
            // S-curve: shadows lifted, highlights punchy
            val s = t * t * (3f - 2f * t)
            val blended = t * 0.4f + s * 0.6f
            lut[i] = (blended * 255f).toInt().coerceIn(0, 255)
        }
        return lut
    }
}
