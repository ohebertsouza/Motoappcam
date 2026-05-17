package com.hebert.motocamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.hebert.motocamera.processing.CaptureMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class WhiteBalance(val awbMode: Int, val label: String) {
    AUTO(CaptureRequest.CONTROL_AWB_MODE_AUTO, "Auto"),
    SUNNY(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, "Sol"),
    CLOUDY(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "Nublado"),
    INDOOR(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, "Interno"),
    FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, "Fluor.");
}

enum class CaptureResolution(val label: String, val targetSize: Size) {
    MP64("64 MP", Size(9248, 6944)),
    MP32("32 MP", Size(6560, 4920)),
    MP12("12 MP", Size(4032, 3024))
}

enum class AspectRatioOption(val label: String, val ratio: Int) {
    RATIO_4_3("4:3", AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", AspectRatio.RATIO_16_9)
}

class CameraController(private val context: Context) {

    private var camera: Camera? = null
    var imageCapture: ImageCapture? = null
        private set
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        resolution: CaptureResolution = CaptureResolution.MP12,
        aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_4_3
    ) {
        val provider = getCameraProvider()
        cameraProvider = provider
        currentLensFacing = lensFacing

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspectRatio.ratio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
            )
            .setResolutionStrategy(
                ResolutionStrategy(resolution.targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelector)

        val sCurve = floatArrayOf(0f,0f, 0.1f,0.08f, 0.25f,0.22f, 0.5f,0.54f, 0.75f,0.82f, 0.9f,0.95f, 1f,1f)
        try {
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONE
