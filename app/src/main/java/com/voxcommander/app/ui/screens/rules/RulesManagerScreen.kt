package com.voxcommander.app.ui.screens.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.registry.IntentRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.VoiceInputTextField
import com.voxcommander.app.utils.RegexGenerator
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RulesManagerContent(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    fastMapDao: FastMapDao,
    onSaveAndClose: () -> Unit,
    onChangesDetected: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val rules by fastMapDao.getAllRules().collectAsStateWithLifecycle(initialValue = emptyList())
    
    var triggerPattern by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }

    // Edit State
    var editingRuleId by remember { mutableStateOf<Long?>(null) }

    // Regex Wizard State
    var wizardTokens by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedWizardIndices = remember { mutableStateListOf<Int>() }

    val categories = remember { IntentRegistry.getAllCategories() }
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val actions = remember(selectedCategory) { IntentRegistry.getActionsForCategory(selectedCategory) }
    var selectedAction by remember(selectedCategory) { mutableStateOf(actions[0]) }
    var actionExpanded by remember { mutableStateOf(false) }
    
    // Logic to handle voice input completion for the wizard
    val onVoiceResult: (String) -> Unit = { spokenText ->
        if (spokenText.isNotBlank()) {
            wizardTokens = RegexGenerator.splitIntoTokens(spokenText)
            selectedWizardIndices.clear()
        }
    }

    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    val voiceLanguage = uiState.voiceLanguage
    val voiceProcessor = uiState.voiceProcessor

    // Check if default voice model is on device
    val isDefaultModelOnDevice = remember(voiceProcessor, voiceLanguage) {
        when (voiceProcessor) {
            "WHISPER_CPP", "WHISPER_VULKAN", "WHISPER_NEON" -> {
                settingsManager.isModelDownloaded(uiState.selectedWhisperModelId)
            }
            "VOSK" -> {
                val customPath = uiState.customVoskModelPaths[voiceLanguage]
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
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // --- HEADER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = languageManager.getString("rules_manager_title"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- ADD NEW RULE FORM ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingRuleId == null) "Add New Fast Trigger (L1)" else "Edit Fast Trigger",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (editingRuleId == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                            if (editingRuleId != null) {
                                TextButton(onClick = {
                                    editingRuleId = null
                                    triggerPattern = ""
                                    artist = ""
                                    track = ""
                                    album = ""
                                    destination = ""
                                    wizardTokens = emptyList()
                                    selectedWizardIndices.clear()
                                }) {
                                    Text("Cancel Edit", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        
                        VoiceInputTextField(
                            value = triggerPattern,
                            onValueChange = { newValue -> 
                                triggerPattern = newValue
                                // Populate wizard even when typing manually
                                if (newValue.isNotBlank() && !newValue.contains(".*")) {
                                    wizardTokens = RegexGenerator.splitIntoTokens(newValue)
                                } else if (newValue.isBlank()) {
                                    wizardTokens = emptyList()
                                    selectedWizardIndices.clear()
                                }
                            },
                            label = { Text(languageManager.getString("trigger_pattern")) },
                            languageManager = languageManager,
                            voiceLanguage = voiceLanguage,
                            voiceProcessor = voiceProcessor,
                            isModelOnDevice = isDefaultModelOnDevice,
                            onVoiceResult = onVoiceResult
                        )

                        // --- REGEX WIZARD UI ---
                        if (wizardTokens.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Regex Wizard: Select keywords", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        wizardTokens.forEachIndexed { index, token ->
                                            val isSelected = selectedWizardIndices.contains(index)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    if (isSelected) selectedWizardIndices.remove(index)
                                                    else selectedWizardIndices.add(index)
                                                    
                                                    // Update Regex automatically
                                                    val selectedWords = selectedWizardIndices.sorted().map { wizardTokens[it] }
                                                    triggerPattern = RegexGenerator.fromWords(selectedWords)
                                                },
                                                label = { Text(token) },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                        }
                                    }
                                    
                                    if (selectedWizardIndices.isEmpty()) {
                                        Text(
                                            text = "Tap words in order to build your trigger pattern.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // New Fields based on category
                        if (selectedCategory == "audio") {
                            OutlinedTextField(
                                value = artist,
                                onValueChange = { artist = it },
                                label = { Text("Artist (Static)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = track,
                                onValueChange = { track = it },
                                label = { Text("Track (Static)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (selectedCategory == "maps") {
                            OutlinedTextField(
                                value = destination,
                                onValueChange = { destination = it },
                                label = { Text("Destination (Static)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Category Dropdown
                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = !categoryExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(languageManager.getString("category_label")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                                    categories.forEach { category ->
                                        DropdownMenuItem(text = { Text(category) }, onClick = { selectedCategory = category; categoryExpanded = false })
                                    }
                                }
                            }

                            // Action Dropdown
                            ExposedDropdownMenuBox(
                                expanded = actionExpanded,
                                onExpandedChange = { actionExpanded = !actionExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedAction,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(languageManager.getString("action_label")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                                    actions.forEach { action ->
                                        DropdownMenuItem(text = { Text(action) }, onClick = { selectedAction = action; actionExpanded = false })
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val rule = FastMapRule(
                                        id = editingRuleId ?: 0,
                                        category = selectedCategory,
                                        actionType = selectedAction,
                                        triggerPattern = triggerPattern,
                                        artist = artist.takeIf { it.isNotBlank() },
                                        track = track.takeIf { it.isNotBlank() },
                                        album = album.takeIf { it.isNotBlank() },
                                        destination = destination.takeIf { it.isNotBlank() }
                                    )
                                    fastMapDao.insertRule(rule)
                                    
                                    // Reset form
                                    triggerPattern = ""
                                    artist = ""
                                    track = ""
                                    album = ""
                                    destination = ""
                                    wizardTokens = emptyList()
                                    selectedWizardIndices.clear()
                                    editingRuleId = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = triggerPattern.isNotBlank()
                        ) {
                            Text(if (editingRuleId == null) languageManager.getString("add_rule_button") else "Update Rule")
                        }
                    }
                }
            }

            // --- LIST OF EXISTING RULES ---
            if (rules.isNotEmpty()) {
                item {
                    Text(text = "Active Fast Triggers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                
                items(rules) { rule ->
                    RuleItem(
                        rule = rule,
                        isEditing = editingRuleId == rule.id,
                        onClick = {
                            editingRuleId = rule.id
                            triggerPattern = rule.triggerPattern
                            artist = rule.artist ?: ""
                            track = rule.track ?: ""
                            album = rule.album ?: ""
                            destination = rule.destination ?: ""
                            selectedCategory = categories.find { it == rule.category } ?: categories[0]
                            // selectedAction will be updated by the remember(selectedCategory)
                            wizardTokens = emptyList()
                            selectedWizardIndices.clear()
                        },
                        onDelete = { 
                            scope.launch { 
                                fastMapDao.deleteRule(rule)
                                if (editingRuleId == rule.id) {
                                    editingRuleId = null
                                    triggerPattern = ""
                                    artist = ""
                                    track = ""
                                    album = ""
                                    destination = ""
                                }
                            } 
                        }
                    )
                }
            }
        }

        // CLOSE BUTTON
        TextButton(
            onClick = onSaveAndClose,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(languageManager.getString("ok_button"))
        }
    }
}

@Composable
fun RuleItem(rule: FastMapRule, isEditing: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isEditing) 2.dp else 1.dp, 
            color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rule.triggerPattern, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                val detail = buildString {
                    append("${rule.category} > ${rule.actionType}")
                    if (rule.artist != null) append(" | Artist: ${rule.artist}")
                    if (rule.track != null) append(" | Track: ${rule.track}")
                    if (rule.destination != null) append(" | Dest: ${rule.destination}")
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}
