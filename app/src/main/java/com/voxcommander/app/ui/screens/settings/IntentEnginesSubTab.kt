package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsRepository
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
    settingsRepo: SettingsRepository,
    appStateManager: AppStateManager,
    onDownloadModel: (String, String, String?) -> Unit,
    onDeleteModel: (String, String) -> Unit,
    downloadProgress: Float?,
    downloadingItem: AppModel?,
    onCancelDownload: () -> Unit,
    onFallbackChanged: () -> Unit = {},
    refreshTrigger: Int = 0
) {
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    
    // 1. Engine key IS the processor — same value from models.json
    val engineKey = uiState.aiProcessor

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

                    val aiOptions = remember(uiState.availableModels) {
                        val list = RemoteModelRegistry.getEngineKeysByType("llm").toMutableList()
                        
                        // Inject Virtual Services
                        if (!list.contains(Strings.AiProcessors.OPENAI)) list.add(Strings.AiProcessors.OPENAI)
                        if (!list.contains(Strings.AiProcessors.GEMINI_NATIVE)) list.add(Strings.AiProcessors.GEMINI_NATIVE)
                        list
                    }

                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(RemoteModelRegistry.getEngineLabel(uiState.aiProcessor, languageManager))
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            aiOptions.forEach { id ->
                                val isEnabled = when (id) {
                                    Strings.AiProcessors.GEMINI_NATIVE -> !settingsRepo.getSettingsSnapshot().geminiIncompatible
                                    else -> true
                                }
                                
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = RemoteModelRegistry.getEngineLabel(id, languageManager) + if (isEnabled) "" else " (Incompatible)",
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
        // Only show if the current engine is a JSON-defined LLM with actual models
        val nluModels = uiState.availableModels[engineKey] ?: emptyList()
        
        if (uiState.cloudIntelligenceEnabled && nluModels.isNotEmpty()) {
            val selectedModel = remember(uiState.activeIntentModelId, nluModels) {
                nluModels.find { it.id == uiState.activeIntentModelId } ?: nluModels.firstOrNull()
            }

            val nluGroups = remember(nluModels) {
                listOf(DropdownGroup(languageManager.getString("available_models_header"), nluModels))
            }

            EngineModelSection(
                title = languageManager.getString("nlu_model_selection_title"),
                languageManager = languageManager,
                settingsRepo = settingsRepo,
                appStateManager = appStateManager,
                groups = remember(nluGroups, uiState, refreshTrigger) { nluGroups },
                selectedItem = selectedModel,
                itemLabel = { "${it.label} (${it.sizeDescription})" },
                modelIdProvider = { it.id },
                onItemSelected = { model, isDownloaded ->
                    appStateManager.setActiveIntentModelId(model.id)
                    appStateManager.saveIntentModelSelection(engineKey, model.id)
                },
                onDownloadRequest = { model ->
                    appStateManager.setActiveIntentModelId(model.id)
                    appStateManager.saveIntentModelSelection(engineKey, model.id)
                    onDownloadModel(model.id, engineKey, null)
                },
                onDeleteRequest = { model -> onDeleteModel(model.id, engineKey) },
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
                    text = languageManager.getString("engine_type_label").format(RemoteModelRegistry.getEngineLabel(engineKey, languageManager)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
