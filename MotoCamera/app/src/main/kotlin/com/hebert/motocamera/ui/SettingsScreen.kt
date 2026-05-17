package com.hebert.motocamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebert.motocamera.camera.AspectRatioOption
import com.hebert.motocamera.camera.CaptureResolution
import com.hebert.motocamera.camera.WhiteBalance

@Composable
fun SettingsScreen(
    state: CameraUiState,
    vm: CameraViewModel,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = Color.White, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text("Configurações", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Divider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(16.dp))

        // Aspect ratio
        SettingsSection("Proporção") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AspectRatioOption.values().forEach { ar ->
                    OptionChip(
                        label = ar.label,
                        selected = state.aspectRatio == ar,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setAspectRatio(ar) }
                    )
                }
            }
        }

        // Resolution
        SettingsSection("Resolução") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureResolution.values().forEach { res ->
                    OptionChip(
                        label = res.label,
                        selected = state.resolution == res,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setResolution(res) }
                    )
                }
            }
        }

        // White balance
        SettingsSection("Balanço de Brancos") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WhiteBalance.values().forEach { wb ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setWhiteBalance(wb) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(wb.label, color = Color.White, fontSize = 15.sp)
                            Text(
                                wbDescription(wb),
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                        }
                        if (state.whiteBalance == wb) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .background(Color(0xFFFFD600), shape = RoundedCornerShape(50))
                            )
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }

        // Timer
        SettingsSection("Temporizador") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0, 3, 5, 10).forEach { sec ->
                    OptionChip(
                        label = if (sec == 0) "Off" else "${sec}s",
                        selected = state.timerSeconds == sec,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setTimer(sec) }
                    )
                }
            }
        }

        // Mirror front camera
        SettingsSection("Câmera Frontal") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Espelhar imagem", color = Color.White, fontSize = 15.sp)
                    Text("Como ver no espelho", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                Switch(
                    checked = state.mirrorFrontCamera,
                    onCheckedChange = { vm.setMirrorFront(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFFFFD600)
                    )
                )
            }
        }

        // Sensor info
        SettingsSection("Câmera — Moto Edge 30 Neo") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorInfo("Principal", "64 MP · f/1.8 · PDAF · Samsung ISOCELL")
                SensorInfo("Ultra-wide", "13 MP · f/2.2 · 120°")
                SensorInfo("Macro", "2 MP")
                SensorInfo("Frontal", "32 MP · f/2.4")
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
    Divider(color = Color.White.copy(alpha = 0.06f))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun OptionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .background(
                if (selected) Color(0xFFFFD600) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SensorInfo(name: String, info: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(info, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

private fun wbDescription(wb: WhiteBalance) = when (wb) {
    WhiteBalance.AUTO -> "Ajuste automático contínuo"
    WhiteBalance.SUNNY -> "Luz do dia · ~5500K"
    WhiteBalance.CLOUDY -> "Céu nublado · ~6500K"
    WhiteBalance.INDOOR -> "Incandescente · ~3200K"
    WhiteBalance.FLUORESCENT -> "Fluorescente · ~4000K"
}
