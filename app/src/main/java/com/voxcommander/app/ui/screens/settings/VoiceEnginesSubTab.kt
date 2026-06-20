package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    onDownloadWhisperModel: (String, String) -> Unit,
    onDownloadVoskModel: (String, String, String) -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    downloadedColor: Color,
    onCancelDownload: () -> Unit,
    onCleanupRequest: () -> Unit,
    onClearDefaultFallback: () -> Unit,
    onDeleteRequest: (AppModel) -> Unit, 
    refreshTrigger: Int = 0
) {
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
                DropdownMenuItem(
                    text = { Text(proc.replace("_", " "), color = if (enabled) LocalContentColor.current else Color.Gray) },
                    onClick = { if (enabled) { onProcessorSelected(proc); processorExpanded = false } },
                    enabled = enabled
                )
            }
        }
    }

    HorizontalDivider()

    // 2. Language Selection (Google/API)
    if (voiceProcessor == Strings.Processors.GOOGLE || voiceProcessor == Strings.Processors.WHISPER_API) {
        val languages = listOf(
            "ro" to languageManager.getString("voice_language_ro"),
            "en" to languageManager.getString("voice_language_en"),
            "de" to languageManager.getString("voice_language_de"),
            "fr" to languageManager.getString("voice_language_fr"),
            "es" to languageManager.getString("voice_language_es"),
            "it" to languageManager.getString("voice_language_it")
        )

        var showLanguageSheet by remember { mutableStateOf(false) }
        val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val languageGroups = listOf(DropdownGroup("AVAILABLE LANGUAGES", languages))
        val selectedLangPair = languages.find { it.first == voiceLanguage }

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

    // 3. Engine Specific Sections
    when (voiceProcessor) {
        Strings.Processors.WHISPER_CPP, 
        Strings.Processors.WHISPER_VULKAN, 
        Strings.Processors.WHISPER_NEON -> {
            EngineModelSection(
                title = languageManager.getString("whisper_model_select"),
                languageManager = languageManager,
                settingsManager = settingsManager,
                groups = remember {
                    listOf(
                        DropdownGroup(languageManager.getString("multilingual_models_header"), whisperModels.filter { it.isMultilingual }),
                        DropdownGroup(languageManager.getString("english_only_models_header"), whisperModels.filter { !it.isMultilingual })
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
                currentProcessor = voiceProcessor,
                refreshTrigger = refreshTrigger
            )
        }
        Strings.Processors.VOSK -> {
            EngineModelSection(
                title = languageManager.getString("vosk_model_select"),
                languageManager = languageManager,
                settingsManager = settingsManager,
                groups = remember(voskGroups) {
                    voskGroups.map { group ->
                        DropdownGroup(group.language.uppercase(), group.models)
                    }
                },
                selectedItem = selectedVoskModel,
                itemLabel = { if (it.size != null) "${it.name} (${it.size})" else it.name },
                modelIdProvider = { it.name },
                onItemSelected = { model, isDownloaded ->
                    val group = voskGroups.find { it.models.contains(model) }
                    val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                    onVoskModelSelected(model, isDownloaded, code)
                },
                onDownloadRequest = { model ->
                    val group = voskGroups.find { it.models.contains(model) }
                    val code = if (group?.language?.contains("Romanian") == true) "ro" else "en"
                    onDownloadVoskModel(code, model.url, model.id)
                },
                onDeleteRequest = onDeleteRequest,
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                currentProcessor = voiceProcessor,
                refreshTrigger = refreshTrigger
            )
        }
    }

    // 4. Global Cleanup
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onCleanupRequest, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
        Text(languageManager.getString("delete_unused_models"))
    }
    Button(onClick = onClearDefaultFallback, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Text(languageManager.getString("clear_default_fallback"))
    }
}
