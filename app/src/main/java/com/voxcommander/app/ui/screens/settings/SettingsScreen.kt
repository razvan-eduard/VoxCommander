package com.voxcommander.app.ui.screens.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.domain.engine.vosk.VoskModelRegistry
import com.voxcommander.app.service.WakeWordService
import com.voxcommander.app.domain.engine.whisper.WhisperModelRegistry
import com.voxcommander.app.domain.engine.whisper.WhisperModelInfo
import com.voxcommander.app.domain.intent.interpreter.LlamaModelInfo
import com.voxcommander.app.domain.intent.interpreter.LlamaModelRegistry
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsContent(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    modelManagementViewModel: com.voxcommander.app.ui.viewmodels.ModelManagementViewModel,
    onSaveAndClose: () -> Unit,
    onDownloadVoskModel: (String, String, String) -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onSelectCustomVoskModel: (String) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    onDeleteUnusedModels: () -> Unit,
    onDownloadLlamaModel: (LlamaModelInfo) -> Unit,
    onDeleteLlamaModel: (AppModel) -> Unit,
    onCancelDownload: () -> Unit = {},
    onRefreshMain: () -> Unit = {},
    downloadProgress: Float? = null,
    selectionSuccessMessage: String? = null,
    googleSttAvailable: Boolean = true,
    updateVoiceEngine: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 6 })

    // REALTIME STATE - observe AppStateManager flows for reactive updates
    val voiceLanguage by appStateManager.voiceLanguage.collectAsState()
    val voiceProcessor by appStateManager.voiceProcessor.collectAsState()
    val selectedWhisperId by appStateManager.selectedWhisperModelId.collectAsState()
    val refreshTrigger by appStateManager.refreshTrigger.collectAsState()

    // Observe service status reactively from AppStateManager
    val isServiceRunning by appStateManager.isWakeWordServiceListening.collectAsState()

    // Observe Vosk model state from ViewModel
    val voskGroups by modelManagementViewModel.voskGroups.collectAsState()
    val isVoskLoading by modelManagementViewModel.isVoskLoading.collectAsState()
    val isVoskOffline by modelManagementViewModel.isVoskOffline.collectAsState()
    val voskError by modelManagementViewModel.voskError.collectAsState()
    
    var selectedVoskModel by remember { mutableStateOf<VoskModelInfo?>(null) }
    
    // Auto-update selectedVoskModel when groups or preferences change
    LaunchedEffect(voskGroups, refreshTrigger) {
        val savedModelName = settingsManager.getSelectedVoskModelName()
        selectedVoskModel = voskGroups.flatMap { it.models }.find { it.name == savedModelName }
    }

    var downloadingItemState by remember { mutableStateOf<AppModel?>(null) }
    val vmDownloadingItem by modelManagementViewModel.downloadingItem.collectAsState()
    
    LaunchedEffect(downloadProgress, vmDownloadingItem) {
        if (downloadProgress == null || downloadProgress >= 1.0f) {
            if (downloadingItemState != null) {
                onRefreshMain()
            }
            downloadingItemState = null
        } else {
            downloadingItemState = vmDownloadingItem
        }
    }

    var modelToDelete by remember { mutableStateOf<AppModel?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    val downloadedColor = Color(0xFF2E7D32)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Text(
                    text = languageManager.getString("settings"),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {}, 
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage])
                        )
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    val tabs = listOf("tab_general", "tab_models", "tab_service", "tab_benchmark", "tab_advanced", "tab_verbose_logging")
                    tabs.forEachIndexed { index, tabKey ->
                        val selected = pagerState.currentPage == index
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(text = languageManager.getString(tabKey), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.padding(horizontal = 4.dp).background(color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(24.dp))
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                if (page == 3) {
                    BenchmarkSettingsTab(languageManager = languageManager, appStateManager = appStateManager)
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (page) {
                            0 -> GeneralSettingsTab(languageManager = languageManager, settingsManager = settingsManager, appStateManager = appStateManager)
                            1 -> ModelsSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                voiceProcessor = voiceProcessor,
                                onProcessorSelected = {
                                    appStateManager.setVoiceProcessor(it)
                                    updateVoiceEngine(); onRefreshMain()
                                },
                                hasApiKey = settingsManager.getApiKey() != null,
                                googleSttAvailable = googleSttAvailable,
                                voiceLanguage = voiceLanguage,
                                onVoiceLanguageSelected = {
                                    appStateManager.setVoiceLanguage(it)
                                    updateVoiceEngine(); onRefreshMain()
                                },
                                whisperModels = WhisperModelRegistry.models,
                                selectedWhisperModel = WhisperModelRegistry.models.find { it.id == selectedWhisperId } ?: WhisperModelRegistry.models.firstOrNull(),
                                onWhisperModelSelected = { model, isDownloaded ->
                                    appStateManager.setSelectedWhisperModelId(model.id)
                                    if (!isDownloaded) {
                                        onDownloadWhisperModel(model.id, model.url)
                                    }
                                    updateVoiceEngine(); onRefreshMain()
                                },
                                onSelectCustomWhisperModel = onSelectCustomWhisperModel,
                                voskGroups = voskGroups,
                                selectedVoskModel = selectedVoskModel,
                                isVoskLoading = isVoskLoading,
                                isVoskOffline = isVoskOffline,
                                isOffline = isVoskOffline,
                                voskError = voskError,
                                onRetryConnection = { modelManagementViewModel.loadVoskModels(true) },
                                onVoskModelSelected = { model, isDownloaded, langCode ->
                                    appStateManager.setVoiceLanguage(langCode)
                                    selectedVoskModel = model
                                    appStateManager.setSelectedVoskModelName(model.name)
                                    if (!isDownloaded) {
                                        onDownloadVoskModel(langCode, model.url, model.name)
                                    }
                                    updateVoiceEngine(); onRefreshMain()
                                },
                                onSelectCustomVoskModel = { onSelectCustomVoskModel(voiceLanguage) },
                                onDownloadWhisperModel = { id, url -> 
                                    onDownloadWhisperModel(id, url) 
                                },
                                onDownloadVoskModel = { code, url, name -> 
                                    onDownloadVoskModel(code, url, name) 
                                },
                                onDownloadLlamaModel = { model ->
                                    onDownloadLlamaModel(model as LlamaModelInfo)
                                },
                                onDeleteLlamaModel = { model ->
                                    modelToDelete = model
                                    showDeleteConfirmDialog = true
                                },
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                downloadedColor = downloadedColor,
                                onCancelDownload = onCancelDownload,
                                onDeleteRequest = { model ->
                                    modelToDelete = model
                                    showDeleteConfirmDialog = true
                                },
                                onFallbackChanged = { appStateManager.refreshAll() },
                                refreshTrigger = refreshTrigger.toInt()
                            )
                            2 -> ServiceSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                wakeWordEnabled = settingsManager.isWakeWordEnabled(),
                                onWakeWordEnabledChange = {
                                    settingsManager.saveWakeWordEnabled(it)
                                    if (!it && isServiceRunning) WakeWordService.stopService(context)
                                    appStateManager.refreshAll()
                                },
                                wakeWord = settingsManager.getWakeWord(),
                                onWakeWordChange = { settingsManager.saveWakeWord(it); appStateManager.refreshAll() },
                                voskGroups = voskGroups,
                                selectedWakeWordModel = voskGroups.flatMap { it.models }.find { it.name == settingsManager.getWakeWordModelPath() },
                                onWakeWordModelSelected = { model ->
                                    settingsManager.saveWakeWordModelPath(model.name)
                                    if (!settingsManager.isModelDownloaded(model.name)) {
                                        onDownloadVoskModel("en", model.url, model.name)
                                    }
                                    appStateManager.refreshAll()
                                },
                                isServiceRunning = isServiceRunning,
                                onStartService = { WakeWordService.startService(context) },
                                onStopService = { WakeWordService.stopService(context) },
                                downloadedColor = downloadedColor,
                                onDownloadRequest = { model -> onDownloadVoskModel("en", model.url, model.name) },
                                onDeleteRequest = { model -> modelToDelete = model as? AppModel; showDeleteConfirmDialog = true },
                                onCancelDownload = onCancelDownload,
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                voiceLanguage = voiceLanguage,
                                voiceProcessor = voiceProcessor,
                                refreshTrigger = refreshTrigger.toInt()
                            )
                            4 -> AdvancedSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                onCleanupRequest = { showCleanupDialog = true },
                                onClearDefaultFallback = { 
                                    settingsManager.clearDefaultOfflineFallback()
                                    appStateManager.refreshAll() 
                                },
                                onVerboseLoggingChange = { 
                                    settingsManager.saveVerboseLoggingEnabled(it)
                                    appStateManager.refreshAll()
                                }
                            )
                            5 -> VerboseLoggingTab(languageManager, settingsManager.isVerboseLoggingEnabled())
                        }
                    }
                }
            }
        }
    }

    SettingsDialogs(
        languageManager = languageManager,
        showCleanupDialog = showCleanupDialog,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        modelToDelete = modelToDelete,
        onDismissCleanup = { showCleanupDialog = false },
        onConfirmCleanup = { onDeleteUnusedModels(); showCleanupDialog = false; appStateManager.refreshAll() },
        onDismissDelete = { showDeleteConfirmDialog = false; modelToDelete = null },
        onConfirmDelete = {
            modelToDelete?.let { m ->
                val isIntent = m.engineType == "Llama"
                if (isIntent) {
                    if (m.id == settingsManager.getDefaultIntentFallbackModel()) settingsManager.clearDefaultIntentFallback()
                } else {
                    if (m.id == settingsManager.getDefaultVoiceFallbackModel()) settingsManager.clearDefaultVoiceFallback()
                }
                settingsManager.setModelDownloaded(m.id, false)
                appStateManager.refreshAll()
                onRefreshMain()
            }
            showDeleteConfirmDialog = false
            modelToDelete = null
        }
    )
}

@Composable
private fun SettingsDialogs(
    languageManager: LanguageManager,
    showCleanupDialog: Boolean,
    showDeleteConfirmDialog: Boolean,
    modelToDelete: AppModel?,
    onDismissCleanup: () -> Unit,
    onConfirmCleanup: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = onDismissCleanup,
            title = { Text(languageManager.getString("confirm_delete_title")) },
            text = { Text(languageManager.getString("confirm_delete_msg")) },
            confirmButton = { TextButton(onClick = onConfirmCleanup, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(languageManager.getString("delete_button")) } },
            dismissButton = { TextButton(onClick = onDismissCleanup) { Text(languageManager.getString("cancel_button")) } }
        )
    }

    if (showDeleteConfirmDialog && modelToDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(languageManager.getString("confirm_delete_title")) },
            text = { Text(languageManager.getString("confirm_delete_msg").format(modelToDelete.engineType, "${modelToDelete.label} (${modelToDelete.sizeDescription})")) },
            confirmButton = { TextButton(onClick = onConfirmDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(languageManager.getString("delete_button")) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(languageManager.getString("cancel_button")) } }
        )
    }
}
