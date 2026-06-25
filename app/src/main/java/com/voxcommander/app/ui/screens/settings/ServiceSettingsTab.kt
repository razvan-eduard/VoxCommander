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
import com.voxcommander.app.domain.model.AppModel
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
    voskModels: List<AppModel>,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    downloadedColor: Color,
    onDownloadRequest: (AppModel) -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    refreshTrigger: Int = 0,
    isVoskMultilingual: Boolean = false,
    availableVoskLanguages: List<String> = emptyList()
) {
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter Vosk models by selected language if not multilingual
    val filteredVoskModels = remember(voskModels, uiState.voiceLanguage, isVoskMultilingual) {
        if (isVoskMultilingual) {
            voskModels
        } else {
            voskModels.filter { (it as? com.voxcommander.app.data.remote.RemoteModelItem)?.lang_code == uiState.voiceLanguage }
        }
    }

    // Get selected wake word model from uiState, default to first downloaded if none selected
    val selectedWakeWordModel = remember(filteredVoskModels, uiState.wakeWordModelPath, refreshTrigger) {
        val path = uiState.wakeWordModelPath
        if (path != null) {
            filteredVoskModels.find { it.id == path }
        } else {
            // Auto-select first downloaded model
            filteredVoskModels.firstOrNull { model ->
                settingsManager.isModelDownloaded(model.id)
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
        // Voice Language Selection (only for non-multilingual Vosk)
        if (!isVoskMultilingual) {
            val languages = availableVoskLanguages.map { lang ->
                lang to lang.uppercase()
            }

            var showLanguageSheet by remember { mutableStateOf(false) }
            val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            val languageGroups = listOf(DropdownGroup("AVAILABLE LANGUAGES", languages))
            val selectedLangPair = languages.find { it.first == uiState.voiceLanguage }

            Text(text = languageManager.getString("voice_language"), style = MaterialTheme.typography.labelLarge)

            GroupedDropdownMenu(
                selectedItem = selectedLangPair,
                groups = languageGroups,
                itemLabel = { it.second },
                isDownloaded = { true },
                onDeviceLabel = "",
                onItemSelected = { pair, _ -> appStateManager.setVoiceLanguage(pair.first) },
                onExpandedChange = { showLanguageSheet = it },
                languageManager = languageManager
            )

            if (showLanguageSheet) {
                ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, sheetState = languageSheetState) {
                    GroupedDropdownContent(
                        title = languageManager.getString("voice_language"),
                        groups = languageGroups,
                        itemLabel = { it.second },
                        isDownloaded = { true },
                        onDeviceLabel = "",
                        onItemSelected = { pair, _ -> appStateManager.setVoiceLanguage(pair.first); showLanguageSheet = false },
                        languageManager = languageManager
                    )
                }
            }

            HorizontalDivider()
        }

        // Wake Word Text Field
        val isWakeWordModelOnDevice = remember(selectedWakeWordModel, refreshTrigger) {
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.id)
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

        val groupedModels = remember(filteredVoskModels, uiState) {
            if (filteredVoskModels.isNotEmpty()) {
                listOf(DropdownGroup("AVAILABLE MODELS", filteredVoskModels))
            } else emptyList()
        }

        GroupedDropdownMenu(
            selectedItem = selectedWakeWordModel,
            groups = groupedModels,
            itemLabel = { it.label + " (" + it.sizeDescription + ")" },
            isDownloaded = { model -> remember(model.id, uiState, refreshTrigger) { settingsManager.isModelDownloaded(model.id) } },
            onDeviceLabel = languageManager.getString("on_device_label"),
            onItemSelected = { model, isDownloaded ->
                appStateManager.setWakeWordModelPath(model.id)
            },
            onExpandedChange = { showModelSheet = it },
            onDownloadRequest = onDownloadRequest,
            onDeleteRequest = onDeleteRequest,
            onCancelDownload = onCancelDownload,
            downloadProgress = downloadProgress,
            downloadingItem = downloadingItem as? AppModel,
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
                    itemLabel = { it.label + " (" + it.sizeDescription + ")" },
                    isDownloaded = { model -> remember(model.id, uiState, refreshTrigger) { settingsManager.isModelDownloaded(model.id) } },
                    onDeviceLabel = languageManager.getString("on_device_label"),
                    onItemSelected = { model, isDownloaded ->
                        appStateManager.setWakeWordModelPath(model.id)
                        showModelSheet = false
                    },
                    onDownloadRequest = onDownloadRequest,
                    onDeleteRequest = onDeleteRequest,
                    onCancelDownload = onCancelDownload,
                    downloadProgress = downloadProgress,
                    downloadingItem = downloadingItem as? AppModel,
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
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.id)
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
