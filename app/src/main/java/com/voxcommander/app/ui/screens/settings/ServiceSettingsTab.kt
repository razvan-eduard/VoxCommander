package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.ui.components.VoiceInputTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    wakeWordEnabled: Boolean,
    onWakeWordEnabledChange: (Boolean) -> Unit,
    wakeWord: String,
    onWakeWordChange: (String) -> Unit,
    voskGroups: List<VoskLanguageGroup>,
    selectedWakeWordModel: VoskModelInfo?,
    onWakeWordModelSelected: (VoskModelInfo) -> Unit,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    downloadedColor: Color,
    onDownloadRequest: (VoskModelInfo) -> Unit,
    onDeleteRequest: (VoskModelInfo) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    voiceLanguage: String,
    voiceProcessor: String,
    refreshTrigger: Int = 0 
) {
    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Text(text = languageManager.getString("service_settings_section"), style = MaterialTheme.typography.titleMedium)

    // Wake Word Enable Switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageManager.getString("wake_word_enabled"))
        Switch(
            checked = wakeWordEnabled,
            onCheckedChange = onWakeWordEnabledChange
        )
    }

    if (wakeWordEnabled) {
        // Wake Word Text Field
        val isWakeWordModelOnDevice = remember(selectedWakeWordModel, refreshTrigger) {
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.name)
        }

        VoiceInputTextField(
            value = wakeWord,
            onValueChange = onWakeWordChange,
            label = { Text(languageManager.getString("wake_word_label")) },
            placeholder = { Text(languageManager.getString("wake_word_hint")) },
            languageManager = languageManager,
            voiceLanguage = voiceLanguage,
            voiceProcessor = voiceProcessor,
            isModelOnDevice = isWakeWordModelOnDevice
        )

        // Wake Word Model Selection
        Text(text = languageManager.getString("wake_word_model"), style = MaterialTheme.typography.labelLarge)

        val groupedModels = remember(voskGroups) {
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
            // REFRESH: Re-evaluate downloaded state when refreshTrigger changes
            isDownloaded = { model -> settingsManager.isModelDownloaded(model.name) },
            onDeviceLabel = languageManager.getString("on_device_label"),
            onItemSelected = { model, isDownloaded -> onWakeWordModelSelected(model) },
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
                    // REFRESH: Re-evaluate downloaded state when refreshTrigger changes
                    isDownloaded = { model -> settingsManager.isModelDownloaded(model.name) },
                    onDeviceLabel = languageManager.getString("on_device_label"),
                    onItemSelected = { model, isDownloaded ->
                        onWakeWordModelSelected(model)
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
            text = if (isServiceRunning) languageManager.getString("service_running") else languageManager.getString("service_stopped"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServiceRunning) downloadedColor else MaterialTheme.colorScheme.secondary
        )

        // Check if selected model is on device
        val isModelOnDevice = remember(selectedWakeWordModel, refreshTrigger) {
            selectedWakeWordModel != null && settingsManager.isModelDownloaded(selectedWakeWordModel.name)
        }

        // Start/Stop Service Button
        Button(
            onClick = if (isServiceRunning) onStopService else onStartService,
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceRunning || isModelOnDevice,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isServiceRunning) languageManager.getString("stop_service") else languageManager.getString("start_service"))
        }

        // Show warning if model not on device
        if (!isModelOnDevice && !isServiceRunning) {
            Text(
                text = "Selected model not on device. Please download the model first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
