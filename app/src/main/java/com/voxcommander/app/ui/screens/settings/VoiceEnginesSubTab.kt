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
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.EngineModelSection
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.utils.Logger
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
    onModelSelected: (AppModel, Boolean, String) -> Unit,
    onDownloadModel: (String, String, String?) -> Unit,
    downloadProgress: Float?,
    downloadingItem: AppModel? = null,
    downloadedColor: Color,
    onCancelDownload: () -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0
) {
    // REALTIME STATE from AppStateManager
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    // 1. Resolve Engine Key
    val engineKey = uiState.voiceProcessor
    
    val isCurrentProcessorMultilingual = RemoteModelRegistry.isMultilingual(engineKey)
    val availableLanguages = RemoteModelRegistry.getLanguages(engineKey)

    Logger.log("VoiceEnginesSubTab: engineKey=$engineKey, isMultilingual=$isCurrentProcessorMultilingual, availLangs=${availableLanguages.size}", "VoiceEnginesSubTab")
    Logger.log("VoiceEnginesSubTab: availableModels keys=${uiState.availableModels.keys}", "VoiceEnginesSubTab")

    // 1. Processor Selection
    Text(text = languageManager.getString("voice_processor_section"), style = MaterialTheme.typography.titleMedium)
    Box {
        var processorExpanded by remember { mutableStateOf(false) }
        
        // Build list of processors: JSON engines (type=voice) + Local/Virtual injections
        val processors = remember(uiState.availableModels, uiState.isExperimentalVulkanEnabled) {
            val list = RemoteModelRegistry.getEngineKeysByType("voice").toMutableList()
            
            // Add virtual models
            if (!list.contains(Strings.Processors.GOOGLE)) list.add(Strings.Processors.GOOGLE)
            if (!list.contains(Strings.Processors.WHISPER_API)) list.add(Strings.Processors.WHISPER_API)
            
            // Experimental Vulkan
            if (uiState.isExperimentalVulkanEnabled && !list.contains(Strings.Processors.WHISPER_VULKAN)) {
                list.add(0, Strings.Processors.WHISPER_VULKAN)
            }
            list
        }

        OutlinedButton(onClick = { processorExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(RemoteModelRegistry.getEngineLabel(uiState.voiceProcessor, languageManager))
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
                    text = { 
                        Text(
                            text = RemoteModelRegistry.getEngineLabel(proc, languageManager), 
                            color = if (enabled) LocalContentColor.current else Color.Gray
                        ) 
                    },
                    onClick = { if (enabled) { onProcessorSelected(proc); processorExpanded = false } },
                    enabled = enabled
                )
            }
        }
    }

    HorizontalDivider()

    // 2. Global Voice Language Selection (only for non-multilingual engines)
    if (!isCurrentProcessorMultilingual && availableLanguages.isNotEmpty()) {
        val languages = availableLanguages.map { lang ->
            lang to lang.uppercase()
        }

        var showLanguageSheet by remember { mutableStateOf(false) }
        val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val languageGroups = listOf(DropdownGroup(languageManager.getString("available_languages_header") ?: "AVAILABLE LANGUAGES", languages))
        val selectedLangPair = languages.find { it.first == uiState.voiceLanguage }

        Text(text = languageManager.getString("voice_language"), style = MaterialTheme.typography.labelLarge)

        GroupedDropdownMenu(
            selectedItem = selectedLangPair,
            groups = languageGroups,
            itemLabel = { it.second },
            isDownloaded = { true },
            onDeviceLabel = "",
            onItemSelected = { pair, _ -> onVoiceLanguageSelected(pair.first) },
            onExpandedChange = { showSheet -> showLanguageSheet = showSheet },
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
        val apiModels = listOf("whisper-1")
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
                modifier = Modifier.fillMaxWidth(0.9f)
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
    val models = uiState.availableModels[engineKey] ?: emptyList()

    // Agnostic model filtering by language
    val filteredModels = remember(models, uiState.voiceLanguage, isCurrentProcessorMultilingual) {
        if (isCurrentProcessorMultilingual) models 
        else models.filter { it.langCode == uiState.voiceLanguage }
    }

    if (filteredModels.isNotEmpty()) {
        EngineModelSection(
            title = languageManager.getString("select_model"),
            languageManager = languageManager,
            settingsManager = settingsManager,
            appStateManager = appStateManager,
            groups = remember(filteredModels, refreshTrigger) {
                listOf(DropdownGroup(languageManager.getString("available_models_header"), filteredModels))
            },
            selectedItem = remember(uiState.activeVoiceModelId, filteredModels) {
                filteredModels.find { it.id == uiState.activeVoiceModelId } ?: filteredModels.firstOrNull()
            },
            itemLabel = { "${it.label} (${it.sizeDescription})" },
            modelIdProvider = { it.id },
            onItemSelected = { model, isDownloaded ->
                val code = model.langCode ?: uiState.voiceLanguage
                onModelSelected(model, isDownloaded, code)
            },
            onDownloadRequest = { model ->
                val code = model.langCode ?: uiState.voiceLanguage
                onDownloadModel(model.id, engineKey, code)
            },
            onDeleteRequest = { model -> onDeleteRequest(model) },
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
