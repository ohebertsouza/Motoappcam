package com.hebert.motocamera.processing

import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PortraitProcessor {

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
    )

    suspend fun process(bitmap: Bitmap, blurRadius: Float = 18f): Bitmap =
        withContext(Dispatchers.Default) {
            val mask = getSegmentationMask(bitmap)
            applyPortraitBlur(bitmap, mask.first, mask.second, mask.third, blurRadius)
        }

    private suspend fun getSegmentationMask(bitmap: Bitmap): Triple<FloatArray, Int, Int> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result: SegmentationMask ->
                    val buffer = result.buffer
                    val maskWidth = result.width
                    val maskHeight = result.height
                    val mask = FloatArray(maskWidth * maskHeight)
                    buffer.rewind()
                    buffer.asFloatBuffer().get(mask)
                    cont.resume(Triple(mask, maskWidth, maskHeight))
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private fun applyPortraitBlur(
        original: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
        radius: Float
    ): Bitmap {
        val width = original.width
        val height = original.height

        val blurred = stackBlur(original, radius.toInt())
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskX = (x.toFloat() / width * maskW).toInt().coerceIn(0, maskW - 1)
                val maskY = (y.toFloat() / height * maskH).toInt().coerceIn(0, maskH - 1)
                val confidence = mask[maskY * maskW + maskX]

                val alpha = smoothStep(0.35f, 0.65f, confidence)
                val origPx = original.getPixel(x, y)
                val blurPx = blurred.getPixel(x, y)

                val r = lerp(Color.red(blurPx), Color.red(origPx), alpha)
                val g = lerp(Color.green(blurPx), Color.green(origPx), alpha)
                val b = lerp(Color.blue(blurPx), Color.blue(origPx), alpha)

                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return result
    }

    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceAtLeast(1)
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0; val count = r * 2 + 1
            for (kx in -r..r) {
                val px = pixels[y * w + kx.coerceIn(0, w - 1)]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px)
            }
            for (x in 0 until w) {
                pixels[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
                val removePx = src.getPixel((x - r).coerceIn(0, w - 1), y)
                val addPx = src.getPixel((x + r + 1).coerceIn(0, w - 1), y)
                rSum += Color.red(addPx) - Color.red(removePx)
                gSum += Color.green(addPx) - Color.green(removePx)
                bSum += Color.blue(addPx) - Color.blue(removePx)
            }
        }

        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0; val count = r * 2 + 1
            for (ky in -r..r) {
                val px = pixels[ky.coerceIn(0, h - 1) * w + x]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px)
            }
            for (y in 0 until h) {
                pixels[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
                val removePx = pixels[(y - r).coerceIn(0, h - 1) * w + x]
                val addPx = pixels[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rSum += Color.red(addPx) - Color.red(removePx)
                gSum += Color.green(addPx) - Color.green(removePx)
                bSum += Color.blue(addPx) - Color.blue(removePx)
            }
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
