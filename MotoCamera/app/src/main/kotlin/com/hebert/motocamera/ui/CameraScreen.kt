package com.hebert.motocamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebert.motocamera.processing.CaptureMode
import com.hebert.motocamera.processing.StyleProcessor

@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsState()

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Permissão de câmera necessária.\nReinicie o app.", color = Color.White, textAlign = TextAlign.Center)
        }
        return
    }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(state.lensFacing) {
        vm.cameraController.bindCamera(lifecycleOwner, previewView, state.lensFacing)
        vm.cameraController.applyModeSettings(state.mode)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { offset ->
                    vm.tapToFocus(offset.x / size.width, offset.y / size.height)
                }
            }
        )

        // Top controls
        TopBar(
            flashEnabled = state.flashEnabled,
            onFlashToggle = vm::toggleFlash,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 16.dp)
        )

        // Bottom panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(bottom = 24.dp)
        ) {
            // Style pad (collapsible)
            AnimatedVisibility(visible = state.showStylePad) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Preset chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(StyleProcessor.PRESETS.keys.toList()) { preset ->
                            PresetChip(
                                name = preset,
                                selected = state.selectedPreset == preset,
                                onClick = { vm.selectPreset(preset) }
                            )
                        }
                    }

                    StylePad(
                        style = state.style,
                        onStyleChange = vm::setStyle,
                        modifier = Modifier.fillMaxWidth()
                    )

                    StyleValues(state.style)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mode selector
            ModeSelector(
                currentMode = state.mode,
                onModeSelect = vm::setMode,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Capture row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .clickable { if (state.lastCapture != null) vm.dismissPreview() },
                    contentAlignment = Alignment.Center
                ) {
                    state.lastCapture?.let { bmp ->
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize())
                    }
                }

                // Shutter button
                ShutterButton(
                    isCapturing = state.isCapturing,
                    mode = state.mode,
                    onClick = vm::capture
                )

                // Style + flip column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = vm::toggleStylePad,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (state.showStylePad) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Text("✦", color = Color.White, fontSize = 20.sp)
                    }
                    Text("Estilo", color = Color.White, fontSize = 10.sp)
                }
            }
        }

        // Photo preview overlay
        if (state.showPreview && state.lastCapture != null) {
            PhotoPreviewOverlay(
                bitmap = state.lastCapture!!,
                onDismiss = vm::dismissPreview
            )
        }

        // Error snackbar
        state.errorMessage?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                Card(
                    Modifier.padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Text(msg, color = Color.White, Modifier.padding(12.dp))
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
    flashEnabled: Boolean,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onFlashToggle) {
            Text(if (flashEnabled) "⚡" else "⚡", color = if (flashEnabled) Color.Yellow else Color.White, fontSize = 22.sp)
        }
        Text("MotoCamera", color = Color.White, fontWeight = FontWeight.Light, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterVertically))
        Spacer(Modifier.size(48.dp))
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

    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        modes.forEach { (mode, label) ->
            val selected = currentMode == mode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onModeSelect(mode) }.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color(0xFFFFD600) else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(4.dp, 2.dp).background(Color(0xFFFFD600), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    mode: CaptureMode,
    onClick: () -> Unit
) {
    val modeColor = when (mode) {
        CaptureMode.NIGHT -> Color(0xFF1565C0)
        CaptureMode.HDR -> Color(0xFF2E7D32)
        CaptureMode.PORTRAIT -> Color(0xFF6A1B9A)
        CaptureMode.AUTO -> Color.White
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.dp, modeColor, CircleShape)
            .clickable(enabled = !isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(Modifier.size(36.dp), color = modeColor, strokeWidth = 3.dp)
        } else {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)))
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            name,
            color = if (selected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StyleValues(style: StyleProcessor.StylePoint) {
    val tonePct = (style.tone * 100).toInt()
    val moodPct = (style.mood * 100).toInt()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        Text(
            "Tom: ${if (tonePct >= 0) "+$tonePct" else "$tonePct"}",
            color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp
        )
        Text(
            "Humor: ${if (moodPct >= 0) "+$moodPct" else "$moodPct"}",
            color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp
        )
    }
}

@Composable
private fun PhotoPreviewOverlay(bitmap: Bitmap, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "Foto capturada",
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("✕", color = Color.White, fontSize = 24.sp,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(8.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
            Text("Toque para fechar", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
