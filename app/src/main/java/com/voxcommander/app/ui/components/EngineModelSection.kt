package com.voxcommander.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.Strings

/**
 * Universal component for managing ANY engine model (Whisper, Vosk, Llama).
 * Handles: Dropdown selection, IMMEDIATE Download (No Popups), Delete confirmation, and Categorized Fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EngineModelSection(
    title: String,
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    groups: List<DropdownGroup<T>>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    modelIdProvider: (T) -> String,
    onItemSelected: (T, Boolean) -> Unit,
    onDownloadRequest: (T) -> Unit,
    onDeleteRequest: (T) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any?,
    currentProcessor: String,
    fallbackCategory: String = Strings.FallbackCategories.VOICE,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0,
    onShowInfo: (() -> Unit)? = null
) {
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 1. Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        if (onShowInfo != null) {
            IconButton(onClick = onShowInfo) {
                Icon(Icons.Outlined.Info, contentDescription = "Info")
            }
        }
    }

    // 2. Main Dropdown
    GroupedDropdownMenu(
        selectedItem = selectedItem,
        groups = groups,
        itemLabel = itemLabel,
        isDownloaded = { item ->
            remember(modelIdProvider(item), uiState, refreshTrigger) { settingsManager.isModelDownloaded(modelIdProvider(item)) }
        },
        onDeviceLabel = languageManager.getString("on_device_label"),
        onItemSelected = { item, isDownloaded ->
            onItemSelected(item, isDownloaded)
            if (!isDownloaded) {
                onDownloadRequest(item)
            }
        },
        onExpandedChange = { showSheet = it },
        onDownloadRequest = { item ->
            // Click on arrow from main button: trigger download, but don't force select/close
            onDownloadRequest(item) 
        },
        onDeleteRequest = { onDeleteRequest(it) },
        onCancelDownload = onCancelDownload,
        downloadProgress = downloadProgress,
        downloadingItem = downloadingItem,
        languageManager = languageManager
    )

    // 3. Selection Sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GroupedDropdownContent(
                title = title,
                groups = groups,
                itemLabel = itemLabel,
                isDownloaded = { item ->
                    remember(modelIdProvider(item), uiState, refreshTrigger) { settingsManager.isModelDownloaded(modelIdProvider(item)) }
                },
                onDeviceLabel = languageManager.getString("on_device_label"),
                onItemSelected = { item, isDownloaded ->
                    // Full row click: select and close
                    onItemSelected(item, isDownloaded)
                    if (!isDownloaded) {
                        onDownloadRequest(item)
                    }
                    showSheet = false
                },
                onDownloadRequest = { item ->
                    // Arrow button click: KEEP SHEET OPEN for multiple downloads
                    onDownloadRequest(item)
                },
                onDeleteRequest = { onDeleteRequest(it) },
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                languageManager = languageManager
            )
        }
    }

    // 4. Categorized Fallback Logic
    if (selectedItem != null) {
        val modelId = modelIdProvider(selectedItem)
        val isDownloaded = remember(modelId, uiState, refreshTrigger) { settingsManager.isModelDownloaded(modelId) }
        
        val defaultProcessor = if (fallbackCategory == Strings.FallbackCategories.VOICE) uiState.defaultVoiceFallbackProcessor else uiState.defaultIntentFallbackProcessor
        val defaultModelId = if (fallbackCategory == Strings.FallbackCategories.VOICE) uiState.defaultVoiceFallbackModel else uiState.defaultIntentFallbackModel
        
        val isDefault = defaultProcessor == currentProcessor && defaultModelId == modelId
        var showChangeDialog by remember { mutableStateOf(false) }

        Surface(
            onClick = {
                if (isDownloaded) {
                    if (!isDefault) {
                        if (defaultProcessor != null && defaultModelId != null && defaultModelId != modelId) {
                            showChangeDialog = true
                        } else {
                            if (fallbackCategory == Strings.FallbackCategories.VOICE) settingsManager.saveDefaultVoiceFallback(currentProcessor, modelId)
                            else settingsManager.saveDefaultIntentFallback(currentProcessor, modelId)
                            onFallbackChanged()
                        }
                    } else {
                        if (fallbackCategory == Strings.FallbackCategories.VOICE) settingsManager.clearDefaultVoiceFallback()
                        else settingsManager.clearDefaultIntentFallback()
                        onFallbackChanged()
                    }
                }
            },
            enabled = isDownloaded,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                width = if (isDefault) 2.dp else 1.dp,
                color = if (!isDownloaded) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        else if (isDefault) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.outline
            ),
            color = if (!isDownloaded) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    else if (isDefault) MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surface
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                Checkbox(checked = isDefault, onCheckedChange = null, enabled = isDownloaded)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = languageManager.getString("default_offline_fallback_model"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDownloaded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        if (showChangeDialog) {
            AlertDialog(
                onDismissRequest = { showChangeDialog = false },
                title = { Text(languageManager.getString("change_default_model_title")) },
                text = { Text(languageManager.getString("change_default_model_message").format(defaultModelId, modelId)) },
                confirmButton = {
                    Button(onClick = {
                        if (fallbackCategory == "voice") settingsManager.saveDefaultVoiceFallback(currentProcessor, modelId)
                        else settingsManager.saveDefaultIntentFallback(currentProcessor, modelId)
                        onFallbackChanged()
                        showChangeDialog = false
                    }) { Text(languageManager.getString("change")) }
                },
                dismissButton = { Button(onClick = { showChangeDialog = false }) { Text(languageManager.getString("cancel_button")) } }
            )
        }
    }
}
