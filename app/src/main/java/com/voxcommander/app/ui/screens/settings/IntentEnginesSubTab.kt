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
import com.voxcommander.app.domain.intent.interpreter.LlamaModelInfo
import com.voxcommander.app.domain.intent.interpreter.LlamaModelRegistry
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
    downloadingItem: Any?,
    onCancelDownload: () -> Unit,
    refreshTrigger: Int
) {
    val cloudEnabled by appStateManager.cloudIntelligenceEnabled.collectAsState()
    val aiProcessor by appStateManager.aiProcessor.collectAsState()
    val selectedLlamaId by appStateManager.selectedLlamaModelId.collectAsState()

    val selectedModel = remember(selectedLlamaId) {
        LlamaModelRegistry.models.find { it.id == selectedLlamaId } ?: LlamaModelRegistry.models.first()
    }

    val llamaGroups = remember {
        listOf(
            DropdownGroup("LLAMA 3.2 MODELS", LlamaModelRegistry.models)
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
                        checked = cloudEnabled,
                        onCheckedChange = { appStateManager.setCloudIntelligenceEnabled(it) }
                    )
                }

                if (cloudEnabled) {
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
                        Strings.AiProcessors.LLAMA_LOCAL to "Llama 3.2 (Local)",
                        Strings.AiProcessors.GEMINI_NATIVE to "Gemini Nano (System)"
                    )

                    aiOptions.forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = aiProcessor == id,
                                onClick = { appStateManager.setAiProcessor(id) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        // --- LLAMA MODEL SELECTION ---
        if (cloudEnabled && aiProcessor == Strings.AiProcessors.LLAMA_LOCAL) {
            EngineModelSection(
                title = "Llama Model Selection",
                languageManager = languageManager,
                settingsManager = settingsManager,
                groups = llamaGroups,
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
                currentProcessor = aiProcessor,
                refreshTrigger = refreshTrigger
            )
            
            Text(
                text = "Requires: ${selectedModel.ramRequirement}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
