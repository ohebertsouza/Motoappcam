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

    suspend fun process(bitmap: Bitmap, blurRadius: Float = 22f): Bitmap =
        withContext(Dispatchers.Default) {
            val scale = 0.4f
            val small = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                false
            )
            val (mask, mW, mH) = getSegmentationMask(small)
            applyPortraitBlur(bitmap, mask, mW, mH, blurRadius.toInt())
        }

    private suspend fun getSegmentationMask(bitmap: Bitmap): Triple<FloatArray, Int, Int> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result: SegmentationMask ->
                    val buf = result.buffer
                    val mW = result.width
                    val mH = result.height
                    val mask = FloatArray(mW * mH)
                    buf.rewind()
                    buf.asFloatBuffer().get(mask)
                    cont.resume(Triple(mask, mW, mH))
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private fun applyPortraitBlur(
        original: Bitmap, mask: FloatArray, maskW: Int, maskH: Int, radius: Int
    ): Bitmap {
        val w = original.width
        val h = original.height
        val blurred = stackBlur(original, radius)
        val origPx = IntArray(w * h)
        val blurPx = IntArray(w * h)
        val result = IntArray(w * h)
        original.getPixels(origPx, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val mX = (x.toFloat() / w * maskW).toInt().coerceIn(0, maskW - 1)
                val mY = (y.toFloat() / h * maskH).toInt().coerceIn(0, maskH - 1)
                val conf = mask[mY * maskW + mX]
                val t = smoothStep(0.35f, 0.65f, conf)
                val idx = y * w + x
                result[idx] = blendPixels(blurPx[idx], origPx[idx], t)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 100)
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = pixels.copyOf()
        for (y in 0 until h) {
            var rS = 0; var gS = 0; var bS = 0
            val count = r * 2 + 1
            for (kx in -r..r) {
                val p = tmp[y * w + kx.coerceIn(0, w - 1)]
                rS += Color.red(p); gS += Color.green(p); bS += Color.blue(p)
            }
            for (x in 0 until w) {
                pixels[y * w + x] = Color.rgb(rS / count, gS / count, bS / count)
                val rem = tmp[y * w + (x - r).coerceIn(0, w - 1)]
                val add = tmp[y * w + (x + r + 1).coerceIn(0, w - 1)]
                rS += Color.red(add) - Color.red(rem)
                gS += Color.green(add) - Color.green(rem)
                bS += Color.blue(add) - Color.blue(rem)
            }
        }
        val tmp2 = pixels.copyOf()
        for (x in 0 until w) {
            var rS = 0; var gS = 0; var bS = 0
            val count = r * 2 + 1
            for (ky in -r..r) {
                val p = tmp2[ky.coerceIn(0, h - 1) * w + x]
                rS += Color.red(p); gS += Color.green(p); bS += Color.blue(p)
            }
            for (y in 0 until h) {
                pixels[y * w + x] = Color.rgb(rS / count, gS / count, bS / count)
                val rem = tmp2[(y - r).coerceIn(0, h - 1) * w + x]
                val add = tmp2[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rS += Color.red(add) - Color.red(rem)
                gS += Color.green(add) - Color.green(rem)
                bS += Color.blue(add) - Color.blue(rem)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun blendPixels(bg: Int, fg: Int, t: Float): Int = Color.argb(
        255,
        lerp(Color.red(bg), Color.red(fg), t),
        lerp(Color.green(bg), Color.green(fg), t),
        lerp(Color.blue(bg), Color.blue(fg), t)
    )

    private fun smoothStep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
