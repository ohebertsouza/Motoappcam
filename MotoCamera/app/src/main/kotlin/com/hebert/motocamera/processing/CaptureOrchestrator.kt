package com.hebert.motocamera.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.content.ContextCompat
import com.hebert.motocamera.camera.CameraController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CaptureOrchestrator(
    private val context: Context,
    private val cameraController: CameraController
) {
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun capture(mode: CaptureMode, style: StyleProcessor.StylePoint): Result<Bitmap> {
        return runCatching {
            val bitmap = when (mode) {
                CaptureMode.NIGHT -> captureNight()
                CaptureMode.HDR -> captureHDR()
                CaptureMode.PORTRAIT -> capturePortrait()
                CaptureMode.AUTO -> captureSingle()
            }
            StyleProcessor.apply(bitmap, style)
        }
    }

    private suspend fun captureSingle(): Bitmap {
        val image = cameraController.captureImage(executor)
        return withContext(Dispatchers.Default) {
            image.use { BitmapUtils.fromImageProxy(it) }
        }
    }

    private suspend fun captureNight(): Bitmap {
        val frames = mutableListOf<Bitmap>()
        repeat(6) {
            val image = cameraController.captureImage(executor)
            frames += withContext(Dispatchers.Default) {
                image.use { BitmapUtils.fromImageProxy(it) }
            }
        }
        return withContext(Dispatchers.Default) {
            NightModeProcessor.process(frames)
        }
    }

    private suspend fun captureHDR(): Bitmap {
        val normal = captureSingle()
        val dark = withContext(Dispatchers.Default) { BitmapUtils.adjustExposure(normal, 0.5f) }
        val bright = withContext(Dispatchers.Default) { BitmapUtils.adjustExposure(normal, 1.8f) }
        return withContext(Dispatchers.Default) {
            HDRProcessor.process(listOf(dark, normal, bright))
        }
    }

    private suspend fun capturePortrait(): Bitmap {
        val image = cameraController.captureImage(executor)
        val bitmap = withContext(Dispatchers.Default) {
            image.use { BitmapUtils.fromImageProxy(it) }
        }
        return withContext(Dispatchers.Default) {
            PortraitProcessor.process(bitmap)
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(dir, "MotoCamera").also { it.mkdirs() }
                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(appDir, "MC_$name.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            } catch (e: Exception) { }
        }
    }
}
