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

    // REALTIME STATE
    var voiceLanguage by remember { mutableStateOf(settingsManager.getVoiceLanguage()) }
    var voiceProcessor by remember { mutableStateOf(settingsManager.getVoiceProcessor()) }
    var selectedWhisperId by remember { mutableStateOf(settingsManager.getSelectedWhisperModelId()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var isServiceRunning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isServiceRunning = context.getSystemService(Context.ACTIVITY_SERVICE)
                ?.let { it as ActivityManager }
                ?.getRunningServices(Integer.MAX_VALUE)
                ?.any { it.service.className == "com.voxcommander.app.service.WakeWordService" } ?: false
            delay(1000)
        }
    }

    var voskGroups by remember { mutableStateOf<List<VoskLanguageGroup>>(emptyList()) }
    var isVoskLoading by remember { mutableStateOf(false) }
    var isVoskOffline by remember { mutableStateOf(false) }
    var voskError by remember { mutableStateOf<String?>(null) }
    var selectedVoskModel by remember { mutableStateOf<VoskModelInfo?>(null) }

    suspend fun loadVoskModels(force: Boolean = false) {
        isVoskLoading = true
        val result = VoskModelRegistry.getModels(force)
        voskGroups = result.groups
        isVoskOffline = !result.isOnline
        voskError = result.errorMessage
        isVoskLoading = false
        
        val savedModelName = settingsManager.getSelectedVoskModelName()
        val allModels = result.groups.flatMap { it.models }
        val previouslySelected = allModels.find { it.name == savedModelName }
        if (previouslySelected != null) selectedVoskModel = previouslySelected
    }

    LaunchedEffect(Unit) { loadVoskModels(force = true) }

    var downloadingItemState by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(downloadProgress) {
        if (downloadProgress == null || downloadProgress >= 1.0f) {
            if (downloadingItemState != null) {
                refreshTrigger++
                onRefreshMain()
            }
            downloadingItemState = null
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
                            0 -> GeneralSettingsTab_Realtime(languageManager = languageManager, settingsManager = settingsManager)
                            1 -> ModelsSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                voiceProcessor = voiceProcessor,
                                onProcessorSelected = {
                                    voiceProcessor = it
                                    appStateManager.setVoiceProcessor(it)
                                    updateVoiceEngine(); onRefreshMain(); refreshTrigger++
                                },
                                hasApiKey = settingsManager.getApiKey() != null,
                                googleSttAvailable = googleSttAvailable,
                                voiceLanguage = voiceLanguage,
                                onVoiceLanguageSelected = {
                                    voiceLanguage = it
                                    appStateManager.setVoiceLanguage(it)
                                    updateVoiceEngine(); onRefreshMain(); refreshTrigger++
                                },
                                whisperModels = WhisperModelRegistry.models,
                                selectedWhisperModel = WhisperModelRegistry.models.find { it.id == selectedWhisperId } ?: WhisperModelRegistry.models.firstOrNull(),
                                onWhisperModelSelected = { model, isDownloaded ->
                                    selectedWhisperId = model.id
                                    appStateManager.setSelectedWhisperModelId(model.id)
                                    if (!isDownloaded) {
                                        downloadingItemState = model
                                        onDownloadWhisperModel(model.id, model.url)
                                    }
                                    updateVoiceEngine(); onRefreshMain(); refreshTrigger++
                                },
                                onSelectCustomWhisperModel = onSelectCustomWhisperModel,
                                voskGroups = voskGroups,
                                selectedVoskModel = selectedVoskModel,
                                isVoskLoading = isVoskLoading,
                                isVoskOffline = isVoskOffline,
                                isOffline = isVoskOffline,
                                voskError = voskError,
                                onRetryConnection = { loadVoskModels(true) },
                                onVoskModelSelected = { model, isDownloaded, langCode ->
                                    voiceLanguage = langCode
                                    appStateManager.setVoiceLanguage(langCode)
                                    selectedVoskModel = model
                                    appStateManager.setSelectedVoskModelName(model.name)
                                    if (!isDownloaded) {
                                        downloadingItemState = model
                                        onDownloadVoskModel(langCode, model.url, model.name)
                                    }
                                    updateVoiceEngine(); onRefreshMain(); refreshTrigger++
                                },
                                onSelectCustomVoskModel = { onSelectCustomVoskModel(voiceLanguage) },
                                onDownloadWhisperModel = { id, url -> 
                                    downloadingItemState = WhisperModelRegistry.getModelById(id)
                                    onDownloadWhisperModel(id, url) 
                                },
                                onDownloadVoskModel = { code, url, name -> 
                                    downloadingItemState = voskGroups.flatMap { it.models }.find { it.name == name }
                                    onDownloadVoskModel(code, url, name) 
                                },
                                onDownloadLlamaModel = { model ->
                                    downloadingItemState = model
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
                                onFallbackChanged = { refreshTrigger++ },
                                refreshTrigger = refreshTrigger
                            )
                            2 -> ServiceSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                wakeWordEnabled = settingsManager.isWakeWordEnabled(),
                                onWakeWordEnabledChange = {
                                    settingsManager.saveWakeWordEnabled(it)
                                    if (!it && isServiceRunning) WakeWordService.stopService(context)
                                    refreshTrigger++
                                },
                                wakeWord = settingsManager.getWakeWord(),
                                onWakeWordChange = { settingsManager.saveWakeWord(it); refreshTrigger++ },
                                voskGroups = voskGroups,
                                selectedWakeWordModel = voskGroups.flatMap { it.models }.find { it.name == settingsManager.getWakeWordModelPath() },
                                onWakeWordModelSelected = { model ->
                                    settingsManager.saveWakeWordModelPath(model.name)
                                    if (!settingsManager.isModelDownloaded(model.name)) {
                                        downloadingItemState = model
                                        onDownloadVoskModel("en", model.url, model.name)
                                    }
                                    refreshTrigger++
                                },
                                isServiceRunning = isServiceRunning,
                                onStartService = { WakeWordService.startService(context) },
                                onStopService = { WakeWordService.stopService(context) },
                                downloadedColor = downloadedColor,
                                onDownloadRequest = { model -> downloadingItemState = model; onDownloadVoskModel("en", model.url, model.name) },
                                onDeleteRequest = { model -> modelToDelete = model as? AppModel; showDeleteConfirmDialog = true },
                                onCancelDownload = onCancelDownload,
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                voiceLanguage = voiceLanguage,
                                voiceProcessor = voiceProcessor,
                                refreshTrigger = refreshTrigger
                            )
                            4 -> AdvancedSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                onCleanupRequest = { showCleanupDialog = true },
                                onClearDefaultFallback = { 
                                    settingsManager.clearDefaultOfflineFallback()
                                    refreshTrigger++ 
                                },
                                onVerboseLoggingChange = { 
                                    settingsManager.saveVerboseLoggingEnabled(it)
                                    refreshTrigger++
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
        onConfirmCleanup = { onDeleteUnusedModels(); showCleanupDialog = false; refreshTrigger++ },
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
                onRefreshMain(); refreshTrigger++
            }
            showDeleteConfirmDialog = false
            modelToDelete = null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsTab_Realtime(languageManager: LanguageManager, settingsManager: SettingsManager) {
    var apiKey by remember { mutableStateOf(settingsManager.getApiKey() ?: "") }
    val languages = languageManager.getAvailableLanguages()
    val appLanguage = settingsManager.getLanguage()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = languageManager.getString("app_settings_section"), style = MaterialTheme.typography.titleMedium)
        TextField(value = apiKey, onValueChange = { apiKey = it; settingsManager.saveApiKey(it) }, label = { Text(languageManager.getString("api_key")) }, modifier = Modifier.fillMaxWidth())
        Text(text = languageManager.getString("language"), style = MaterialTheme.typography.labelLarge)
        Box {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(appLanguage.uppercase()) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.uppercase()) }, onClick = { settingsManager.saveLanguage(lang); languageManager.loadLanguage(lang); expanded = false })
                }
            }
        }
    }
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
