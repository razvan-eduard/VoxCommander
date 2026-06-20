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
    onDownloadLlamaModel: (AppModel) -> Unit,
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
        
        // Fix persistence: Try to find the model matching the saved name first
        val savedModelName = settingsManager.getSelectedVoskModelName()
        val allModels = result.groups.flatMap { it.models }
        val previouslySelected = allModels.find { it.name == savedModelName }
        
        if (previouslySelected != null) {
            selectedVoskModel = previouslySelected
            selectedVoskModelName = previouslySelected.name
        } else {
            // Fallback to first model of the current language if none saved
            val currentGroup = result.groups.find { it.language.contains(voiceLanguage, ignoreCase = true) }
            selectedVoskModel = currentGroup?.models?.firstOrNull() ?: result.groups.firstOrNull()?.models?.firstOrNull()
            selectedVoskModelName = selectedVoskModel?.name ?: ""
        }
    }

    LaunchedEffect(Unit) {
        loadVoskModels(force = true)
    }

    // Dialogs
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingVoskModel by remember { mutableStateOf<Pair<String, VoskModelInfo>?>(null) }
    var pendingWhisperModel by remember { mutableStateOf<WhisperModelInfo?>(null) }
    var pendingLlamaModel by remember { mutableStateOf<AppModel?>(null) }
    var pendingWakeWordModel by remember { mutableStateOf<VoskModelInfo?>(null) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var downloadingItemState by remember { mutableStateOf<Any?>(null) }
    
    // Sync downloadingItem with incoming progress nullity
    LaunchedEffect(downloadProgress) {
        if (downloadProgress == null || downloadProgress >= 1.0f) {
            if (downloadingItemState != null) {
                // Download just finished!
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding() 
            .padding(bottom = 16.dp)
    ) {
        // --- HEADER WITH BORDER ---
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
                    val tabs = listOf(
                        "tab_general", "tab_models", "tab_service", "tab_benchmark", "tab_advanced", "tab_verbose_logging"
                    )
                    
                    tabs.forEachIndexed { index, tabKey ->
                        val selected = pagerState.currentPage == index
                        val interactionSource = remember { MutableInteractionSource() }
                        
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            interactionSource = interactionSource,
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            text = {
                                Text(
                                    text = languageManager.getString(tabKey),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            HorizontalPager(
                state = pagerState, 
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                if (page == 3) {
                    BenchmarkSettingsTab(
                        languageManager = languageManager,
                        appStateManager = appStateManager
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> GeneralSettingsTab_Realtime(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                refreshTrigger = refreshTrigger
                            )
                            1 -> ModelsSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                appStateManager = appStateManager,
                                voiceProcessor = voiceProcessor,
                                onProcessorSelected = {
                                    voiceProcessor = it
                                    appStateManager.setVoiceProcessor(it)
                                    when (it) {
                                        Strings.Processors.WHISPER_CPP,
                                        Strings.Processors.WHISPER_VULKAN,
                                        Strings.Processors.WHISPER_NEON -> {
                                            val modelId = appStateManager.selectedWhisperModelId.value
                                            appStateManager.setVoiceModelReady(settingsManager.isModelDownloaded(modelId) || appStateManager.customWhisperModelPath.value != null)
                                        }
                                        Strings.Processors.VOSK -> {
                                            val modelName = appStateManager.selectedVoskModelName.value
                                            val lang = appStateManager.voiceLanguage.value
                                            val customPath = appStateManager.customVoskModelPaths.value[lang]
                                            appStateManager.setVoiceModelReady(settingsManager.isModelDownloaded(modelName ?: "") || !customPath.isNullOrBlank())
                                        }
                                        else -> appStateManager.setVoiceModelReady(true)
                                    }
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
                                    appStateManager.setVoiceModelReady(isDownloaded || appStateManager.customWhisperModelPath.value != null)
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                    if (!isDownloaded && appStateManager.customWhisperModelPath.value == null) {
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
                                    val customPath = appStateManager.customVoskModelPaths.value[langCode]
                                    appStateManager.setVoiceModelReady(isDownloaded || !customPath.isNullOrBlank())
                                    updateVoiceEngine()
                                    onRefreshMain()
                                    refreshTrigger++
                                    if (!isDownloaded && customPath == null) {
                                        downloadingItemState = model
                                        pendingVoskModel = langCode to model
                                        showDownloadDialog = true
                                    }
                                },
                                onSelectCustomVoskModel = { onSelectCustomVoskModel(voiceLanguage) },
                                downloadProgress = downloadProgress,
                                downloadingItem = downloadingItemState,
                                downloadedColor = downloadedColor,
                                onCancelDownload = onCancelDownload,
                                onCleanupRequest = { showCleanupDialog = true },
                                onClearDefaultFallback = {
                                    settingsManager.clearDefaultOfflineFallback()
                                    refreshTrigger++
                                },
                                onDownloadLlamaModel = { model ->
                                    downloadingItemState = model
                                    pendingLlamaModel = model
                                    showDownloadDialog = true
                                },
                                onDeleteLlamaModel = { model ->
                                    isDeletingDefaultFallback = (model.id == settingsManager.getDefaultOfflineFallbackModel())
                                    isDeletingActiveWakeWord = false
                                    modelToDelete = model
                                    showDeleteConfirmDialog = true
                                },
                                onDeleteRequest = { model ->
                                    val modelId = when(model) {
                                        is WhisperModelInfo -> model.id
                                        is VoskModelInfo -> model.name
                                        else -> model.toString()
                                    }
                                    isDeletingDefaultFallback = (modelId == settingsManager.getDefaultOfflineFallbackModel())
                                    isDeletingActiveWakeWord = (modelId == settingsManager.getWakeWordModelPath())
                                    modelToDelete = model
                                    showDeleteConfirmDialog = true
                                },
                                refreshTrigger = refreshTrigger
                            )
                            2 -> ServiceSettingsTab(
                                languageManager = languageManager,
                                settingsManager = settingsManager,
                                wakeWordEnabled = wakeWordEnabled,
                                onWakeWordEnabledChange = { enabled ->
                                    wakeWordEnabled = enabled
                                    settingsManager.saveWakeWordEnabled(enabled)
                                    if (!enabled && isServiceRunning) {
                                        WakeWordService.stopService(context)
                                    }
                                    refreshTrigger++
                                },
                                wakeWord = wakeWordState,
                                onWakeWordChange = { 
                                    wakeWordState = it
                                    settingsManager.saveWakeWord(it)
                                    if (isServiceRunning) WakeWordService.stopService(context)
                                    refreshTrigger++
                                },
                                voskGroups = voskGroups,
                                selectedWakeWordModel = voskGroups.flatMap { it.models }.find { it.name == settingsManager.getWakeWordModelPath() } ?: voskGroups.firstOrNull()?.models?.firstOrNull(),
                                onWakeWordModelSelected = { model ->
                                    settingsManager.saveWakeWordModelPath(model.name)
                                    if (isServiceRunning) WakeWordService.stopService(context)
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
                                onDownloadRequest = { model ->
                                    downloadingItemState = model
                                    pendingWakeWordModel = model
                                    showDownloadDialog = true
                                },
                                onDeleteRequest = { model -> 
                                    isDeletingDefaultFallback = (model.name == settingsManager.getDefaultOfflineFallbackModel())
                                    isDeletingActiveWakeWord = true
                                    modelToDelete = model
                                    showDeleteConfirmDialog = true
                                },
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
                                onVerboseLoggingChange = { 
                                    verboseLoggingEnabled = it
                                    settingsManager.saveVerboseLoggingEnabled(it)
                                    refreshTrigger++
                                }
                            )
                            5 -> VerboseLoggingTab(
                                languageManager = languageManager,
                                verboseLoggingEnabled = verboseLoggingEnabled
                            )
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
        onDismissDownload = { 
            showDownloadDialog = false
            pendingVoskModel = null
            pendingWhisperModel = null
            pendingLlamaModel = null
            pendingWakeWordModel = null
            downloadingItemState = null 
        },
        onConfirmDownload = {
            pendingVoskModel?.let { (lang, model) -> onDownloadVoskModel(lang, model.url, model.name) }
            pendingWhisperModel?.let { model -> onDownloadWhisperModel(model.id, model.url) }
            pendingLlamaModel?.let { model -> onDownloadLlamaModel(model) }
            pendingWakeWordModel?.let { model -> 
                val group = voskGroups.find { it.models.contains(model) }
                val langCode = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                onDownloadVoskModel(langCode, model.url, model.name) 
            }
            showDownloadDialog = false; pendingVoskModel = null; pendingWhisperModel = null; pendingLlamaModel = null; pendingWakeWordModel = null
        },
        onDismissDelete = { showDeleteConfirmDialog = false; modelToDelete = null },
        onConfirmDelete = {
            modelToDelete?.let { model ->
                val modelId = when(model) {
                    is WhisperModelInfo -> model.id
                    is VoskModelInfo -> model.name
                    else -> (model as? AppModel)?.id ?: model.toString()
                }
                
                if (isDeletingDefaultFallback) {
                    settingsManager.clearDefaultOfflineFallback()
                }

                if (isDeletingActiveWakeWord && isServiceRunning) {
                    WakeWordService.stopService(context)
                }

                settingsManager.setModelDownloaded(modelId, false)
                if (appStateManager.selectedWhisperModelId.value == modelId || 
                    appStateManager.selectedVoskModelName.value == modelId ||
                    appStateManager.selectedLlamaModelId.value == modelId) {
                    appStateManager.setVoiceModelReady(false)
                }
                onRefreshMain()
                refreshTrigger++
            }
            showDeleteConfirmDialog = false
            modelToDelete = null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsTab_Realtime(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    refreshTrigger: Int = 0
) {
    var apiKey by remember { mutableStateOf(settingsManager.getApiKey() ?: "") }
    val languages = languageManager.getAvailableLanguages()
    val appLanguage = settingsManager.getLanguage()

    Text(text = languageManager.getString("app_settings_section"), style = MaterialTheme.typography.titleMedium)

    TextField(
        value = apiKey,
        onValueChange = { 
            apiKey = it 
            settingsManager.saveApiKey(it)
        },
        label = { Text(languageManager.getString("api_key")) },
        modifier = Modifier.fillMaxWidth()
    )

    Text(text = languageManager.getString("language"), style = MaterialTheme.typography.labelLarge)

    Box {
        var expanded by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(appLanguage.uppercase())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth()) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.uppercase()) },
                    onClick = {
                        settingsManager.saveLanguage(lang)
                        languageManager.loadLanguage(lang)
                        expanded = false
                    }
                )
            }
        }
    }

    HorizontalDivider()

    Text(text = languageManager.getString("offline_fallback_section"), style = MaterialTheme.typography.titleMedium)

    Text(text = languageManager.getString("offline_fallback_timeout"), style = MaterialTheme.typography.labelLarge)
    
    val timeoutOptions = listOf(
        5 to "5s",
        10 to "10s", 
        25 to "25s",
        35 to "35s",
        60 to "1m",
        120 to "2m",
        300 to "5m"
    )
    val currentTimeout = settingsManager.getOfflineFallbackTimeout()
    
    Box {
        var timeoutExpanded by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { timeoutExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(timeoutOptions.find { it.first == currentTimeout }?.second ?: "${currentTimeout}s")
        }
        DropdownMenu(expanded = timeoutExpanded, onDismissRequest = { timeoutExpanded = false }, modifier = Modifier.fillMaxWidth()) {
            timeoutOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        settingsManager.saveOfflineFallbackTimeout(value)
                        timeoutExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(text = languageManager.getString("default_offline_model"), style = MaterialTheme.typography.labelLarge)
    val whisperModels = WhisperModelRegistry.models
    val defaultProcessor = remember(refreshTrigger) { settingsManager.getDefaultOfflineFallbackProcessor() }
    val defaultModel = remember(refreshTrigger) { settingsManager.getDefaultOfflineFallbackModel() }
    val selectedModel = whisperModels.find { it.id == defaultModel }
    Text(
        text = if (defaultProcessor != null && defaultModel != null) {
            "$defaultProcessor: ${selectedModel?.let { "${it.label} (${it.sizeDescription})" } ?: defaultModel}"
        } else {
            "None"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary
    )
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
            confirmButton = {
                TextButton(onClick = onConfirmCleanup, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(languageManager.getString("delete_button"))
                }
            },
            dismissButton = { TextButton(onClick = onDismissCleanup) { Text(languageManager.getString("cancel_button")) } }
        )
    }

    if (showDownloadDialog) {
        val model = pendingVoskModel?.second ?: pendingWhisperModel ?: pendingLlamaModel ?: pendingWakeWordModel
        AlertDialog(
            onDismissRequest = onDismissDownload,
            title = { Text(languageManager.getString("download_model_title")) },
            text = { 
                model?.let {
                    Text(languageManager.getString("download_model_msg").format(it.label, it.sizeDescription))
                }
            },
            confirmButton = { TextButton(onClick = onConfirmDownload) { Text(languageManager.getString("download_button")) } },
            dismissButton = { TextButton(onClick = onDismissDownload) { Text(languageManager.getString("cancel_button")) } }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(languageManager.getString("confirm_delete_title")) },
            text = {
                val model = modelToDelete as? AppModel
                val msgTemplate = if (isDeletingDefaultFallback) {
                    languageManager.getString("confirm_delete_default_msg")
                } else {
                    languageManager.getString("confirm_delete_msg")
                }
                
                Column {
                    model?.let {
                        Text(msgTemplate.format(it.engineType, "${it.label} (${it.sizeDescription})"))
                    }
                    if (isDeletingActiveWakeWord) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = languageManager.getString("delete_active_wakeword_warning"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isDeletingDefaultFallback) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = languageManager.getString("delete_default_fallback_warning"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(languageManager.getString("delete_button"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(languageManager.getString("cancel_button"))
                }
            }
        )
    }
}
