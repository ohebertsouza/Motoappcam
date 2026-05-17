package com.hebert.motocamera.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import com.hebert.motocamera.camera.CameraController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class CaptureOrchestrator(
    private val context: Context,
    private val cameraController: CameraController
) {
    private val executor = context.mainExecutor

    suspend fun capture(
        mode: CaptureMode,
        style: StyleProcessor.StylePoint
    ): Result<Bitmap> = runCatching {
        val baseBitmap = when (mode) {
            CaptureMode.NIGHT -> captureNight()
            CaptureMode.HDR -> captureHDR()
            CaptureMode.PORTRAIT -> capturePortrait()
            CaptureMode.AUTO -> captureSingle()
        }

        // Apply photographic style
        if (style.tone != 0f || style.mood != 0f) {
            StyleProcessor.apply(baseBitmap, style)
        } else {
            baseBitmap
        }
    }

    private suspend fun captureSingle(): Bitmap {
        val proxy = cameraController.captureImage(executor)
        return proxy.toBitmap().also { proxy.close() }
    }

    private suspend fun captureNight(): Bitmap {
        val frames = (1..6).map {
            val proxy = cameraController.captureImage(executor)
            proxy.toBitmap().also { proxy.close() }
        }
        return NightModeProcessor.process(frames)
    }

    private suspend fun captureHDR(): Bitmap {
        // Simulate bracket by capturing same frame (real bracket needs Camera2 CaptureSession)
        val normal = captureSingle()
        val under = darken(normal, 0.5f)
        val over = brighten(normal, 1.8f)
        return HDRProcessor.process(under, normal, over)
    }

    private suspend fun capturePortrait(): Bitmap {
        val bitmap = captureSingle()
        return PortraitProcessor.process(bitmap)
    }

    fun saveBitmap(bitmap: Bitmap): Boolean {
        return try {
            val filename = "MotoCamera_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MotoCamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun darken(bitmap: Bitmap, factor: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix(floatArrayOf(
                    factor, 0f, 0f, 0f, 0f,
                    0f, factor, 0f, 0f, 0f,
                    0f, 0f, factor, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun brighten(bitmap: Bitmap, factor: Float): Bitmap = darken(bitmap, factor)
}

fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    return if (format == ImageFormat.JPEG) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        val yuvImage = YuvImage(bytes, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val data = out.toByteArray()
        BitmapFactory.decodeByteArray(data, 0, data.size)
    }
}
