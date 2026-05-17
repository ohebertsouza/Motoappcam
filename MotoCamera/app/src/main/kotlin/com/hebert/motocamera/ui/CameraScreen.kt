package com.hebert.motocamera.ui

import android.graphics.Bitmap
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebert.motocamera.processing.CaptureMode
import com.hebert.motocamera.processing.StyleProcessor

@Composable
fun CameraApp(vm: CameraViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    when (state.screen) {
        AppScreen.CAMERA -> CameraScreen(vm)
        AppScreen.SETTINGS -> SettingsScreen(
            state = state,
            vm = vm,
            onBack = { vm.navigate(AppScreen.CAMERA) }
        )
       AppScreen.GALLERY -> GalleryScreen(
    captures = state.recentCaptures,
    onBack = { vm.navigate(AppScreen.CAMERA) }
)
    }
}

@Composable
fun CameraScreen(vm: CameraViewModel) {
    val state by vm.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var surfaceW by remember { mutableStateOf(1) }
    var surfaceH by remember { mutableStateOf(1) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val portraitFrame by vm.cameraController.portraitAnalyzer.frame.collectAsState()

    LaunchedEffect(previewView, state.lensFacing, state.resolution, state.aspectRatio, state.needsRebind) {
        val pv = previewView ?: return@LaunchedEffect
        try {
            vm.cameraController.bindCamera(
                lifecycleOwner, pv,
                state.lensFacing, state.resolution, state.aspectRatio
            )
            vm.onRebindComplete()
            vm.updateExposureRange()
        } catch (e: Exception) { }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (state.lensFacing == CameraSelector.LENS_FACING_FRONT
                        && state.mirrorFrontCamera) scaleX = -1f
                }
                .onSizeChanged { surfaceW = it.width; surfaceH = it.height }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        vm.setZoom(state.zoom * zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        vm.tapToFocus(offset.x, offset.y, surfaceW, surfaceH)
                    }
                }
        )

        // Live portrait overlay
        if (state.mode == CaptureMode.PORTRAIT) {
            portraitFrame?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (state.lensFacing == CameraSelector.LENS_FACING_FRONT
                                && state.mirrorFrontCamera) scaleX = -1f
                        }
                )
            }
        }

        // Focus ring
        if (state.showFocusRing) {
            FocusRing(
                x = state.focusX / surfaceW.toFloat(),
                y = state.focusY / surfaceH.toFloat()
            )
        }

        TopBar(state, vm)
        ModeSelector(state, vm)
        BottomControls(state, vm)

        if (state.showExposureSlider) {
            ExposureSlider(state, vm)
        }

        if (state.isTimerRunning) {
            TimerOverlay(state.timerCountdown)
        }

        if (state.showPreview && state.lastCapture != null) {
            CapturePreviewOverlay(state.lastCapture!!, vm)
        }

        if (state.showStylePad) {
            StylePadOverlay(state, vm)
        }

        state.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { vm.dismissError() }) { Text(text = "OK") } }
            ) { Text(text = msg) }
        }
    }
}

@Composable
private fun BoxScope.TopBar(state: CameraUiState, vm: CameraViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPill(modifier = Modifier.size(44.dp)) {
            IconButton(onClick = { vm.toggleFlash() }) {
                Text(text = if (state.flashEnabled) "⚡" else "☁", fontSize = 18.sp)
            }
        }

        GlassPill {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = state.sceneLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (state.autoModeActive) {
                    Text(text = "AUTO", color = Color(0xFFFFD600), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        GlassPill(modifier = Modifier.size(44.dp)) {
            IconButton(onClick = { vm.navigate(AppScreen.SETTINGS) }) {
                Text(text = "⚙", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.ModeSelector(state: CameraUiState, vm: CameraViewModel) {
    val modes = listOf(
        CaptureMode.AUTO to "Auto",
        CaptureMode.PORTRAIT to "Retrato",
        CaptureMode.HDR to "HDR",
        CaptureMode.NIGHT to "Noite"
    )
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 160.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { (mode, label) ->
            val selected = state.mode == mode
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Color(0xFFFFD600) else Color.White.copy(alpha = 0.15f))
                    .clickable { vm.setMode(mode); vm.setAutoMode(false) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun BoxScope.BottomControls(state: CameraUiState, vm: CameraViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable { vm.navigate(AppScreen.GALLERY) },
            contentAlignment = Alignment.Center
        ) {
            state.recentCaptures.lastOrNull()?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Text(text = "🖼", fontSize = 22.sp)
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { vm.capture() },
            contentAlignment = Alignment.Center
        ) {
            if (state.isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).border(3.dp, Color.Black, CircleShape))
            }
        }

        GlassPill(modifier = Modifier.size(52.dp)) {
            IconButton(onClick = { vm.flipCamera() }) {
                Text(text = "🔄", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.ExposureSlider(state: CameraUiState, vm: CameraViewModel) {
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassPanel(cornerRadius = 32.dp) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "☀", fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = state.exposureIndex.toFloat(),
                    onValueChange = { vm.setExposure(it.toInt()) },
                    valueRange = state.exposureMin.toFloat()..state.exposureMax.toFloat(),
                    modifier = Modifier.height(140.dp).rotate(-90f).width(140.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD600),
                        activeTrackColor = Color(0xFFFFD600)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.exposureIndex >= 0) "+${state.exposureIndex}" else "${state.exposureIndex}",
                    color = Color.White, fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        GlassPill {
            Box(modifier = Modifier.padding(8.dp).clickable { vm.dismissExposureSlider() }) {
                Text(text = "✕", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.TimerOverlay(countdown: Int) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$countdown",
            color = Color.White,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BoxScope.CapturePreviewOverlay(bitmap: Bitmap, vm: CameraViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { vm.dismissPreview() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(20.dp))
        )
        GlassPill(modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable { vm.dismissPreview() }) {
                Text(text = "✕", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.StylePadOverlay(state: CameraUiState, vm: CameraViewModel) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Estilo Fotográfico", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StyleProcessor.PRESETS.keys.toList()) { name ->
                        val selected = state.selectedPreset == name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Color(0xFFFFD600) else Color.White.copy(alpha = 0.15f))
                                .clickable { vm.selectPreset(name) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val padSizePx = 220f
                Box(
                    modifier = Modifier
                        .size(padSizePx.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .pointerInput(state.style) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
val ny = (1f - offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                vm.setStyle(StyleProcessor.StylePoint(nx * 2f - 1f, ny * 2f - 1f))
                            }
                        }
                        .pointerInput(state.style) {
                            detectTransformGestures { _, pan, _, _ ->
                                val curX = state.style.tone / 2f + 0.5f
val curY = state.style.mood / 2f + 0.5f
val nx = (curX + pan.x / size.width.toFloat()).coerceIn(0f, 1f)
val ny = (curY - pan.y / size.height.toFloat()).coerceIn(0f, 1f)
                                vm.setStyle(StyleProcessor.StylePoint(nx * 2f - 1f, ny * 2f - 1f))
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // grid lines
                        val step = size.width / 4f
                        for (i in 1..3) {
                            drawLine(Color.White.copy(alpha = 0.1f),
                                androidx.compose.ui.geometry.Offset(step * i, 0f),
                                androidx.compose.ui.geometry.Offset(step * i, size.height), strokeWidth = 1f)
                            drawLine(Color.White.copy(alpha = 0.1f),
                                androidx.compose.ui.geometry.Offset(0f, step * i),
                                androidx.compose.ui.geometry.Offset(size.width, step * i), strokeWidth = 1f)
                        }
                        // dot
                        val dotCx = (state.style.tone / 2f + 0.5f) * size.width
val dotCy = (1f - (state.style.mood / 2f + 0.5f)) * size.height
                        drawCircle(color = Color(0xFFFFD600), radius = 12f,
                            center = androidx.compose.ui.geometry.Offset(dotCx, dotCy))
                        drawCircle(color = Color.White, radius = 12f,
                            center = androidx.compose.ui.geometry.Offset(dotCx, dotCy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }

                    Text(text = "Vivid →", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    Text(text = "Warm ↑", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassPill {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { vm.selectPreset("Standard") }) {
                            Text(text = "Reset", color = Color.White, fontSize = 13.sp)
                        }
                    }
                    GlassPill {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { vm.toggleStylePad() }) {
                            Text(text = "Fechar", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
