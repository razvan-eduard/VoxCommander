package com.voxcommander.app.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.MicrophoneButton
import com.voxcommander.app.ui.components.ModelNotPresentMessage
import com.voxcommander.app.ui.components.TopHeaderContainer
import com.voxcommander.app.ui.components.TopHeaderMode
import com.voxcommander.app.ui.components.VulkanTestModal
import com.voxcommander.app.ui.viewmodels.MainViewModel
import com.voxcommander.app.utils.Strings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    fastMapDao: FastMapDao,
    viewModel: MainViewModel,
    modelManagementViewModel: com.voxcommander.app.ui.viewmodels.ModelManagementViewModel,
    onDownloadVoskModel: (String, String, String) -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onSelectCustomVoskModel: (String) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    onDeleteUnusedModels: () -> Unit,
    onDownloadLlamaModel: (AppModel) -> Unit,
    onDeleteLlamaModel: (AppModel) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    selectionSuccessMessage: String?,
    googleSttAvailable: Boolean,
    updateVoiceEngine: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val lastIntent by viewModel.currentIntent.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val transcription by viewModel.transcription.collectAsStateWithLifecycle()
    val isListening by VoiceManager.isListeningFlow.collectAsStateWithLifecycle()
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

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
                    MicrophoneButton(
                        languageManager = languageManager,
                        appStateManager = appStateManager,
                        isProcessing = isProcessing,
                        onClick = {
                            if (isProcessing) {
                                viewModel.stopVoiceCommand()
                            } else {
                                viewModel.processVoiceCommand(uiState.voiceLanguage, uiState.voiceProcessor)
                            }
                        }
                    )

                    ModelNotPresentMessage(
                        languageManager = languageManager,
                        appStateManager = appStateManager
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show transcription only, "Recording" status is now handled visually by the button
                Text(
                    text = if (isProcessing && transcription.isEmpty()) "" else transcription,
                    style = MaterialTheme.typography.bodyMedium
                )

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

        // --- UNIFIED TOP HEADER CONTAINER ---
        TopHeaderContainer(
            mode = currentHeaderMode,
            languageManager = languageManager,
            settingsManager = settingsManager,
            appStateManager = appStateManager,
            modelManagementViewModel = modelManagementViewModel,
            fastMapDao = fastMapDao,
            onDismissRequest = { currentHeaderMode = TopHeaderMode.NONE },
            onDownloadVoskModel = onDownloadVoskModel,
            onDownloadWhisperModel = onDownloadWhisperModel,
            onSelectCustomVoskModel = onSelectCustomVoskModel,
            onSelectCustomWhisperModel = onSelectCustomWhisperModel,
            onDeleteUnusedModels = onDeleteUnusedModels,
            onDownloadLlamaModel = onDownloadLlamaModel,
            onDeleteLlamaModel = onDeleteLlamaModel,
            onCancelDownload = onCancelDownload,
            onRefreshMain = { /* AppStateManager handles state updates automatically */ },
            downloadProgress = downloadProgress,
            selectionSuccessMessage = selectionSuccessMessage,
            googleSttAvailable = googleSttAvailable,
            updateVoiceEngine = updateVoiceEngine,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onRequestMicrophonePermission = onRequestMicrophonePermission,
            onRequestNotificationPermission = onRequestNotificationPermission
        )

        // --- VULKAN TEST MODAL ---
        VulkanTestModal(
            vulkanTestState = appStateManager.vulkanTestState.collectAsStateWithLifecycle().value,
            vulkanTestPassed = appStateManager.vulkanTestPassed.collectAsStateWithLifecycle().value,
            onDismiss = { appStateManager.dismissVulkanTestResult() }
        )
    }
}
