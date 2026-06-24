package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.intent.interpreter.LlmModelInfo
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.EngineModelSection
import com.voxcommander.app.utils.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentEnginesSubTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    onDownloadLlamaModel: (AppModel) -> Unit,
    onDeleteLlamaModel: (AppModel) -> Unit,
    downloadProgress: Float?,
    downloadingItem: AppModel?,
    onCancelDownload: () -> Unit,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0
) {
    val uiState by appStateManager.uiState.collectAsState()

    // Dynamically build model list from Remote Registry
    val llmModels = remember {
        RemoteModelRegistry.getLlmModels().map {
            LlmModelInfo(
                id = it.id,
                label = it.label,
                url = it.path,
                sizeDescription = "${it.size_mb} MB",
                engineTypeTag = it.engine_type ?: "MEDIAPIPE_GENAI"
            )
        }
    }

    val selectedModel = remember(uiState.selectedLlamaModelId, llmModels) {
        llmModels.find { it.id == uiState.selectedLlamaModelId } ?: llmModels.firstOrNull()
    }

    val nluGroups = remember(llmModels) {
        listOf(
            DropdownGroup("AVAILABLE NLU MODELS", llmModels)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- MASTER TOGGLE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = languageManager.getString("cloud_intelligence_title"), 
                            style = MaterialTheme.typography.bodyLarge, 
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = languageManager.getString("cloud_intelligence_desc"), 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.cloudIntelligenceEnabled,
                        onCheckedChange = { appStateManager.setCloudIntelligenceEnabled(it) }
                    )
                }

                if (uiState.cloudIntelligenceEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.alpha(0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = languageManager.getString("ai_processor_label"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    val aiOptions = listOf(
                        Strings.AiProcessors.OPENAI to "OpenAI (Cloud)",
                        Strings.AiProcessors.NLU_LOCAL to "NLU AI (Local)",
                        Strings.AiProcessors.GEMINI_NATIVE to "Gemini Nano (System)"
                    )

                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val currentLabel = aiOptions.find { it.first == uiState.aiProcessor }?.second ?: "NLU AI (Local)"
                            Text(text = currentLabel)
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            aiOptions.forEach { (id, label) ->
                                val isEnabled = when (id) {
                                    Strings.AiProcessors.GEMINI_NATIVE -> !settingsManager.isGeminiIncompatible()
                                    else -> true
                                }
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = if (isEnabled) label else "$label (Incompatible)",
                                            color = if (isEnabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        ) 
                                    },
                                    onClick = {
                                        if (isEnabled) {
                                            appStateManager.setAiProcessor(id)
                                            expanded = false
                                        }
                                    },
                                    enabled = isEnabled
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- NLU MODEL SELECTION ---
        if (uiState.cloudIntelligenceEnabled && uiState.aiProcessor == Strings.AiProcessors.NLU_LOCAL) {
            EngineModelSection(
                title = "NLU Model Selection",
                languageManager = languageManager,
                settingsManager = settingsManager,
                appStateManager = appStateManager,
                groups = remember(nluGroups, uiState, refreshTrigger) { nluGroups },
                selectedItem = selectedModel,
                itemLabel = { "${it.label} (${it.sizeDescription})" },
                modelIdProvider = { it.id },
                onItemSelected = { model, isDownloaded ->
                    appStateManager.setSelectedLlamaModelId(model.id)
                },
                onDownloadRequest = { model ->
                    appStateManager.setSelectedLlamaModelId(model.id)
                    onDownloadLlamaModel(model)
                },
                onDeleteRequest = onDeleteLlamaModel,
                onCancelDownload = onCancelDownload,
                downloadProgress = downloadProgress,
                downloadingItem = downloadingItem,
                currentProcessor = uiState.aiProcessor,
                fallbackCategory = Strings.FallbackCategories.INTENT,
                onFallbackChanged = onFallbackChanged,
                refreshTrigger = refreshTrigger
            )
            
            if (selectedModel != null) {
                Text(
                    text = "Engine: ${selectedModel.engineTypeTag}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
