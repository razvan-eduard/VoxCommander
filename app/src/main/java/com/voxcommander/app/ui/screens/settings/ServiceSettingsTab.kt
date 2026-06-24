package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.ui.components.VoiceInputTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    voskGroups: List<VoskLanguageGroup>,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    downloadedColor: Color,
    onDownloadRequest: (VoskModelInfo) -> Unit,
    onDeleteRequest: (VoskModelInfo) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    refreshTrigger: Int = 0
) {
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Get selected wake word model from uiState, default to first downloaded if none selected
    val selectedWakeWordModel = remember(voskGroups, uiState.wakeWordModelPath, refreshTrigger) {
        val path = uiState.wakeWordModelPath
        if (path != null) {
            voskGroups.flatMap { it.models }.find { it.name == path }
        } else {
            // Auto-select first downloaded model
            voskGroups.flatMap { it.models }.firstOrNull { model ->
                settingsManager.isModelDownloaded(model.name)
            }
        }
    }

    Text(text = languageManager.getString("service_settings_section"), style = MaterialTheme.typography.titleMedium)

    // Wake Word Enable Switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageManager.getString("wake_word_enabled"))
        Switch(
            checked = uiState.wakeWordEnabled,
            onCheckedChange = { enabled ->
                appStateManager.setWakeWordEnabled(enabled)
                if (!enabled && uiState.isWakeWordServiceListening) {
                    onStopService()
                }
            }
        )
    }

    if (uiState.wakeWordEnabled) {
        // Wake Word Text Field
        val isWakeWordModelOnDevice = remember(selectedWakeWordModel, refreshTrigger) {
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.name)
        }

        VoiceInputTextField(
            value = uiState.wakeWord,
            onValueChange = { appStateManager.setWakeWord(it) },
            label = { Text(languageManager.getString("wake_word_label")) },
            placeholder = { Text(languageManager.getString("wake_word_hint")) },
            languageManager = languageManager,
            voiceLanguage = uiState.voiceLanguage,
            voiceProcessor = uiState.voiceProcessor,
            isModelOnDevice = isWakeWordModelOnDevice
        )

        // Wake Word Model Selection
        Text(text = languageManager.getString("wake_word_model"), style = MaterialTheme.typography.labelLarge)

        val groupedModels = remember(voskGroups, uiState) {
            voskGroups.map { group ->
                DropdownGroup(
                    header = group.language.uppercase(),
                    items = group.models
                )
            }
        }

        GroupedDropdownMenu(
            selectedItem = selectedWakeWordModel,
            groups = groupedModels,
            itemLabel = { if (it.size != null) "${it.name} (${it.size})" else it.name },
            isDownloaded = { model -> remember(model.name, uiState, refreshTrigger) { settingsManager.isModelDownloaded(model.name) } },
            onDeviceLabel = languageManager.getString("on_device_label"),
            onItemSelected = { model, isDownloaded ->
                appStateManager.setWakeWordModelPath(model.name)
            },
            onExpandedChange = { showModelSheet = it },
            onDownloadRequest = onDownloadRequest,
            onDeleteRequest = onDeleteRequest,
            onCancelDownload = onCancelDownload,
            downloadProgress = downloadProgress,
            downloadingItem = downloadingItem,
            languageManager = languageManager
        )

        // ModalBottomSheet for model selection
        if (showModelSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModelSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                GroupedDropdownContent(
                    title = languageManager.getString("wake_word_model"),
                    groups = groupedModels,
                    itemLabel = { if (it.size != null) "${it.name} (${it.size})" else it.name },
                    isDownloaded = { model -> remember(model.name, uiState, refreshTrigger) { settingsManager.isModelDownloaded(model.name) } },
                    onDeviceLabel = languageManager.getString("on_device_label"),
                    onItemSelected = { model, isDownloaded ->
                        appStateManager.setWakeWordModelPath(model.name)
                        showModelSheet = false
                    },
                    onDownloadRequest = onDownloadRequest,
                    onDeleteRequest = onDeleteRequest,
                    onCancelDownload = onCancelDownload,
                    downloadProgress = downloadProgress,
                    downloadingItem = downloadingItem,
                    languageManager = languageManager
                )
            }
        }

        // Service Status
        Text(text = languageManager.getString("service_status"), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (uiState.isWakeWordServiceListening) languageManager.getString("service_running") else languageManager.getString("service_stopped"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.isWakeWordServiceListening) downloadedColor else MaterialTheme.colorScheme.secondary
        )

        // Check if selected model is on device
        val isModelOnDevice = remember(selectedWakeWordModel, uiState, refreshTrigger) {
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.name)
        }

        // Start/Stop Service Button
        Button(
            onClick = if (uiState.isWakeWordServiceListening) onStopService else onStartService,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isWakeWordServiceListening || isModelOnDevice,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isWakeWordServiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (uiState.isWakeWordServiceListening) languageManager.getString("stop_service") else languageManager.getString("start_service"))
        }

        // Show warning if model not on device
        if (!isModelOnDevice && !uiState.isWakeWordServiceListening) {
            Text(
                text = "Selected model not on device. Please download the model first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
