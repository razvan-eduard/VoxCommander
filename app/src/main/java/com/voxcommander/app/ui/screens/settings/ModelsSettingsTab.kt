package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.domain.engine.whisper.WhisperModelInfo
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.launch
import java.io.File

object ModelsSettingsTabConfig {
    const val SHOW_SAVE_BUTTON = true
    var isModelOnDevice: Boolean = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    voiceProcessor: String,
    onProcessorSelected: (String) -> Unit,
    hasApiKey: Boolean,
    googleSttAvailable: Boolean,
    voiceLanguage: String,
    onVoiceLanguageSelected: (String) -> Unit,
    whisperModels: List<WhisperModelInfo>,
    selectedWhisperModel: WhisperModelInfo?,
    onWhisperModelSelected: (WhisperModelInfo, Boolean) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    voskGroups: List<VoskLanguageGroup>,
    selectedVoskModel: VoskModelInfo?,
    isVoskLoading: Boolean,
    isVoskOffline: Boolean,
    isOffline: Boolean,
    voskError: String?,
    onRetryConnection: suspend () -> Unit,
    onVoskModelSelected: (VoskModelInfo, Boolean, String) -> Unit,
    onSelectCustomVoskModel: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    downloadedColor: Color,
    onCancelDownload: () -> Unit,
    onCleanupRequest: () -> Unit,
    onClearDefaultFallback: () -> Unit,
    onDeleteRequest: (Any) -> Unit, 
    refreshTrigger: Int = 0
) {
    val scope = rememberCoroutineScope()

    val filteredVoskGroups = remember(voskGroups, isOffline, refreshTrigger) {
        if (isOffline) {
            voskGroups.map { group ->
                VoskLanguageGroup(
                    language = group.language,
                    models = group.models.filter { settingsManager.isModelDownloaded(it.name) }
                )
            }.filter { it.models.isNotEmpty() }
        } else {
            voskGroups
        }
    }

    val filteredWhisperModels = remember(whisperModels, isOffline, refreshTrigger) {
        if (isOffline) {
            whisperModels.filter { settingsManager.isModelDownloaded(it.id) }
        } else {
            whisperModels
        }
    }

    val isSelectedModelOnDevice = remember(voiceProcessor, selectedWhisperModel, selectedVoskModel, voiceLanguage, refreshTrigger) {
        when (voiceProcessor) {
            Strings.Processors.WHISPER_CPP,
            Strings.Processors.WHISPER_VULKAN,
            Strings.Processors.WHISPER_NEON -> {
                selectedWhisperModel != null && settingsManager.isModelDownloaded(selectedWhisperModel.id)
            }
            Strings.Processors.VOSK -> {
                val customPath = settingsManager.getCustomVoskModelPath(voiceLanguage)
                if (!customPath.isNullOrBlank()) {
                    File(customPath).exists()
                } else {
                    selectedVoskModel != null && settingsManager.isModelDownloaded(selectedVoskModel.name)
                }
            }
            else -> true 
        }
    }

    LaunchedEffect(isSelectedModelOnDevice) {
        ModelsSettingsTabConfig.isModelOnDevice = isSelectedModelOnDevice
    }


    // Voice Processor Selection (at top)
    var showProcessorInfo by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = languageManager.getString("voice_processor_section"), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { showProcessorInfo = true }) {
            Icon(Icons.Outlined.Info, contentDescription = "Processor Info")
        }
    }
    Box {
        var processorExpanded by remember { mutableStateOf(false) }
        val processors = listOf(
            Strings.Processors.WHISPER_VULKAN,
            Strings.Processors.WHISPER_NEON,
            Strings.Processors.VOSK,
            Strings.Processors.GOOGLE,
            Strings.Processors.WHISPER_API
        )

        OutlinedButton(onClick = { processorExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(voiceProcessor.replace("_", " "))
        }
        DropdownMenu(expanded = processorExpanded, onDismissRequest = { processorExpanded = false }, modifier = Modifier.fillMaxWidth()) {
            processors.forEach { proc ->
                val enabled = when (proc) {
                    Strings.Processors.WHISPER_API -> hasApiKey
                    Strings.Processors.GOOGLE -> googleSttAvailable
                    Strings.Processors.WHISPER_VULKAN -> !settingsManager.isVulkanIncompatible()
                    else -> true
                }
                val displayText = when {
                    proc == Strings.Processors.GOOGLE && !googleSttAvailable -> languageManager.getString("google_not_available")
                    proc == Strings.Processors.WHISPER_VULKAN && settingsManager.isVulkanIncompatible() -> "WHISPER VULKAN (Incompatible)"
                    else -> proc.replace("_", " ")
                }

                DropdownMenuItem(
                    text = { Text(text = displayText, color = if (enabled) LocalContentColor.current else Color.Gray) },
                    onClick = {
                        if (enabled) {
                            onProcessorSelected(proc)
                            processorExpanded = false
                        }
                    },
                    enabled = enabled
                )
            }
        }
    }

    HorizontalDivider()

    // Voice Language Selection (Only visible for Google/API)
    if (voiceProcessor == Strings.Processors.GOOGLE || voiceProcessor == Strings.Processors.WHISPER_API) {
        val languages = listOf(
            "ro" to languageManager.getString("voice_language_ro"),
            "en" to languageManager.getString("voice_language_en"),
            "de" to languageManager.getString("voice_language_de"),
            "fr" to languageManager.getString("voice_language_fr"),
            "es" to languageManager.getString("voice_language_es"),
            "it" to languageManager.getString("voice_language_it"),
            "pt" to languageManager.getString("voice_language_pt"),
            "nl" to languageManager.getString("voice_language_nl"),
            "pl" to languageManager.getString("voice_language_pl"),
            "ru" to languageManager.getString("voice_language_ru"),
            "uk" to languageManager.getString("voice_language_uk"),
            "tr" to languageManager.getString("voice_language_tr"),
            "ar" to languageManager.getString("voice_language_ar"),
            "zh" to languageManager.getString("voice_language_zh"),
            "ja" to languageManager.getString("voice_language_ja"),
            "ko" to languageManager.getString("voice_language_ko"),
            "hi" to languageManager.getString("voice_language_hi"),
            "sv" to languageManager.getString("voice_language_sv"),
            "da" to languageManager.getString("voice_language_da"),
            "fi" to languageManager.getString("voice_language_fi"),
            "no" to languageManager.getString("voice_language_no"),
            "cs" to languageManager.getString("voice_language_cs"),
            "el" to languageManager.getString("voice_language_el"),
            "he" to languageManager.getString("voice_language_he"),
            "id" to languageManager.getString("voice_language_id"),
            "vi" to languageManager.getString("voice_language_vi"),
            "th" to languageManager.getString("voice_language_th"),
            "ms" to languageManager.getString("voice_language_ms"),
            "hu" to languageManager.getString("voice_language_hu"),
            "bg" to languageManager.getString("voice_language_bg"),
            "sr" to languageManager.getString("voice_language_sr"),
            "hr" to languageManager.getString("voice_language_hr"),
            "sk" to languageManager.getString("voice_language_sk"),
            "sl" to languageManager.getString("voice_language_sl"),
            "et" to languageManager.getString("voice_language_et"),
            "lv" to languageManager.getString("voice_language_lv"),
            "lt" to languageManager.getString("voice_language_lt")
        )

        var showLanguageSheet by remember { mutableStateOf(false) }
        val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val languageGroups = listOf(
            DropdownGroup(
                header = "EUROPEAN",
                items = languages.filter { it.first in listOf("ro", "en", "de", "fr", "es", "it", "pt", "nl", "pl", "uk", "tr", "sv", "da", "fi", "no", "cs", "el", "hu", "bg", "sr", "hr", "sk", "sl", "et", "lv", "lt") }
            ),
            DropdownGroup(
                header = "ASIAN",
                items = languages.filter { it.first in listOf("zh", "ja", "ko", "hi", "th", "id", "vi", "ms") }
            ),
            DropdownGroup(
                header = "MIDDLE EAST",
                items = languages.filter { it.first in listOf("ar", "he") }
            ),
            DropdownGroup(
                header = "OTHER",
                items = languages.filter { it.first == "ru" }
            )
        )

        val selectedLangPair = languages.find { it.first == voiceLanguage }

        Text(text = languageManager.getString("voice_language"), style = MaterialTheme.typography.labelLarge)
        
        GroupedDropdownMenu(
            selectedItem = selectedLangPair,
            groups = languageGroups,
            itemLabel = { it.second },
            isDownloaded = { true }, // Remote languages are always ready
            onDeviceLabel = "",
            onItemSelected = { pair, _ -> 
                onVoiceLanguageSelected(pair.first)
            },
            onExpandedChange = { showLanguageSheet = it },
            languageManager = languageManager,
            placeholder = languageManager.getString("voice_language")
        )

        if (showLanguageSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLanguageSheet = false },
                sheetState = languageSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                GroupedDropdownContent(
                    title = languageManager.getString("voice_language"),
                    groups = languageGroups,
                    itemLabel = { it.second },
                    isDownloaded = { true },
                    onDeviceLabel = "",
                    onItemSelected = { pair, _ ->
                        onVoiceLanguageSelected(pair.first)
                        showLanguageSheet = false
                    },
                    languageManager = languageManager
                )
            }
        }
        
        HorizontalDivider()
    }

    // Model Selection
    var showModelInfo by remember { mutableStateOf(false) }
    when (voiceProcessor) {
        Strings.Processors.WHISPER_CPP, 
        Strings.Processors.WHISPER_VULKAN, 
        Strings.Processors.WHISPER_NEON -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = languageManager.getString("whisper_model_select"), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { showModelInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Model Info")
                }
            }
            WhisperSettingsSection(
                languageManager = languageManager,
                settingsManager = settingsManager,
                whisperModels = filteredWhisperModels,
                selectedModel = selectedWhisperModel ?: filteredWhisperModels.firstOrNull(),
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                downloadedColor = downloadedColor,
                onModelSelected = { model, isDownloaded -> onWhisperModelSelected(model, isDownloaded) },
                onSelectCustomModel = onSelectCustomWhisperModel,
                onCancelDownload = onCancelDownload,
                onDeleteRequest = onDeleteRequest, 
                currentProcessor = voiceProcessor,
                refreshTrigger = refreshTrigger
            )
        }
        Strings.Processors.VOSK -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = languageManager.getString("vosk_model_select"), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { showModelInfo = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Model Info")
                }
            }
            VoskSettingsSection(
                languageManager = languageManager,
                settingsManager = settingsManager,
                voskGroups = filteredVoskGroups,
                selectedModel = selectedVoskModel,
                voiceLanguage = voiceLanguage,
                isVoskLoading = isVoskLoading,
                isVoskOffline = isVoskOffline,
                voskError = voskError,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                downloadedColor = downloadedColor,
                onRetryConnection = { scope.launch { onRetryConnection() } },
                onModelSelected = { model, isDownloaded, code -> onVoskModelSelected(model, isDownloaded, code) },
                onSelectCustomModel = onSelectCustomVoskModel,
                onCancelDownload = onCancelDownload,
                onDeleteRequest = onDeleteRequest, 
                currentProcessor = voiceProcessor,
                refreshTrigger = refreshTrigger
            )
        }
        else -> {
            Text(
                text = "No model settings available for ${voiceProcessor.replace("_", " ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }

    // Delete Unused Models Button (only on Models tab)
    Spacer(modifier = Modifier.height(4.dp))
    Button(
        onClick = onCleanupRequest,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text(languageManager.getString("delete_unused_models"))
    }

    // Clear Default Offline Fallback Model Button
    Spacer(modifier = Modifier.height(2.dp))
    Button(
        onClick = onClearDefaultFallback,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Text(languageManager.getString("clear_default_fallback"))
    }

    // Processor info dialog
    if (showProcessorInfo) {
        AlertDialog(
            onDismissRequest = { showProcessorInfo = false },
            title = { Text(languageManager.getString("processor_info_title")) },
            text = {
                Column {
                    Text(languageManager.getString("processor_info_whisper"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(languageManager.getString("processor_info_vosk"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(languageManager.getString("processor_info_google"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(languageManager.getString("processor_info_whisper_api"), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showProcessorInfo = false }) {
                    Text(languageManager.getString("ok"))
                }
            }
        )
    }

    // Model info dialog
    if (showModelInfo) {
        AlertDialog(
            onDismissRequest = { showModelInfo = false },
            title = { Text(languageManager.getString("model_info_title")) },
            text = {
                Column {
                    when (voiceProcessor) {
                        Strings.Processors.WHISPER_CPP,
                        Strings.Processors.WHISPER_VULKAN,
                        Strings.Processors.WHISPER_NEON -> {
                            Text(languageManager.getString("whisper_models_title"), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(languageManager.getString("whisper_tiny"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("whisper_base"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("whisper_small"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("whisper_medium"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("whisper_large"), style = MaterialTheme.typography.bodyMedium)
                        }
                        Strings.Processors.VOSK -> {
                            Text(languageManager.getString("vosk_models_title"), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(languageManager.getString("vosk_small"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("vosk_large"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(languageManager.getString("vosk_language_specific"), style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Text(languageManager.getString("no_model_info"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelInfo = false }) {
                    Text(languageManager.getString("ok"))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperSettingsSection(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    whisperModels: List<WhisperModelInfo>,
    selectedModel: WhisperModelInfo?,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    downloadedColor: Color,
    onModelSelected: (WhisperModelInfo, Boolean) -> Unit,
    onSelectCustomModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteRequest: (Any) -> Unit, 
    currentProcessor: String,
    refreshTrigger: Int = 0
) {
    var showWhisperSheet by remember { mutableStateOf(false) }
    val whisperSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val groups = remember {
        listOf(
            DropdownGroup(languageManager.getString("multilingual_models_header"), whisperModels.filter { it.isMultilingual }),
            DropdownGroup(languageManager.getString("english_only_models_header"), whisperModels.filter { !it.isMultilingual })
        )
    }

    GroupedDropdownMenu(
        selectedItem = selectedModel,
        groups = groups,
        itemLabel = { "${it.label} (${it.sizeDescription})" },
        isDownloaded = { model ->
            remember(model.id, refreshTrigger) { settingsManager.isModelDownloaded(model.id) }
        },
        onDeviceLabel = languageManager.getString("on_device_label"),
        onItemSelected = { model, isDownloaded -> onModelSelected(model, isDownloaded) },
        onExpandedChange = { showWhisperSheet = it },
        onDownloadRequest = { model ->
            if (!settingsManager.isModelDownloaded(model.id) && settingsManager.getCustomWhisperModelPath() == null) {
                onModelSelected(model, false)
            }
        },
        onDeleteRequest = onDeleteRequest, 
        onCancelDownload = onCancelDownload,
        downloadProgress = downloadProgress,
        downloadingItem = downloadingItem,
        languageManager = languageManager
    )

    // ModalBottomSheet for model selection
    if (showWhisperSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWhisperSheet = false },
            sheetState = whisperSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GroupedDropdownContent(
                title = languageManager.getString("whisper_model_select"),
                groups = groups,
                itemLabel = { "${it.label} (${it.sizeDescription})" },
                isDownloaded = { model ->
                    remember(model.id, refreshTrigger) { settingsManager.isModelDownloaded(model.id) }
                },
                onDeviceLabel = languageManager.getString("on_device_label"),
                onItemSelected = { model, isDownloaded ->
                    if (model != null) {
                        onModelSelected(model, isDownloaded)
                        showWhisperSheet = false
                    }
                },
                onDownloadRequest = { model ->
                    if (!settingsManager.isModelDownloaded(model.id) && settingsManager.getCustomWhisperModelPath() == null) {
                        onModelSelected(model, false)
                    }
                },
                onDeleteRequest = onDeleteRequest, 
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                languageManager = languageManager
            )
        }
    }

    // Default offline model checkbox
    var defaultProcessor by remember(refreshTrigger) { mutableStateOf(settingsManager.getDefaultOfflineFallbackProcessor()) }
    var defaultModel by remember(refreshTrigger) { mutableStateOf(settingsManager.getDefaultOfflineFallbackModel()) }
    
    // SAFEGUARD: Check if currently selected model is actually on device
    val isCurrentSelectedOnDevice = remember(selectedModel, refreshTrigger) {
        selectedModel != null && settingsManager.isModelDownloaded(selectedModel.id)
    }
    
    val isDefaultOffline = selectedModel?.let { model ->
        defaultProcessor == currentProcessor && defaultModel == model.id
    } ?: false
    var showChangeDialog by remember { mutableStateOf(false) }
    var showClearWarningDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            selectedModel?.let { model ->
                if (!isDefaultOffline) {
                    if (defaultProcessor != null && defaultModel != null &&
                        (defaultProcessor != currentProcessor || defaultModel != model.id)) {
                        showChangeDialog = true
                    } else {
                        settingsManager.saveDefaultOfflineFallback(currentProcessor, model.id)
                        defaultProcessor = currentProcessor
                        defaultModel = model.id
                    }
                } else {
                    showClearWarningDialog = true
                }
            }
        },
        enabled = isCurrentSelectedOnDevice, // DISABLED IF NOT ON DEVICE
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = if (isDefaultOffline) 2.dp else 1.dp,
            color = if (!isCurrentSelectedOnDevice) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else if (isDefaultOffline) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outline
        ),
        color = if (!isCurrentSelectedOnDevice) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else if (isDefaultOffline) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp)
        ) {
            Checkbox(
                checked = isDefaultOffline,
                onCheckedChange = null,
                enabled = isCurrentSelectedOnDevice // SYNC WITH SURFACE
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = languageManager.getString("default_offline_fallback_model"),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrentSelectedOnDevice) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }

    if (showChangeDialog) {
        selectedModel?.let { model ->
            val currentDefaultModel = whisperModels.find { it.id == defaultModel }
            AlertDialog(
                onDismissRequest = { showChangeDialog = false },
                title = { Text(languageManager.getString("change_default_model_title")) },
                text = {
                    Text(
                        languageManager.getString("change_default_model_message").format(
                            currentDefaultModel?.label ?: defaultModel,
                            model.label
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        settingsManager.saveDefaultOfflineFallback(currentProcessor, model.id)
                        defaultProcessor = currentProcessor
                        defaultModel = model.id
                        showChangeDialog = false
                    }) {
                        Text(languageManager.getString("change"))
                    }
                },
                dismissButton = {
                    Button(onClick = { showChangeDialog = false }) {
                        Text(languageManager.getString("cancel_button"))
                    }
                }
            )
        }
    }

    if (showClearWarningDialog) {
        AlertDialog(
            onDismissRequest = { showClearWarningDialog = false },
            title = { Text(languageManager.getString("clear_default_warning_title")) },
            text = { Text(languageManager.getString("clear_default_warning_message")) },
            confirmButton = {
                Button(onClick = {
                    settingsManager.clearDefaultOfflineFallback()
                    defaultProcessor = null
                    defaultModel = null
                    showClearWarningDialog = false
                }) {
                    Text(languageManager.getString("ok_button"))
                }
            },
            dismissButton = {
                Button(onClick = { showClearWarningDialog = false }) {
                    Text(languageManager.getString("cancel_button"))
                }
            }
        )
    }

    settingsManager.getCustomWhisperModelPath()?.let { path ->
        Text(
            text = languageManager.getString("using_custom").format(path),
            style = MaterialTheme.typography.bodySmall, 
            color = MaterialTheme.colorScheme.secondary
        )
    }

    OutlinedButton(onClick = onSelectCustomModel, modifier = Modifier.fillMaxWidth()) {
        Text(languageManager.getString("select_custom_whisper"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoskSettingsSection(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    voskGroups: List<VoskLanguageGroup>,
    selectedModel: VoskModelInfo?,
    voiceLanguage: String,
    isVoskLoading: Boolean,
    isVoskOffline: Boolean,
    voskError: String?,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    downloadedColor: Color,
    onRetryConnection: suspend () -> Unit,
    onModelSelected: (VoskModelInfo, Boolean, String) -> Unit,
    onSelectCustomModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteRequest: (Any) -> Unit, 
    currentProcessor: String,
    refreshTrigger: Int = 0
) {
    val groupedModels = remember(voskGroups) {
        voskGroups.map { group ->
            val headerLabel = when {
                group.language.contains("English", ignoreCase = true) -> "ENGLISH"
                group.language.contains("Romanian", ignoreCase = true) -> "ROMANIAN"
                group.language.contains("Punctuation", ignoreCase = true) -> "PUNCTUATION"
                group.language.contains("Chinese", ignoreCase = true) -> "CHINESE"
                group.language.contains("Russian", ignoreCase = true) -> "RUSSIAN"
                group.language.contains("French", ignoreCase = true) -> "FRENCH"
                group.language.contains("German", ignoreCase = true) -> "GERMAN"
                group.language.contains("Spanish", ignoreCase = true) -> "SPANISH"
                group.language.contains("Italian", ignoreCase = true) -> "ITALIAN"
                group.language.contains("Japanese", ignoreCase = true) -> "JAPANESE"
                else -> group.language.uppercase()
            }
            DropdownGroup(
                header = headerLabel,
                items = group.models
            )
        }
    }

    if (isVoskLoading) CircularProgressIndicator(modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))

    if (isVoskOffline) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = languageManager.getString("offline_mode_msg"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            voskError?.let { Text(text = "Error: $it", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall) }
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = { scope.launch { onRetryConnection() } },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(languageManager.getString("retry_connection"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    var showVoskSheet by remember { mutableStateOf(false) }
    val voskSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GroupedDropdownMenu(
        selectedItem = selectedModel,
        groups = groupedModels,
        itemLabel = { if (it.size != null) "${it.name} (${it.size})" else it.name },
        isDownloaded = { model ->
            remember(model.name, refreshTrigger) { settingsManager.isModelDownloaded(model.name) }
        },
        onDeviceLabel = languageManager.getString("on_device_label"),
        onItemSelected = { model, isDownloaded ->
            val group = voskGroups.find { it.models.contains(model) }
            val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
            onModelSelected(model, isDownloaded, code)
        },
        onExpandedChange = { showVoskSheet = it },
        onDownloadRequest = { model ->
            if (!settingsManager.isModelDownloaded(model.name) && settingsManager.getCustomVoskModelPath(voiceLanguage) == null) {
                val group = voskGroups.find { it.models.contains(model) }
                val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                onModelSelected(model, false, code)
            }
        },
        onDeleteRequest = onDeleteRequest, 
        onCancelDownload = onCancelDownload,
        downloadProgress = downloadProgress,
        downloadingItem = downloadingItem,
        languageManager = languageManager
    )

    if (showVoskSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVoskSheet = false },
            sheetState = voskSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GroupedDropdownContent(
                title = languageManager.getString("vosk_model_select"),
                groups = groupedModels,
                itemLabel = { if (it.size != null) "${it.name} (${it.size})" else it.name },
                isDownloaded = { model ->
                    remember(model.name, refreshTrigger) { settingsManager.isModelDownloaded(model.name) }
                },
                onDeviceLabel = languageManager.getString("on_device_label"),
                onItemSelected = { model, isDownloaded ->
                    val group = voskGroups.find { it.models.contains(model) }
                    val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                    onModelSelected(model, isDownloaded, code)
                    showVoskSheet = false
                },
                onDownloadRequest = { model ->
                    if (!settingsManager.isModelDownloaded(model.name) && settingsManager.getCustomVoskModelPath(voiceLanguage) == null) {
                        val group = voskGroups.find { it.models.contains(model) }
                        val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                        onModelSelected(model, false, code)
                    }
                },
                onDeleteRequest = onDeleteRequest, 
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                languageManager = languageManager
            )
        }
    }

    // Default offline model checkbox
    var defaultProcessor by remember(refreshTrigger) { mutableStateOf(settingsManager.getDefaultOfflineFallbackProcessor()) }
    var defaultModel by remember(refreshTrigger) { mutableStateOf(settingsManager.getDefaultOfflineFallbackModel()) }
    
    // SAFEGUARD: Check if currently selected model is actually on device
    val isCurrentSelectedOnDevice = remember(selectedModel, refreshTrigger) {
        selectedModel != null && settingsManager.isModelDownloaded(selectedModel.name)
    }

    val isDefaultOffline = selectedModel?.let { model ->
        defaultProcessor == currentProcessor && defaultModel == model.name
    } ?: false
    var showChangeDialog by remember { mutableStateOf(false) }
    var showClearWarningDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            selectedModel?.let { model ->
                if (!isDefaultOffline) {
                    if (defaultProcessor != null && defaultModel != null &&
                        (defaultProcessor != currentProcessor || defaultModel != model.name)) {
                        showChangeDialog = true
                    } else {
                        settingsManager.saveDefaultOfflineFallback(currentProcessor, model.name)
                        defaultProcessor = currentProcessor
                        defaultModel = model.name
                    }
                } else {
                    showClearWarningDialog = true
                }
            }
        },
        enabled = isCurrentSelectedOnDevice, // DISABLED IF NOT ON DEVICE
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = if (isDefaultOffline) 2.dp else 1.dp,
            color = if (!isCurrentSelectedOnDevice) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else if (isDefaultOffline) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outline
        ),
        color = if (!isCurrentSelectedOnDevice) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else if (isDefaultOffline) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp)
        ) {
            Checkbox(
                checked = isDefaultOffline,
                onCheckedChange = null,
                enabled = isCurrentSelectedOnDevice // SYNC WITH SURFACE
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = languageManager.getString("default_offline_fallback_model"),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrentSelectedOnDevice) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }

    if (showChangeDialog) {
        selectedModel?.let { model ->
            AlertDialog(
                onDismissRequest = { showChangeDialog = false },
                title = { Text(languageManager.getString("change_default_model_title")) },
                text = {
                    Text(
                        languageManager.getString("change_default_model_message").format(
                            defaultModel,
                            model.name
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        settingsManager.saveDefaultOfflineFallback(currentProcessor, model.name)
                        defaultProcessor = currentProcessor
                        defaultModel = model.name
                        showChangeDialog = false
                    }) {
                        Text(languageManager.getString("change"))
                    }
                },
                dismissButton = {
                    Button(onClick = { showChangeDialog = false }) {
                        Text(languageManager.getString("cancel_button"))
                    }
                }
            )
        }
    }

    if (showClearWarningDialog) {
        AlertDialog(
            onDismissRequest = { showClearWarningDialog = false },
            title = { Text(languageManager.getString("clear_default_warning_title")) },
            text = { Text(languageManager.getString("clear_default_warning_message")) },
            confirmButton = {
                Button(onClick = {
                    settingsManager.clearDefaultOfflineFallback()
                    defaultProcessor = null
                    defaultModel = null
                    showClearWarningDialog = false
                }) {
                    Text(languageManager.getString("delete_button"))
                }
            },
            dismissButton = {
                Button(onClick = { showClearWarningDialog = false }) {
                    Text(languageManager.getString("cancel_button"))
                }
            }
        )
    }

    settingsManager.getCustomVoskModelPath(voiceLanguage)?.let { path ->
        Text(text = languageManager.getString("using_custom").format(path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }

    OutlinedButton(onClick = onSelectCustomModel, modifier = Modifier.fillMaxWidth()) {
        Text(languageManager.getString("select_custom_vosk"))
    }
}

@Composable
private fun DownloadProgressIndicator(progress: Float, statusLabel: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = "$statusLabel: ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
