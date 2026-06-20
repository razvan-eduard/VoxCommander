package com.voxcommander.app.ui.screens.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

    // --- 1. HOISTED REALTIME STATE ---
    var wakeWordEnabled by remember { mutableStateOf(settingsManager.isWakeWordEnabled()) }
    var verboseLoggingEnabled by remember { mutableStateOf(settingsManager.isVerboseLoggingEnabled()) }
    var wakeWordState by remember { mutableStateOf(settingsManager.getWakeWord()) }
    var voiceLanguage by remember { mutableStateOf(settingsManager.getVoiceLanguage()) }
    var voiceProcessor by remember { mutableStateOf(settingsManager.getVoiceProcessor()) }
    
    var selectedWhisperId by remember { mutableStateOf(settingsManager.getSelectedWhisperModelId()) }
    var selectedVoskModelName by remember { mutableStateOf("") } 

    // THE MASTER UI REFRESH TRIGGER
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Service status check (Live)
    var isServiceRunning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val running = context.getSystemService(Context.ACTIVITY_SERVICE)
                ?.let { it as ActivityManager }
                ?.getRunningServices(Integer.MAX_VALUE)
                ?.any { it.service.className == "com.voxcommander.app.service.WakeWordService" }
                ?: false
            isServiceRunning = running
            delay(1000)
        }
    }

    // Vosk loading
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
        
        if (previouslySelected != null) {
            selectedVoskModel = previouslySelected
            selectedVoskModelName = previouslySelected.name
        } else {
            val currentGroup = result.groups.find { it.language.contains(voiceLanguage, ignoreCase = true) }
            selectedVoskModel = currentGroup?.models?.firstOrNull() ?: result.groups.firstOrNull()?.models?.firstOrNull()
            selectedVoskModelName = selectedVoskModel?.name ?: ""
        }
    }

    LaunchedEffect(Unit) { loadVoskModels(force = true) }

    // Dialogs
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingVoskModel by remember { mutableStateOf<Pair<String, VoskModelInfo>?>(null) }
    var pendingWhisperModel by remember { mutableStateOf<WhisperModelInfo?>(null) }
    var pendingLlamaModel by remember { mutableStateOf<AppModel?>(null) }
    var pendingWakeWordModel by remember { mutableStateOf<VoskModelInfo?>(null) }
    var showCleanupDialog by remember { mutableStateOf(false) }
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

    // DELETE SAFEGUARD DIALOGS
    var modelToDelete by remember { mutableStateOf<Any?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingDefaultFallback by remember { mutableStateOf(false) }
    var isDeletingActiveWakeWord by remember { mutableStateOf(false) }

    val downloadedColor = Color(0xFF2E7D32)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 16.dp)) {
        // --- HEADER ---
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
                    indicator = { },
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
                            0 -> GeneralSettingsTab_Realtime(languageManager = languageManager, settingsManager = settingsManager, refreshTrigger = refreshTrigger)
                            1 -> ModelsSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                voiceProcessor = voiceProcessor,
                                onProcessorSelected = {
                                    voiceProcessor = it
                                    appStateManager.setVoiceProcessor(it)
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                },
                                hasApiKey = !(settingsManager.getApiKey().isNullOrBlank()),
                                googleSttAvailable = googleSttAvailable,
                                voiceLanguage = voiceLanguage,
                                onVoiceLanguageSelected = {
                                    voiceLanguage = it
                                    appStateManager.setVoiceLanguage(it)
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                },
                                whisperModels = WhisperModelRegistry.models,
                                selectedWhisperModel = WhisperModelRegistry.models.find { it.id == selectedWhisperId } ?: WhisperModelRegistry.models.firstOrNull(),
                                onWhisperModelSelected = { model, isDownloaded ->
                                    selectedWhisperId = model.id
                                    appStateManager.setSelectedWhisperModelId(model.id)
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                    if (!isDownloaded) {
                                        downloadingItemState = model
                                        pendingWhisperModel = model
                                        showDownloadDialog = true
                                    }
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
                                    selectedVoskModelName = model.name
                                    appStateManager.setSelectedVoskModelName(model.name)
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                    if (!isDownloaded) {
                                        downloadingItemState = model
                                        pendingVoskModel = langCode to model
                                        showDownloadDialog = true
                                    }
                                },
                                onSelectCustomVoskModel = { onSelectCustomVoskModel(voiceLanguage) },
                                onDownloadWhisperModel = { id, url -> onDownloadWhisperModel(id, url) },
                                onDownloadVoskModel = { code, url, name -> onDownloadVoskModel(code, url, name) },
                                onDownloadLlamaModel = { model ->
                                    // DIRECT BUTTON: Start download immediately
                                    downloadingItemState = model
                                    onDownloadLlamaModel(model as LlamaModelInfo)
                                },
                                onDeleteLlamaModel = { model ->
                                    // THIS IS THE BRIDGE FOR DIRECT BUTTONS (ALL ENGINES)
                                    val m = model as? AppModel ?: return@ModelsSettingsTab
                                    downloadingItemState = m
                                    when (m.engineType) {
                                        "Whisper" -> onDownloadWhisperModel(m.id, (m as WhisperModelInfo).url)
                                        "Vosk" -> onDownloadVoskModel("en", (m as VoskModelInfo).url, m.id)
                                        "Llama" -> onDownloadLlamaModel(m as LlamaModelInfo)
                                    }
                                },
                                onDeleteRequest = { model ->
                                    modelToDelete = model
                                    isDeletingDefaultFallback = ((model as? AppModel)?.id == settingsManager.getDefaultOfflineFallbackModel())
                                    showDeleteConfirmDialog = true
                                },
                                refreshTrigger = refreshTrigger,
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                downloadedColor = downloadedColor,
                                onCancelDownload = onCancelDownload,
                                onCleanupRequest = { showCleanupDialog = true },
                                onClearDefaultFallback = { settingsManager.clearDefaultOfflineFallback(); refreshTrigger++ }
                            )
                            2 -> ServiceSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                wakeWordEnabled = wakeWordEnabled,
                                onWakeWordEnabledChange = {
                                    wakeWordEnabled = it
                                    settingsManager.saveWakeWordEnabled(it)
                                    if (!it && isServiceRunning) WakeWordService.stopService(context)
                                    refreshTrigger++
                                },
                                wakeWord = wakeWordState,
                                onWakeWordChange = { wakeWordState = it; settingsManager.saveWakeWord(it); refreshTrigger++ },
                                voskGroups = voskGroups,
                                selectedWakeWordModel = voskGroups.flatMap { it.models }.find { it.name == settingsManager.getWakeWordModelPath() },
                                onWakeWordModelSelected = { model ->
                                    settingsManager.saveWakeWordModelPath(model.name)
                                    refreshTrigger++
                                    if (!settingsManager.isModelDownloaded(model.name)) {
                                        downloadingItemState = model
                                        pendingWakeWordModel = model
                                        showDownloadDialog = true
                                    }
                                },
                                isServiceRunning = isServiceRunning,
                                onStartService = { WakeWordService.startService(context) },
                                onStopService = { WakeWordService.stopService(context) },
                                downloadedColor = downloadedColor,
                                onDownloadRequest = { model -> downloadingItemState = model; pendingWakeWordModel = model; showDownloadDialog = true },
                                onDeleteRequest = { model -> modelToDelete = model; showDeleteConfirmDialog = true },
                                onCancelDownload = onCancelDownload,
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                voiceLanguage = voiceLanguage,
                                voiceProcessor = voiceProcessor,
                                refreshTrigger = refreshTrigger
                            )
                            4 -> AdvancedSettingsTab(languageManager, settingsManager, appStateManager) { verboseLoggingEnabled = it; refreshTrigger++ }
                            5 -> VerboseLoggingTab(languageManager, verboseLoggingEnabled)
                        }
                    }
                }
            }
        }
    }

    SettingsDialogs(
        languageManager = languageManager,
        showCleanupDialog = showCleanupDialog,
        showDownloadDialog = showDownloadDialog,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        isDeletingDefaultFallback = isDeletingDefaultFallback,
        isDeletingActiveWakeWord = isDeletingActiveWakeWord,
        modelToDelete = modelToDelete,
        pendingVoskModel = pendingVoskModel,
        pendingWhisperModel = pendingWhisperModel,
        pendingLlamaModel = pendingLlamaModel,
        pendingWakeWordModel = pendingWakeWordModel,
        onDismissCleanup = { showCleanupDialog = false },
        onConfirmCleanup = { onDeleteUnusedModels(); showCleanupDialog = false; refreshTrigger++ },
        onDismissDownload = { showDownloadDialog = false; downloadingItemState = null },
        onConfirmDownload = {
            pendingVoskModel?.let { (lang, model) -> onDownloadVoskModel(lang, model.url, model.name) }
            pendingWhisperModel?.let { model -> onDownloadWhisperModel(model.id, model.url) }
            pendingLlamaModel?.let { model -> onDownloadLlamaModel(model as LlamaModelInfo) }
            pendingWakeWordModel?.let { model -> onDownloadVoskModel("en", model.url, model.name) }
            showDownloadDialog = false
        },
        onDismissDelete = { showDeleteConfirmDialog = false; modelToDelete = null },
        onConfirmDelete = {
            modelToDelete?.let { model ->
                val m = model as? AppModel ?: return@let
                settingsManager.setModelDownloaded(m.id, false)
                onRefreshMain(); refreshTrigger++
            }
            showDeleteConfirmDialog = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsTab_Realtime(languageManager: LanguageManager, settingsManager: SettingsManager, refreshTrigger: Int = 0) {
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
    showDownloadDialog: Boolean,
    showDeleteConfirmDialog: Boolean,
    isDeletingDefaultFallback: Boolean,
    isDeletingActiveWakeWord: Boolean,
    modelToDelete: Any?,
    pendingVoskModel: Pair<String, VoskModelInfo>?,
    pendingWhisperModel: WhisperModelInfo?,
    pendingLlamaModel: AppModel?,
    pendingWakeWordModel: VoskModelInfo?,
    onDismissCleanup: () -> Unit,
    onConfirmCleanup: () -> Unit,
    onDismissDownload: () -> Unit,
    onConfirmDownload: () -> Unit,
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

    if (showDownloadDialog) {
        val model = pendingVoskModel?.second ?: pendingWhisperModel ?: pendingLlamaModel ?: pendingWakeWordModel
        AlertDialog(
            onDismissRequest = onDismissDownload,
            title = { Text(languageManager.getString("download_model_title")) },
            text = { model?.let { Text(languageManager.getString("download_model_msg").format(it.label, it.sizeDescription)) } },
            confirmButton = { TextButton(onClick = onConfirmDownload) { Text(languageManager.getString("download_button")) } },
            dismissButton = { TextButton(onClick = onDismissDownload) { Text(languageManager.getString("cancel_button")) } }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(languageManager.getString("confirm_delete_title")) },
            text = {
                val m = modelToDelete as? AppModel
                Column {
                    m?.let { Text(languageManager.getString("confirm_delete_msg").format(it.engineType, "${it.label} (${it.sizeDescription})")) }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(languageManager.getString("delete_button")) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(languageManager.getString("cancel_button")) } }
        )
    }
}
