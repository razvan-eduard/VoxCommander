package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.RemoteModelItem
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.EngineModelSection
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.utils.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEnginesSubTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    onProcessorSelected: (String) -> Unit,
    hasApiKey: Boolean,
    googleSttAvailable: Boolean,
    onVoiceLanguageSelected: (String) -> Unit,
    whisperModels: List<AppModel>,
    onWhisperModelSelected: (AppModel, Boolean) -> Unit,
    onSelectCustomWhisperModel: () -> Unit,
    voskModels: List<AppModel>,
    selectedVoskModel: AppModel?,
    isVoskLoading: Boolean,
    isOffline: Boolean,
    voskError: String?,
    onRetryConnection: suspend () -> Unit,
    onVoskModelSelected: (AppModel, Boolean, String) -> Unit,
    onSelectCustomVoskModel: () -> Unit,
    onDownloadWhisperModel: (String, String) -> Unit,
    onDownloadVoskModel: (String, String, String) -> Unit,
    downloadProgress: Float?,
    downloadingItem: AppModel? = null,
    downloadedColor: Color,
    onCancelDownload: () -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0,
    isWhisperMultilingual: Boolean = true,
    isVoskMultilingual: Boolean = false,
    availableVoskLanguages: List<String> = emptyList()
) {
    // REALTIME STATE from AppStateManager
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    val selectedWhisperModel = remember(uiState.selectedWhisperModelId, whisperModels) {
        whisperModels.find { it.id == uiState.selectedWhisperModelId } ?: whisperModels.firstOrNull()
    }

    // Determine if current processor is multilingual
    val isCurrentProcessorMultilingual = when (uiState.voiceProcessor) {
        Strings.Processors.WHISPER_CPP, Strings.Processors.WHISPER_VULKAN, Strings.Processors.WHISPER_NEON -> isWhisperMultilingual
        Strings.Processors.VOSK -> isVoskMultilingual
        else -> true
    }
    // 1. Processor Selection
    Text(text = languageManager.getString("voice_processor_section"), style = MaterialTheme.typography.titleMedium)
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
            Text(uiState.voiceProcessor.replace("_", " "))
        }
        DropdownMenu(expanded = processorExpanded, onDismissRequest = { processorExpanded = false }, modifier = Modifier.fillMaxWidth()) {
            processors.forEach { proc ->
                val enabled = when (proc) {
                    Strings.Processors.WHISPER_API -> hasApiKey
                    Strings.Processors.GOOGLE -> googleSttAvailable
                    Strings.Processors.WHISPER_VULKAN -> !settingsManager.isVulkanIncompatible()
                    else -> true
                }
                DropdownMenuItem(
                    text = { Text(proc.replace("_", " "), color = if (enabled) LocalContentColor.current else Color.Gray) },
                    onClick = { if (enabled) { onProcessorSelected(proc); processorExpanded = false } },
                    enabled = enabled
                )
            }
        }
    }

    HorizontalDivider()

    // 2. Global Voice Language Selection (only for non-multilingual engines)
    if (!isCurrentProcessorMultilingual) {
        val languages = if (uiState.voiceProcessor == Strings.Processors.VOSK) {
            availableVoskLanguages.map { lang ->
                lang to lang.uppercase()
            }
        } else {
            listOf(
                "ro" to languageManager.getString("voice_language_ro"),
                "en" to languageManager.getString("voice_language_en"),
                "de" to languageManager.getString("voice_language_de"),
                "fr" to languageManager.getString("voice_language_fr"),
                "es" to languageManager.getString("voice_language_es"),
                "it" to languageManager.getString("voice_language_it")
            )
        }

        var showLanguageSheet by remember { mutableStateOf(false) }
        val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val languageGroups = listOf(DropdownGroup("AVAILABLE LANGUAGES", languages))
        val selectedLangPair = languages.find { it.first == uiState.voiceLanguage }

        Text(text = languageManager.getString("voice_language"), style = MaterialTheme.typography.labelLarge)

        GroupedDropdownMenu(
            selectedItem = selectedLangPair,
            groups = languageGroups,
            itemLabel = { it.second },
            isDownloaded = { true },
            onDeviceLabel = "",
            onItemSelected = { pair, _ -> onVoiceLanguageSelected(pair.first) },
            onExpandedChange = { showLanguageSheet = it },
            languageManager = languageManager
        )

        if (showLanguageSheet) {
            ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, sheetState = languageSheetState) {
                GroupedDropdownContent(
                    title = languageManager.getString("voice_language"),
                    groups = languageGroups,
                    itemLabel = { it.second },
                    isDownloaded = { true },
                    onDeviceLabel = "",
                    onItemSelected = { pair, _ -> onVoiceLanguageSelected(pair.first); showLanguageSheet = false },
                    languageManager = languageManager
                )
            }
        }

        HorizontalDivider()
    }

    // 3. API Model Selection (OpenAI Whisper)
    if (uiState.voiceProcessor == Strings.Processors.WHISPER_API) {
        val apiModels = listOf("whisper-1") // Add more as they become available
        var selectedApiModel by remember { mutableStateOf(apiModels.first()) }
        val isSelectionEnabled = apiModels.size > 1
        
        Text(
            text = "OpenAI Whisper Model", 
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelectionEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { if (isSelectionEnabled) expanded = true }, 
                modifier = Modifier.fillMaxWidth(),
                enabled = isSelectionEnabled
            ) {
                Text(text = selectedApiModel)
            }
            
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f) // Match the width of the button roughly in the container
            ) {
                apiModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            selectedApiModel = model
                            expanded = false
                        }
                    )
                }
            }
        }
        HorizontalDivider()
    }

    // 4. Engine Specific Sections
    when (uiState.voiceProcessor) {
        Strings.Processors.WHISPER_CPP, 
        Strings.Processors.WHISPER_VULKAN, 
        Strings.Processors.WHISPER_NEON -> {
            EngineModelSection(
                title = languageManager.getString("whisper_model_select"),
                languageManager = languageManager,
                settingsManager = settingsManager,
                appStateManager = appStateManager,
                groups = remember(whisperModels, refreshTrigger) {
                    listOf(
                        DropdownGroup(languageManager.getString("multilingual_models_header"), whisperModels.filter { (it as? RemoteModelItem)?.is_multilingual == true }),
                        DropdownGroup(languageManager.getString("english_only_models_header"), whisperModels.filter { (it as? RemoteModelItem)?.is_multilingual != true })
                    )
                },
                selectedItem = selectedWhisperModel ?: whisperModels.firstOrNull(),
                itemLabel = { "${it.label} (${it.sizeDescription})" },
                modelIdProvider = { it.id },
                onItemSelected = { model, isDownloaded ->
                    onWhisperModelSelected(model, isDownloaded)
                },
                onDownloadRequest = { model ->
                    onDownloadWhisperModel(model.id, model.url)
                },
                onDeleteRequest = onDeleteRequest,
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                currentProcessor = uiState.voiceProcessor,
                fallbackCategory = Strings.FallbackCategories.VOICE,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        }
        Strings.Processors.VOSK -> {
            // Filter Vosk models by selected language
            val filteredVoskModels = remember(voskModels, uiState.voiceLanguage) {
                voskModels.filter { (it as? RemoteModelItem)?.lang_code == uiState.voiceLanguage }
            }

            EngineModelSection(
                title = languageManager.getString("vosk_model_select"),
                languageManager = languageManager,
                settingsManager = settingsManager,
                appStateManager = appStateManager,
                groups = remember(filteredVoskModels, refreshTrigger) {
                    if (filteredVoskModels.isNotEmpty()) {
                        listOf(DropdownGroup("AVAILABLE MODELS", filteredVoskModels))
                    } else emptyList()
                },
                selectedItem = selectedVoskModel,
                itemLabel = { it.label + " (" + it.sizeDescription + ")" },
                modelIdProvider = { it.id },
                onItemSelected = { model, isDownloaded ->
                    val code = (model as? RemoteModelItem)?.lang_code ?: uiState.voiceLanguage
                    onVoskModelSelected(model, isDownloaded, code)
                },
                onDownloadRequest = { model ->
                    val code = (model as? RemoteModelItem)?.lang_code ?: uiState.voiceLanguage
                    onDownloadVoskModel(code, model.url, model.id)
                },
                onDeleteRequest = onDeleteRequest,
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                currentProcessor = uiState.voiceProcessor,
                fallbackCategory = Strings.FallbackCategories.VOICE,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
        }
    }
}
