package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxcommander.app.domain.diagnostic.BenchmarkEngine
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun BenchmarkSettingsTab(
    languageManager: LanguageManager,
    appStateManager: AppStateManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceState by appStateManager.voiceState.collectAsState()
    val benchmarkResults by appStateManager.benchmarkResults.collectAsState()
    val nativeLibsStatus by appStateManager.nativeLibsStatus.collectAsState()
    val systemInfo by appStateManager.systemInfo.collectAsState()
    
    val settingsManager = remember { com.voxcommander.app.data.preferences.SettingsManager(context) }
    val benchmarkEngine = remember { BenchmarkEngine(context, settingsManager, appStateManager) }
    
    val isRunning = voiceState == VoiceState.BENCHMARKING

    LaunchedEffect(Unit) {
        appStateManager.refreshNativeLibsStatus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Global Engine Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Synthetic speed test across cloud services and downloaded on-device models.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { scope.launch { benchmarkEngine.runFullBenchmark() } },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running All Tests...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Benchmark")
                        }
                    }
                }
            }
        }

        if (benchmarkResults.isNotEmpty()) {
            item { Text(text = "Performance Metrics", style = MaterialTheme.typography.titleSmall) }
            items(benchmarkResults) { result -> BenchmarkResultItem(result) }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Native Library Inventory", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { appStateManager.refreshNativeLibsStatus() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }
        }
        
        item {
            val whisperLibs = nativeLibsStatus.filter { it.name.contains("whisper") || it.name.contains("ggml") }
            val voskLibs = nativeLibsStatus.filter { it.name.contains("vosk") }
            val llamaLibs = nativeLibsStatus.filter { it.name.contains("mediapipe") || it.name.contains("genai") }
            val otherLibs = nativeLibsStatus.filter { !whisperLibs.contains(it) && !voskLibs.contains(it) && !llamaLibs.contains(it) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (whisperLibs.isNotEmpty()) EngineLibGroupCard("Voice: Whisper & GGML", whisperLibs)
                if (voskLibs.isNotEmpty()) EngineLibGroupCard("Voice: Vosk Engine", voskLibs)
                if (llamaLibs.isNotEmpty()) EngineLibGroupCard("Intent: Llama (MediaPipe)", llamaLibs)
                if (otherLibs.isNotEmpty()) EngineLibGroupCard("System & Runtime", otherLibs)
            }
        }

        if (systemInfo.isNotBlank()) {
            item {
                Text(text = "Engine Runtime Diagnostics", style = MaterialTheme.typography.titleSmall)
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                    Text(text = systemInfo, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color.Green, modifier = Modifier.padding(12.dp), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun BenchmarkResultItem(result: com.voxcommander.app.state.BenchmarkResult) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (result.isSuccess) Color.Gray.copy(alpha = 0.3f) else Color.Red)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${result.engine} (${result.model})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (result.isSuccess) {
                    Text(text = "Time: ${result.inferenceTimeMs}ms | RTF: ${String.format(Locale.US, "%.2fx", result.rtf)}", style = MaterialTheme.typography.bodySmall, color = if (result.rtf < 0.5f) Color(0xFF4CAF50) else Color.Gray)
                } else {
                    Text(text = "Error: ${result.error}", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                }
            }
            if (result.isSuccess) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
            else Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
        }
    }
}

@Composable
fun EngineLibGroupCard(title: String, libs: List<com.voxcommander.app.state.NativeLibStatus>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            libs.forEach { lib -> NativeLibStatusItem(lib) }
        }
    }
}

@Composable
fun NativeLibStatusItem(lib: com.voxcommander.app.state.NativeLibStatus) {
    val statusColor = when {
        lib.isActive && lib.exists -> Color.Green
        !lib.isActive && lib.exists -> Color(0xFF2196F3) // Blue (Standby)
        lib.isActive && !lib.exists -> Color.Red // CRITICAL: Active but missing
        else -> Color.Gray // Not present, not active
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(statusColor, shape = androidx.compose.foundation.shape.CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = lib.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    lib.isActive && lib.exists -> "${lib.description} (Active)"
                    !lib.isActive && lib.exists -> "${lib.description} (Ready)"
                    lib.isActive && !lib.exists -> "${lib.description} (MISSING!)"
                    else -> "${lib.description} (Not Installed)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (lib.isActive && !lib.exists) Color.Red else Color.Gray
            )
        }
    }
}
