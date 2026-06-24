package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.domain.engine.whisper.WhisperModelInfo
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager

object ModelsSettingsTabConfig {
    const val SHOW_SAVE_BUTTON = true
    var isModelOnDevice: Boolean = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    onProcessorSelected: (String) -> Unit,
    hasApiKey: Boolean,
    googleSttAvailable: Boolean,
    onVoiceLanguageSelected: (String) -> Unit,
    whisperModels: List<WhisperModelInfo>,
    onWhisperModelSelected: (WhisperModelInfo, Boolean) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    voskGroups: List<VoskLanguageGroup>,
    selectedVoskModel: VoskModelInfo?,
    isVoskLoading: Boolean,
    isOffline: Boolean,
    voskError: String?,
    onRetryConnection: suspend () -> Unit,
    onVoskModelSelected: (VoskModelInfo, Boolean, String) -> Unit,
    onSelectCustomVoskModel: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    downloadedColor: Color,
    onCancelDownload: () -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onDownloadVoskModel: (String, String, String) -> Unit,
    onDownloadLlamaModel: (AppModel) -> Unit,
    onDeleteLlamaModel: (AppModel) -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab])
                    )
                }
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Voice Engines", style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Intent Engines", style = MaterialTheme.typography.labelLarge) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSubTab == 0) {
            VoiceEnginesSubTab(
                languageManager = languageManager,
                settingsManager = settingsManager,
                appStateManager = appStateManager,
                onProcessorSelected = onProcessorSelected,
                hasApiKey = hasApiKey,
                googleSttAvailable = googleSttAvailable,
                onVoiceLanguageSelected = onVoiceLanguageSelected,
                whisperModels = whisperModels,
                onWhisperModelSelected = onWhisperModelSelected,
                onSelectCustomWhisperModel = onSelectCustomWhisperModel,
                voskGroups = voskGroups,
                selectedVoskModel = selectedVoskModel,
                isVoskLoading = isVoskLoading,
                isOffline = isOffline,
                voskError = voskError,
                onRetryConnection = onRetryConnection,
                onVoskModelSelected = onVoskModelSelected,
                onSelectCustomVoskModel = onSelectCustomVoskModel,
                onDownloadWhisperModel = onDownloadWhisperModel,
                onDownloadVoskModel = onDownloadVoskModel,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem as? AppModel,
                downloadedColor = downloadedColor,
                onCancelDownload = onCancelDownload,
                onDeleteRequest = onDeleteRequest,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        } else {
            IntentEnginesSubTab(
                languageManager = languageManager, 
                settingsManager = settingsManager, 
                appStateManager = appStateManager,
                onDownloadLlamaModel = onDownloadLlamaModel,
                onDeleteLlamaModel = onDeleteLlamaModel,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem as? AppModel,
                onCancelDownload = onCancelDownload,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        }
    }
}
