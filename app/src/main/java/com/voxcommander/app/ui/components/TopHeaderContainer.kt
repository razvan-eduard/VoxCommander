package com.voxcommander.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.screens.rules.RulesManagerContent
import com.voxcommander.app.ui.screens.settings.SettingsContent

enum class TopHeaderMode {
    NONE, SETTINGS, RULES
}

/**
 * Unified FULLSCREEN container for all management overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderContainer(
    mode: TopHeaderMode,
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    fastMapDao: FastMapDao,
    onDismissRequest: () -> Unit,
    onDownloadVoskModel: (String, String, String) -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onSelectCustomVoskModel: (String) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    onDeleteUnusedModels: () -> Unit,
    onDownloadLlamaModel: (AppModel) -> Unit,
    onDeleteLlamaModel: (AppModel) -> Unit,
    onCancelDownload: () -> Unit,
    onRefreshMain: () -> Unit,
    downloadProgress: Float?,
    selectionSuccessMessage: String?,
    googleSttAvailable: Boolean,
    updateVoiceEngine: () -> Unit
) {
    if (mode == TopHeaderMode.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        modifier = Modifier.fillMaxSize()
    ) {
        key(mode) {
            when (mode) {
                TopHeaderMode.SETTINGS -> {
                    SettingsContent(
                        languageManager = languageManager,
                        settingsManager = settingsManager,
                        appStateManager = appStateManager,
                        onSaveAndClose = onDismissRequest,
                        onDownloadVoskModel = onDownloadVoskModel,
                        onDownloadWhisperModel = onDownloadWhisperModel,
                        onSelectCustomVoskModel = onSelectCustomVoskModel,
                        onSelectCustomWhisperModel = onSelectCustomWhisperModel,
                        onDeleteUnusedModels = onDeleteUnusedModels,
                        onDownloadLlamaModel = onDownloadLlamaModel,
                        onDeleteLlamaModel = onDeleteLlamaModel,
                        onCancelDownload = onCancelDownload,
                        onRefreshMain = onRefreshMain,
                        downloadProgress = downloadProgress,
                        selectionSuccessMessage = selectionSuccessMessage,
                        googleSttAvailable = googleSttAvailable,
                        updateVoiceEngine = updateVoiceEngine
                    )
                }
                TopHeaderMode.RULES -> {
                    RulesManagerContent(
                        languageManager = languageManager,
                        settingsManager = settingsManager,
                        fastMapDao = fastMapDao,
                        onSaveAndClose = onDismissRequest
                    )
                }
                else -> {}
            }
        }
    }
}
