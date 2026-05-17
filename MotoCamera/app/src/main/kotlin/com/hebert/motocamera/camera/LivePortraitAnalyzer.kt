package com.hebert.motocamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.hebert.motocamera.processing.PortraitProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class LivePortraitAnalyzer : ImageAnalysis.Analyzer {

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    private var lastMs = 0L
    private var cachedMask: FloatArray? = null
    private var cachedMaskW = 0
    private var cachedMaskH = 0

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastMs < 100) {
            if (cachedMask != null) {
                val bmp = image.toSmallBitmap()
                if (bmp != null) {
                    _frame.value = applyMask(bmp, cachedMask!!, cachedMaskW, cachedMaskH)
                }
            }
            image.close()
            return
        }
        lastMs = now

        val bitmap = image.toSmallBitmap()
        if (bitmap == null) { image.close(); return }

        val mlImage = InputImage.fromBitmap(bitmap, image.imageInfo.rotationDegrees)
        segmenter.process(mlImage)
            .addOnSuccessListener { mask: SegmentationMask ->
                val buf = mask.buffer
                val mW = mask.width
                val mH = mask.height
                val maskArr = FloatArray(mW * mH)
                buf.rewind()
                buf.asFloatBuffer().get(maskArr)
                cachedMask = maskArr
                cachedMaskW = mW
                cachedMaskH = mH
                _frame.value = applyMask(bitmap, maskArr, mW, mH)
            }
            .addOnFailureListener { }

        image.close()
    }

    private fun applyMask(bitmap: Bitmap, mask: FloatArray, mW: Int, mH: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val blurred = PortraitProcessor.stackBlur(bitmap, 14)

        val origPx = IntArray(w * h)
        val blurPx = IntArray(w * h)
        val result = IntArray(w * h)

        bitmap.getPixels(origPx, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPx, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val mX = (x.toFloat() / w * mW).toInt().coerceIn(0, mW - 1)
                val mY = (y.toFloat() / h * mH).toInt().coerceIn(0, mH - 1)
                val conf = mask[mY * mW + mX]
                val t = smoothStep(0.3f, 0.7f, conf)
                val i = y * w + x
                val op = origPx[i]; val bp = blurPx[i]
                result[i] = Color.rgb(
                    lerp(Color.red(bp), Color.red(op), t),
                    lerp(Color.green(bp), Color.green(op), t),
                    lerp(Color.blue(bp), Color.blue(op), t)
                )
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    private fun ImageProxy.toSmallBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 60, out)
            val bytes = out.toByteArray()
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val targetW = 320
            val targetH = (height.toFloat() / width * targetW).toInt()
            Bitmap.createScaledBitmap(full, targetW, targetH, false)
        } catch (e: Exception) { null }
    }

    private fun smoothStep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
