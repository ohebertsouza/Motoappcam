package com.hebert.motocamera.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebert.motocamera.camera.CameraController
import com.hebert.motocamera.processing.CaptureMode
import com.hebert.motocamera.processing.CaptureOrchestrator
import com.hebert.motocamera.processing.StyleProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraUiState(
    val mode: CaptureMode = CaptureMode.AUTO,
    val style: StyleProcessor.StylePoint = StyleProcessor.StylePoint(0f, 0f),
    val isCapturing: Boolean = false,
    val lastCapture: Bitmap? = null,
    val showStylePad: Boolean = false,
    val showPreview: Boolean = false,
    val errorMessage: String? = null,
    val zoom: Float = 1f,
    val flashEnabled: Boolean = false,
    val lensFacing: Int = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
    val selectedPreset: String = "Standard"
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraController = CameraController(application)
    private val orchestrator = CaptureOrchestrator(application, cameraController)

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    fun setMode(mode: CaptureMode) {
        _state.value = _state.value.copy(mode = mode)
        cameraController.applyModeSettings(mode)
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
            if (newFlash) androidx.camera.core.ImageCapture.FLASH_MODE_ON
            else androidx.camera.core.ImageCapture.FLASH_MODE_OFF
        )
    }

    fun setZoom(ratio: Float) {
        _state.value = _state.value.copy(zoom = ratio.coerceIn(1f, 10f))
        cameraController.setZoom(_state.value.zoom)
    }

    fun tapToFocus(x: Float, y: Float) {
        cameraController.tapToFocus(x, y)
    }

    fun capture() {
        if (_state.value.isCapturing) return
        _state.value = _state.value.copy(isCapturing = true, errorMessage = null)

        viewModelScope.launch {
            val result = orchestrator.capture(_state.value.mode, _state.value.style)
            result.onSuccess { bitmap ->
                _state.value = _state.value.copy(
                    lastCapture = bitmap,
                    isCapturing = false,
                    showPreview = true
                )
                orchestrator.saveBitmap(bitmap)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isCapturing = false,
                    errorMessage = "Erro ao capturar: ${e.message}"
                )
            }
        }
    }

    fun dismissPreview() {
        _state.value = _state.value.copy(showPreview = false, lastCapture = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
