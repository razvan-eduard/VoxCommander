package com.voxcommander.app.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.ui.components.TopHeaderContainer
import com.voxcommander.app.ui.components.TopHeaderMode
import com.voxcommander.app.ui.viewmodels.MainViewModel
import com.voxcommander.app.utils.Strings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    fastMapDao: FastMapDao,
    viewModel: MainViewModel,
    onDownloadVoskModel: (String, String, String) -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onSelectCustomVoskModel: (String) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    onDeleteUnusedModels: () -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    selectionSuccessMessage: String?,
    googleSttAvailable: Boolean,
    updateVoiceEngine: () -> Unit
) {
    val lastIntent by viewModel.currentIntent.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val transcription by viewModel.transcription.collectAsState()
    val isListening by VoiceManager.isListeningFlow.collectAsState()
    
    // --- REFRESH TRIGGER FOR MAIN MIC SAFEGUARD ---
    var micRefreshTrigger by remember { mutableIntStateOf(0) }

    // CHECK IF ACTIVE MODEL IS ON DEVICE (REACTIVE)
    val isModelOnDevice by remember(micRefreshTrigger, isListening) {
        derivedStateOf {
            val voiceProcessor = settingsManager.getVoiceProcessor()
            val voiceLanguage = settingsManager.getVoiceLanguage()
            
            when (voiceProcessor) {
                Strings.Processors.WHISPER_CPP,
                Strings.Processors.WHISPER_VULKAN,
                Strings.Processors.WHISPER_NEON -> {
                    val selectedModelId = settingsManager.getSelectedWhisperModelId()
                    settingsManager.isModelDownloaded(selectedModelId)
                }
                Strings.Processors.VOSK -> {
                    val customPath = settingsManager.getCustomVoskModelPath(voiceLanguage)
                    if (!customPath.isNullOrBlank()) {
                        File(customPath).exists()
                    } else {
                        val filesDir = File("/storage/emulated/0/Android/data/com.voxcommander.app/files")
                        if (filesDir.exists()) {
                            filesDir.listFiles()?.any { 
                                it.isDirectory && it.name.startsWith("vosk-model-") && it.name.contains(voiceLanguage, ignoreCase = true) 
                            } ?: false
                        } else false
                    }
                }
                else -> true 
            }
        }
    }

    var currentHeaderMode by remember { mutableStateOf(TopHeaderMode.NONE) }
    
    var manualText by remember { mutableStateOf("") }
    val gson = remember { GsonBuilder().setPrettyPrinting().create() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(languageManager.getString("main_screen")) },
                    actions = {
                        IconButton(onClick = { currentHeaderMode = TopHeaderMode.RULES }) {
                            Icon(Icons.Default.List, contentDescription = languageManager.getString("content_desc_rules"))
                        }
                        IconButton(onClick = { currentHeaderMode = TopHeaderMode.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = languageManager.getString("content_desc_settings"))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Manual Text Input
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text(languageManager.getString("manual_command")) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Button(onClick = { viewModel.processTextCommand(manualText) }) {
                            Text(languageManager.getString("test_button"))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Microphone Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { 
                            if (isProcessing) {
                                viewModel.stopVoiceCommand()
                            } else {
                                viewModel.processVoiceCommand(settingsManager.getVoiceLanguage(), settingsManager.getVoiceProcessor())
                            }
                        },
                        modifier = Modifier.size(150.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        enabled = isModelOnDevice, 
                        colors = if (isProcessing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
                             else if (!isModelOnDevice) ButtonDefaults.buttonColors(containerColor = Color.Gray)
                             else ButtonDefaults.buttonColors()
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = languageManager.getString("content_desc_record"),
                            modifier = Modifier.size(80.dp),
                            tint = if (isModelOnDevice) Color.White else Color.LightGray
                        )
                    }
                    
                    if (!isModelOnDevice) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = languageManager.getString("model_not_present"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = if (isProcessing) languageManager.getString("recording_status") else transcription)

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = languageManager.getString("last_intent"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = lastIntent?.let { gson.toJson(it) } ?: languageManager.getString("no_intent"),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }
        }

        ListeningScreen(languageManager = languageManager)

        // --- UNIFIED TOP HEADER CONTAINER ---
        TopHeaderContainer(
            mode = currentHeaderMode,
            languageManager = languageManager,
            settingsManager = settingsManager,
            fastMapDao = fastMapDao,
            onDismissRequest = { currentHeaderMode = TopHeaderMode.NONE },
            onDownloadVoskModel = onDownloadVoskModel,
            onDownloadWhisperModel = onDownloadWhisperModel,
            onSelectCustomVoskModel = onSelectCustomVoskModel,
            onSelectCustomWhisperModel = onSelectCustomWhisperModel,
            onDeleteUnusedModels = onDeleteUnusedModels,
            onCancelDownload = onCancelDownload,
            onRefreshMain = { micRefreshTrigger++ }, // REFRESH MIC STATE
            downloadProgress = downloadProgress,
            selectionSuccessMessage = selectionSuccessMessage,
            googleSttAvailable = googleSttAvailable,
            updateVoiceEngine = updateVoiceEngine
        )
    }
}
