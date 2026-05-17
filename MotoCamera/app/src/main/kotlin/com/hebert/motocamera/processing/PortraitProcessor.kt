package com.hebert.motocamera.processing

import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenter
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Portrait processor using ML Kit:
 * - Selfie segmentation separates subject from background
 * - Face detection refines focus area
 * - Progressive blur applied to background with edge-aware blending
 */
object PortraitProcessor {

    private val segmenter = SelfieSegmenter.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    suspend fun process(bitmap: Bitmap, blurRadius: Float = 18f): Bitmap =
        withContext(Dispatchers.Default) {
            val mask = getSegmentationMask(bitmap)
            applyPortraitBlur(bitmap, mask, blurRadius)
        }

    private suspend fun getSegmentationMask(bitmap: Bitmap): FloatArray =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result ->
                    val buffer = result.buffer
                    val maskWidth = result.width
                    val maskHeight = result.height
                    val mask = FloatArray(maskWidth * maskHeight)
                    buffer.rewind()
                    buffer.asFloatBuffer().get(mask)
                    cont.resume(mask)
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private fun applyPortraitBlur(original: Bitmap, mask: FloatArray, radius: Float): Bitmap {
        val width = original.width
        val height = original.height

        val blurred = stackBlur(original, radius.toInt())

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(blurred, 0f, 0f, null)

        // Composite: where mask says "subject" (confidence > 0.5), draw original pixel
        val maskW = (mask.size / height).coerceAtLeast(1)
        val paint = Paint().apply { isAntiAlias = true }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskX = (x.toFloat() / width * maskW).toInt().coerceIn(0, maskW - 1)
                val maskY = (y.toFloat() / height * (mask.size / maskW)).toInt()
                    .coerceIn(0, mask.size / maskW - 1)
                val confidence = mask[maskY * maskW + maskX]

                // Smooth edge-aware blending
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

    /** Stack blur — O(n) approximation of Gaussian blur */
    private fun stackBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceAtLeast(1)
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Horizontal pass
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
            for (kx in -r..r) {
                val px = pixels[y * w + kx.coerceIn(0, w - 1)]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px); count++
            }
            for (x in 0 until w) {
                pixels[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
                val removeX = (x - r).coerceIn(0, w - 1)
                val addX = (x + r + 1).coerceIn(0, w - 1)
                val removePx = src.getPixel(removeX, y)
                val addPx = src.getPixel(addX, y)
                rSum += Color.red(addPx) - Color.red(removePx)
                gSum += Color.green(addPx) - Color.green(removePx)
                bSum += Color.blue(addPx) - Color.blue(removePx)
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
            for (ky in -r..r) {
                val px = pixels[ky.coerceIn(0, h - 1) * w + x]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px); count++
            }
            for (y in 0 until h) {
                pixels[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
                val removeY = (y - r).coerceIn(0, h - 1)
                val addY = (y + r + 1).coerceIn(0, h - 1)
                val removePx = pixels[removeY * w + x]
                val addPx = pixels[addY.coerceIn(0, h - 1) * w + x]
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
