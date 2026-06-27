package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSettingsTab(
    languageManager: LanguageManager,
    settingsRepo: SettingsRepository,
    appStateManager: AppStateManager,
    onProcessorSelected: (String) -> Unit,
    hasApiKey: Boolean,
    googleSttAvailable: Boolean,
    onVoiceLanguageSelected: (String) -> Unit,
    onModelSelected: (AppModel, Boolean, String) -> Unit,
    onDownloadModel: (String, String, String?) -> Unit,
    onDeleteModel: (String, String) -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: AppModel? = null,
    downloadedColor: Color,
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
                text = { Text(languageManager.getString("voice_engines"), style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(languageManager.getString("intent_engines"), style = MaterialTheme.typography.labelLarge) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSubTab == 0) {
            VoiceEnginesSubTab(
                languageManager = languageManager,
                settingsRepo = settingsRepo,
                appStateManager = appStateManager,
                onProcessorSelected = onProcessorSelected,
                hasApiKey = hasApiKey,
                googleSttAvailable = googleSttAvailable,
                onVoiceLanguageSelected = onVoiceLanguageSelected,
                onModelSelected = onModelSelected,
                onDownloadModel = onDownloadModel,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                downloadedColor = downloadedColor,
                onCancelDownload = onCancelDownload,
                onDeleteRequest = onDeleteRequest,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        } else {
            IntentEnginesSubTab(
                languageManager = languageManager,
                settingsRepo = settingsRepo,
                appStateManager = appStateManager,
                onDownloadModel = onDownloadModel,
                onDeleteModel = onDeleteModel,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                onCancelDownload = onCancelDownload,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        }
    }
}
