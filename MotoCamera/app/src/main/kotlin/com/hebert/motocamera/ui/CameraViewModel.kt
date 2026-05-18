package com.hebert.motocamera.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebert.motocamera.camera.AspectRatioOption
import com.hebert.motocamera.camera.CameraController
import com.hebert.motocamera.camera.CaptureResolution
import com.hebert.motocamera.camera.WhiteBalance
import com.hebert.motocamera.processing.CaptureMode
import com.hebert.motocamera.processing.CaptureOrchestrator
import com.hebert.motocamera.processing.StyleProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen { CAMERA, SETTINGS, GALLERY }

data class CameraUiState(
    val screen: AppScreen = AppScreen.CAMERA,
    val mode: CaptureMode = CaptureMode.AUTO,
    val style: StyleProcessor.StylePoint = StyleProcessor.StylePoint(0f, 0f),
    val selectedPreset: String = "Standard",
    val isCapturing: Boolean = false,
    val lastCapture: Bitmap? = null,
    val recentCaptures: List<Bitmap> = emptyList(),
    val showStylePad: Boolean = false,
    val showPreview: Boolean = false,
    val errorMessage: String? = null,
    val zoom: Float = 1f,
    val flashEnabled: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val mirrorFrontCamera: Boolean = true,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val resolution: CaptureResolution = CaptureResolution.MP12,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_4_3,
    val exposureIndex: Int = 0,
    val exposureMin: Int = -6,
    val exposureMax: Int = 6,
    val timerSeconds: Int = 0,
    val timerCountdown: Int = 0,
    val isTimerRunning: Boolean = false,
    val showExposureSlider: Boolean = false,
    val faceCount: Int = 0,
    val needsRebind: Boolean = false,
    val sceneLabel: String = "Auto",
    val autoModeActive: Boolean = true,
    val focusX: Float = 0f,
    val focusY: Float = 0f,
    val showFocusRing: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraController = CameraController(application)
    private val orchestrator = CaptureOrchestrator(application, cameraController)

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            cameraController.sceneAnalyzer.scene.collect { scene ->
                _state.value = _state.value.copy(sceneLabel = scene.label)
                if (_state.value.autoModeActive) {
                    if (scene.suggestedMode != _state.value.mode) {
                        _state.value = _state.value.copy(mode = scene.suggestedMode)
                        cameraController.applyModeSettings(scene.suggestedMode)
                    }
                }
            }
        }
    }

    fun setAutoMode(enabled: Boolean) {
        _state.value = _state.value.copy(autoModeActive = enabled)
    }

    fun navigate(screen: AppScreen) {
        _state.value = _state.value.copy(screen = screen)
    }

    fun setMode(mode: CaptureMode) {
        _state.value = _state.value.copy(mode = mode)
        cameraController.applyModeSettings(mode)
        cameraController.switchToPortraitAnalyzer(mode == CaptureMode.PORTRAIT)
    }

    fun setStyle(style: StyleProcessor.StylePoint) {
        _state.value = _state.value.copy(style = style, selectedPreset = "Custom")
    }

    fun selectPreset(name: String) {
        val point = StyleProcessor.PRESETS[name] ?: return
        _state.value = _state.value.copy(style = point, selectedPreset = name)
    }

    fun toggleStylePad() {
        _state.value = _state.value.copy(showStylePad = !_state.value.showStylePad)
    }

    fun toggleFlash() {
        val newFlash = !_state.value.flashEnabled
        _state.value = _state.value.copy(flashEnabled = newFlash)
        cameraController.setFlash(
            if (newFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        )
    }

    fun flipCamera() {
        val newFacing = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        _state.value = _state.value.copy(lensFacing = newFacing, needsRebind = true)
    }

    fun onRebindComplete() {
        _state.value = _state.value.copy(needsRebind = false)
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(1f, 10f)
        _state.value = _state.value.copy(zoom = clamped)
        cameraController.setZoom(clamped)
    }

    fun tapToFocus(x: Float, y: Float, w: Int, h: Int) {
        cameraController.tapToFocus(x, y, w, h)
        _state.value = _state.value.copy(
            showExposureSlider = true,
            focusX = x,
            focusY = y,
            showFocusRing = true
        )
        viewModelScope.launch {
            // Aguarda AE estabilizar depois do foco (~800ms) e lê o EV real da câmera
            delay(800)
            val newEv = cameraController.getCurrentExposureIndex()
            if (newEv != null) {
                _state.value = _state.value.copy(exposureIndex = newEv)
            }
            delay(1000)
            _state.value = _state.value.copy(showFocusRing = false)
        }
    }

    fun setExposure(index: Int) {
        _state.value = _state.value.copy(exposureIndex = index)
        cameraController.applyExposureCompensation(index)
    }

    fun updateExposureRange() {
        val range = cameraController.getExposureRange() ?: return
        _state.value = _state.value.copy(
            exposureMin = range.lower,
            exposureMax = range.upper
        )
    }

    fun dismissExposureSlider() {
        _state.value = _state.value.copy(showExposureSlider = false)
    }

    fun setWhiteBalance(wb: WhiteBalance) {
        _state.value = _state.value.copy(whiteBalance = wb)
        cameraController.applyWhiteBalance(wb)
    }

    fun setResolution(res: CaptureResolution) {
        _state.value = _state.value.copy(resolution = res, needsRebind = true)
    }

    fun setAspectRatio(ar: AspectRatioOption) {
        _state.value = _state.value.copy(aspectRatio = ar, needsRebind = true)
    }

    fun setTimer(seconds: Int) {
        _state.value = _state.value.copy(timerSeconds = seconds)
    }

    fun setMirrorFront(mirror: Boolean) {
        _state.value = _state.value.copy(mirrorFrontCamera = mirror)
    }

    fun capture() {
        if (_state.value.isCapturing || _state.value.isTimerRunning) return
        val timerSec = _state.value.timerSeconds
        if (timerSec > 0) {
            startTimerAndCapture(timerSec)
        } else {
            doCapture()
        }
    }

    private fun startTimerAndCapture(seconds: Int) {
        timerJob?.cancel()
        _state.value = _state.value.copy(isTimerRunning = true, timerCountdown = seconds)
        timerJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _state.value = _state.value.copy(timerCountdown = i)
                delay(1000)
            }
            _state.value = _state.value.copy(isTimerRunning = false, timerCountdown = 0)
            doCapture()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _state.value = _state.value.copy(isTimerRunning = false, timerCountdown = 0)
    }

    private fun doCapture() {
        _state.value = _state.value.copy(isCapturing = true, errorMessage = null)
        viewModelScope.launch {
            val result = orchestrator.capture(_state.value.mode, _state.value.style)
            result.onSuccess { bitmap ->
                val recents = (_state.value.recentCaptures + bitmap).takeLast(30)
                _state.value = _state.value.copy(
                    lastCapture = bitmap,
                    recentCaptures = recents,
                    isCapturing = false,
                    showPreview = true
                )
                orchestrator.saveBitmap(bitmap)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isCapturing = false,
                    errorMessage = "Erro: ${e.message}"
                )
            }
        }
    }

    fun dismissPreview() {
        _state.value = _state.value.copy(showPreview = false)
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
