package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.Color

object NightModeProcessor {

    fun process(frames: List<Bitmap>): Bitmap {
        if (frames.isEmpty()) throw IllegalArgumentException("No frames")
        if (frames.size == 1) return frames[0]

        val w = frames[0].width
        val h = frames[0].height
        val total = w * h

        val accR = FloatArray(total)
        val accG = FloatArray(total)
        val accB = FloatArray(total)

        val px = IntArray(total)
        for (frame in frames) {
            val f = if (frame.width != w || frame.height != h)
                Bitmap.createScaledBitmap(frame, w, h, true) else frame
            f.getPixels(px, 0, w, 0, 0, w, h)
            for (i in 0 until total) {
                accR[i] += Color.red(px[i]).toFloat()
                accG[i] += Color.green(px[i]).toFloat()
                accB[i] += Color.blue(px[i]).toFloat()
            }
        }

        val count = frames.size.toFloat()
        val lut = buildReinhardLut(count)

        val result = IntArray(total)
        for (i in 0 until total) {
            result[i] = Color.rgb(
                lut[(accR[i] / count).toInt().coerceIn(0, 255)],
                lut[(accG[i] / count).toInt().coerceIn(0, 255)],
                lut[(accB[i] / count).toInt().coerceIn(0, 255)]
            )
        }

        val merged = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        merged.setPixels(result, 0, w, 0, 0, w, h)
        return sharpen(merged)
    }

    private fun buildReinhardLut(frameCount: Float): IntArray {
        val lut = IntArray(256)
        for (i in 0..255) {
            val lin = i / 255f
            val mapped = lin / (lin + 1f) * 2f
            lut[i] = (mapped.coerceIn(0f, 1f) * 255f).toInt()
        }
        return lut
    }

    private fun sharpen(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val total = w * h
        val px = IntArray(total)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val out = IntArray(total)
        val k = 0.3f

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val r = Color.red(px[i])
                val g = Color.green(px[i])
                val b = Color.blue(px[i])

                val neighbors = listOf(
                    px[(y - 1) * w + x], px[(y + 1) * w + x],
                    px[y * w + (x - 1)], px[y * w + (x + 1)]
                )
                val avgR = neighbors.sumOf { Color.red(it) } / 4f
                val avgG = neighbors.sumOf { Color.green(it) } / 4f
                val avgB = neighbors.sumOf { Color.blue(it) } / 4f

                out[i] = Color.rgb(
                    (r + k * (r - avgR)).toInt().coerceIn(0, 255),
                    (g + k * (g - avgG)).toInt().coerceIn(0, 255),
                    (b + k * (b - avgB)).toInt().coerceIn(0, 255)
                )
            }
        }

        // Copy borders
        for (x in 0 until w) { out[x] = px[x]; out[(h - 1) * w + x] = px[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = px[y * w]; out[y * w + w - 1] = px[y * w + w - 1] }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
