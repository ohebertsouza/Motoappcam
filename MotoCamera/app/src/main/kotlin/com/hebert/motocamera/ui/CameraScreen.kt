package com.hebert.motocamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebert.motocamera.camera.AspectRatioOption
import com.hebert.motocamera.processing.CaptureMode
import com.hebert.motocamera.processing.StyleProcessor

@Composable
fun CameraApp() {
    val vm: CameraViewModel = viewModel()
    val state by vm.state.collectAsState()

    when (state.screen) {
        AppScreen.CAMERA -> CameraScreen(vm = vm, state = state)
        AppScreen.SETTINGS -> SettingsScreen(state = state, vm = vm, onBack = { vm.navigate(AppScreen.CAMERA) })
        AppScreen.GALLERY -> GalleryScreen(captures = state.recentCaptures, onBack = { vm.navigate(AppScreen.CAMERA) })
    }
}

@Composable
fun CameraScreen(vm: CameraViewModel, state: CameraUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                text = "Permissão de câmera necessária.\nReinicie o app.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val previewView = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }}

    LaunchedEffect(state.lensFacing, state.resolution, state.aspectRatio, state.needsRebind) {
        if (state.needsRebind || true) {
            vm.cameraController.bindCamera(
                lifecycleOwner, previewView,
                state.lensFacing, state.resolution, state.aspectRatio
            )
            vm.cameraController.applyModeSettings(state.mode)
            vm.cameraController.applyWhiteBalance(state.whiteBalance)
            vm.updateExposureRange()
            vm.onRebindComplete()
        }
    }

    var previewWidth by remember { mutableStateOf(1) }
    var previewHeight by remember { mutableStateOf(1) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Mirror front camera
                    if (state.lensFacing == CameraSelector.LENS_FACING_FRONT && state.mirrorFrontCamera) {
                        scaleX = -1f
                    }
                }
                .pointerInput(Unit) {
                    previewWidth = size.width
                    previewHeight = size.height
                    detectTapGestures { offset ->
                        vm.tapToFocus(offset.x, offset.y, size.width, size.height)
                    }
                }
        )

        // Face overlay
        if (state.mode == CaptureMode.PORTRAIT) {
            FaceOverlay(bitmap = state.lastCapture)
        }

        // Top bar
        TopBar(
            state = state,
            onSettings = { vm.navigate(AppScreen.SETTINGS) },
            onFlash = vm::toggleFlash,
            onFlip = vm::flipCamera,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        )

        // Exposure slider
        AnimatedVisibility(
            visible = state.showExposureSlider,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            ExposureSlider(
                value = state.exposureIndex,
                min = state.exposureMin,
                max = state.exposureMax,
                onValueChange = vm::setExposure,
                onDismiss = vm::dismissExposureSlider
            )
        }

        // Timer countdown overlay
        if (state.isTimerRunning && state.timerCountdown > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val scale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = repeatable(
                        iterations = 1,
                        animation = tween(800, easing = EaseOutBack)
                    ), label = "timer_scale"
                )
                Text(
                    text = "${state.timerCountdown}",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Thin,
                    modifier = Modifier.scale(scale).graphicsLayer { alpha = 0.9f }
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Style pad
            AnimatedVisibility(visible = state.showStylePad) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        items(StyleProcessor.PRESETS.keys.toList()) { preset ->
                            PresetChip(
                                name = preset,
                                selected = state.selectedPreset == preset,
                                onClick = { vm.selectPreset(preset) }
                            )
                        }
                    }
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            StylePad(
                                style = state.style,
                                onStyleChange = vm::setStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                            StyleValues(state.style)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Mode selector
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                ModeSelector(
                    currentMode = state.mode,
                    onModeSelect = vm::setMode,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Capture row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail / gallery
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .clickable { vm.navigate(AppScreen.GALLERY) },
                    contentAlignment = Alignment.Center
                ) {
                    val lastBmp = state.recentCaptures.lastOrNull()
                    if (lastBmp != null) {
                        Image(
                            bitmap = lastBmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Text("⊞", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp)
                    }
                }

                // Shutter
                ShutterButton(
                    isCapturing = state.isCapturing,
                    isTimerRunning = state.isTimerRunning,
                    mode = state.mode,
                    onClick = vm::capture,
                    onCancelTimer = vm::cancelTimer
                )

                // Right controls column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassPill(modifier = Modifier.size(44.dp).clickable { vm.toggleStylePad() }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "✦",
                                color = if (state.showStylePad) Color(0xFFFFD600) else Color.White,
                                fontSize = 18.sp
                            )
                        }
                    }
                    if (state.timerSeconds > 0) {
                        GlassPill(modifier = Modifier.wrapContentSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                "${state.timerSeconds}s",
                                color = Color(0xFFFFD600),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Photo preview overlay
        if (state.showPreview && state.lastCapture != null) {
            PhotoPreviewOverlay(bitmap = state.lastCapture!!, onDismiss = vm::dismissPreview)
        }

        // Error
        state.errorMessage?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 140.dp), contentAlignment = Alignment.BottomCenter) {
                GlassPanel(Modifier.padding(horizontal = 24.dp)) {
                    Text(msg, color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                }
            }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                vm.dismissError()
            }
        }
    }
}

@Composable
private fun TopBar(
    state: CameraUiState,
    onSettings: () -> Unit,
    onFlash: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        GlassPill(modifier = Modifier.clickable(onClick = onSettings)) {
            Text("⚙", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(10.dp))
        }

        GlassPill {
            Text(
                text = state.resolution.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassPill(modifier = Modifier.clickable(onClick = onFlash)) {
                Text(
                    if (state.flashEnabled) "⚡" else "⚡",
                    color = if (state.flashEnabled) Color(0xFFFFD600) else Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
            GlassPill(modifier = Modifier.clickable(onClick = onFlip)) {
                Text("⟳", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun ModeSelector(
    currentMode: CaptureMode,
    onModeSelect: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        CaptureMode.NIGHT to "NOITE",
        CaptureMode.AUTO to "AUTO",
        CaptureMode.HDR to "HDR",
        CaptureMode.PORTRAIT to "RETRATO",
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        modes.forEach { (mode, label) ->
            val selected = currentMode == mode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onModeSelect(mode) }.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) Color(0xFFFFD600) else Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp, 2.dp).background(Color(0xFFFFD600), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    isTimerRunning: Boolean,
    mode: CaptureMode,
    onClick: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val modeColor = when (mode) {
        CaptureMode.NIGHT -> Color(0xFF82B1FF)
        CaptureMode.HDR -> Color(0xFF69F0AE)
        CaptureMode.PORTRAIT -> Color(0xFFEA80FC)
        CaptureMode.AUTO -> Color.White
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(2.5.dp, modeColor.copy(alpha = 0.7f), CircleShape)
            .clickable { if (isTimerRunning) onCancelTimer() else onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            isCapturing -> CircularProgressIndicator(
                modifier = Modifier.size(38.dp),
                color = modeColor,
                strokeWidth = 2.5.dp
            )
            isTimerRunning -> Text("✕", color = Color.White, fontSize = 24.sp)
            else -> Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
            )
        }
    }
}

@Composable
private fun ExposureSlider(
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .width(48.dp)
            .height(220.dp),
        cornerRadius = 24.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("☀", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier
                    .height(140.dp)
                    .graphicsLayer { rotationZ = -90f },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFD600),
                    activeTrackColor = Color(0xFFFFD600),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Text("☽", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .border(
                0.5.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = name,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StyleValues(style: StyleProcessor.StylePoint) {
    val t = (style.tone * 100).toInt()
    val m = (style.mood * 100).toInt()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        Text("Tom ${if (t >= 0) "+$t" else "$t"}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text("Humor ${if (m >= 0) "+$m" else "$m"}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun PhotoPreviewOverlay(bitmap: Bitmap, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
            GlassPill(modifier = Modifier.size(42.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
            GlassPill {
                Text(
                    "Salvo · Toque para fechar",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
