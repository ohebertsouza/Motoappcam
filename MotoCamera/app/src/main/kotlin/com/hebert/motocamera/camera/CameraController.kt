package com.hebert.motocamera.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
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
    val sceneAnalyzer = SceneAnalyzer()
    val portraitAnalyzer = LivePortraitAnalyzer()
    private var currentAnalysis: ImageAnalysis? = null

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        resolution: CaptureResolution = CaptureResolution.MP12,
        aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_4_3
    ) {
        val provider = getCameraProvider()
        cameraProvider = provider

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspectRatio.ratio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    resolution.targetSize,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelector)

        val sCurve = floatArrayOf(
            0f, 0f, 0.1f, 0.08f, 0.25f, 0.22f,
            0.5f, 0.54f, 0.75f, 0.82f, 0.9f, 0.95f, 1f, 1f
        )
        try {
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(
                    CaptureRequest.TONEMAP_MODE,
                    CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
                )
                .setCaptureRequestOption(
                    CaptureRequest.TONEMAP_CURVE,
                    TonemapCurve(sCurve, sCurve, sCurve)
                )
        } catch (e: Exception) { }

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(97)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), sceneAnalyzer) }
        currentAnalysis = analysis

        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture!!, analysis)
    }

    fun switchToPortraitAnalyzer(portrait: Boolean) {
        val analyzer = if (portrait) portraitAnalyzer else sceneAnalyzer
        currentAnalysis?.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
    }

    fun applyModeSettings(mode: CaptureMode) {
        val cam = camera ?: return
        val control = Camera2CameraControl.from(cam.cameraControl)
        val options = CaptureRequestOptions.Builder().apply {
            when (mode) {
                CaptureMode.NIGHT -> {
                    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    setCaptureRequestOption(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
                }
                CaptureMode.HDR -> {
                    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                }
                CaptureMode.PORTRAIT -> {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)
                }
                CaptureMode.AUTO -> {
                    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                }
            }
        }.build()
        control.setCaptureRequestOptions(options)
    }

    fun applyWhiteBalance(wb: WhiteBalance) {
        val cam = camera ?: return
        val control = Camera2CameraControl.from(cam.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wb.awbMode)
            .build()
        control.setCaptureRequestOptions(options)
    }

    fun applyExposureCompensation(ev: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(ev)
    }

    fun getExposureRange(): Range<Int>? {
        val cam = camera ?: return null
        return cam.cameraInfo.exposureState.exposureCompensationRange
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setFlash(mode: Int) {
        imageCapture?.flashMode = mode
    }

    fun tapToFocus(x: Float, y: Float, surfaceWidth: Int, surfaceHeight: Int) {
        val factory = SurfaceOrientedMeteringPointFactory(
            surfaceWidth.toFloat(), surfaceHeight.toFloat()
        )
        val point = factory.createPoint(x * surfaceWidth, y * surfaceHeight)
        val action = FocusMeteringAction.Builder(point)
            .addPoint(point, FocusMeteringAction.FLAG_AE)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    suspend fun captureImage(executor: Executor): ImageProxy =
        suspendCancellableCoroutine { cont ->
            imageCapture?.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) =
                    cont.resumeWithException(exception)
            }) ?: cont.resumeWithException(IllegalStateException("Camera not initialized"))
        }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener(
                    { cont.resume(future.get()) },
                    ContextCompat.getMainExecutor(context)
                )
            }
        }
}
