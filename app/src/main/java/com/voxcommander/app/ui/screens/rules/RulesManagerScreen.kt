package com.voxcommander.app.ui.screens.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.registry.IntentRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.ui.components.VoiceInputTextField
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesManagerContent(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    fastMapDao: FastMapDao,
    onSaveAndClose: () -> Unit,
    onChangesDetected: (Boolean) -> Unit = {} // Add callback to notify parent
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var triggerPattern by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    val categories = remember { IntentRegistry.getAllCategories() }
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val actions = remember(selectedCategory) { IntentRegistry.getActionsForCategory(selectedCategory) }
    var selectedAction by remember(selectedCategory) { mutableStateOf(actions[0]) }
    var actionExpanded by remember { mutableStateOf(false) }

    // CHANGE DETECTION
    val hasChanges by remember {
        derivedStateOf {
            triggerPattern.isNotBlank() || target.isNotBlank()
        }
    }
    
    // Notify parent whenever hasChanges updates
    LaunchedEffect(hasChanges) {
        onChangesDetected(hasChanges)
    }

    val voiceLanguage = remember { settingsManager.getVoiceLanguage() }
    val voiceProcessor = remember { settingsManager.getVoiceProcessor() }

    // Check if default voice model is on device
    val isDefaultModelOnDevice = remember(voiceProcessor, voiceLanguage) {
        when (voiceProcessor) {
            "WHISPER_CPP", "WHISPER_VULKAN", "WHISPER_NEON" -> {
                val selectedModel = settingsManager.getSelectedWhisperModelId()
                settingsManager.isModelDownloaded(selectedModel)
            }
            "VOSK" -> {
                val customPath = settingsManager.getCustomVoskModelPath(voiceLanguage)
                if (!customPath.isNullOrBlank()) {
                    File(customPath).exists()
                } else {
                    val rootDir = context.getExternalFilesDir(null)
                    val modelDir = rootDir?.listFiles()?.find { it.isDirectory && it.name.startsWith("vosk-model-") && it.name.contains(voiceLanguage, ignoreCase = true) }
                    modelDir != null && modelDir.exists()
                }
            }
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Scrollable form area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = languageManager.getString("rules_manager_title"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            VoiceInputTextField(
                value = triggerPattern,
                onValueChange = { triggerPattern = it },
                label = { Text(languageManager.getString("trigger_pattern")) },
                languageManager = languageManager,
                voiceLanguage = voiceLanguage,
                voiceProcessor = voiceProcessor,
                isModelOnDevice = isDefaultModelOnDevice
            )

            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text(languageManager.getString("target_label")) },
                modifier = Modifier.fillMaxWidth()
            )

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(languageManager.getString("category_label")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Action Dropdown
            ExposedDropdownMenuBox(
                expanded = actionExpanded,
                onExpandedChange = { actionExpanded = !actionExpanded }
            ) {
                OutlinedTextField(
                    value = selectedAction,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(languageManager.getString("action_label")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { actionExpanded = false }
                ) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action) },
                            onClick = {
                                selectedAction = action
                                actionExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // ADD RULE BUTTON (Renamed from Save)
        Button(
            onClick = {
                scope.launch {
                    val rule = FastMapRule(
                        category = selectedCategory,
                        actionType = selectedAction,
                        target = target,
                        triggerPattern = triggerPattern
                    )
                    fastMapDao.insertRule(rule)
                    onSaveAndClose()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = triggerPattern.isNotBlank() && target.isNotBlank()
        ) {
            Text(languageManager.getString("add_rule_button"))
        }
    }
}
