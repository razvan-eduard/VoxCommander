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
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.intent.registry.AppRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.AppSelectorDropdown
import com.voxcommander.app.ui.components.VoiceInputTextField
import com.voxcommander.app.utils.RegexGenerator
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RulesManagerContent(
    languageManager: LanguageManager,
    settingsRepo: SettingsRepository,
    appStateManager: AppStateManager,
    fastMapDao: FastMapDao,
    onSaveAndClose: () -> Unit,
    onChangesDetected: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rules by fastMapDao.getAllRules().collectAsStateWithLifecycle(initialValue = emptyList())

    // Voice input text (shared between trigger and query)
    var voiceInputText by remember { mutableStateOf("") }

    // Token state — shared tokens from voice input
    var allTokens by remember { mutableStateOf<List<String>>(emptyList()) }
    val triggerSelectedIndices = remember { mutableStateListOf<Int>() }
    val querySelectedIndices = remember { mutableStateListOf<Int>() }

    // App + Intent selection
    var selectedTargetPackage by remember { mutableStateOf<String?>(null) }
    var selectedIntentIndex by remember { mutableStateOf(-1) }
    var availableIntents by remember { mutableStateOf<List<AppRegistry.KnownIntents.IntentOption>>(emptyList()) }
    var lazyQuery by remember { mutableStateOf(false) }

    // Edit state
    var editingRuleId by remember { mutableStateOf<Long?>(null) }

    // Voice input handler — splits into tokens
    val onVoiceResult: (String) -> Unit = { spokenText ->
        if (spokenText.isNotBlank()) {
            voiceInputText = spokenText
            allTokens = RegexGenerator.splitIntoTokens(spokenText)
            triggerSelectedIndices.clear()
            querySelectedIndices.clear()
        }
    }

    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    val voiceLanguage = uiState.voiceLanguage
    val voiceProcessor = uiState.voiceProcessor

    val isDefaultModelOnDevice = remember(voiceProcessor, voiceLanguage) {
        when (voiceProcessor) {
            Strings.Processors.WHISPER_VULKAN -> {
                val modelId = uiState.activeVoiceModelId
                modelId != null && uiState.isModelDownloaded(modelId)
            }
            Strings.Processors.GOOGLE, Strings.Processors.WHISPER_API -> true
            else -> {
                if (com.voxcommander.app.data.remote.RemoteModelRegistry.isZipEngine(voiceProcessor)) {
                    val customPath = uiState.customVoskModelPaths[voiceLanguage]
                    if (!customPath.isNullOrBlank()) {
                        File(customPath).exists()
                    } else {
                        val rootDir = context.getExternalFilesDir(null)
                        val modelDir = rootDir?.listFiles()?.find { it.isDirectory && it.name.startsWith("vosk-model-") && it.name.contains(voiceLanguage, ignoreCase = true) }
                        modelDir != null && modelDir.exists()
                    }
                } else {
                    val modelId = uiState.activeVoiceModelId
                    modelId != null && uiState.isModelDownloaded(modelId)
                }
            }
        }
    }

    // Update available intents when app changes
    LaunchedEffect(selectedTargetPackage) {
        if (selectedTargetPackage != null) {
            availableIntents = AppRegistry.KnownIntents.probeSupported(context, selectedTargetPackage!!)
            selectedIntentIndex = if (availableIntents.isNotEmpty()) 0 else -1
        } else {
            availableIntents = emptyList()
            selectedIntentIndex = -1
        }
    }

    fun resetForm() {
        voiceInputText = ""
        allTokens = emptyList()
        triggerSelectedIndices.clear()
        querySelectedIndices.clear()
        selectedTargetPackage = null
        selectedIntentIndex = -1
        availableIntents = emptyList()
        lazyQuery = false
        editingRuleId = null
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
            // --- ADD/EDIT RULE FORM ---
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
                                text = if (editingRuleId == null) languageManager.getString("add_new_fast_trigger") else languageManager.getString("edit_fast_trigger"),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (editingRuleId == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                            if (editingRuleId != null) {
                                TextButton(onClick = { resetForm() }) {
                                    Text(languageManager.getString("cancel_edit"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Voice input
                        VoiceInputTextField(
                            value = voiceInputText,
                            onValueChange = { newValue ->
                                voiceInputText = newValue
                                if (newValue.isNotBlank()) {
                                    allTokens = RegexGenerator.splitIntoTokens(newValue)
                                    triggerSelectedIndices.clear()
                                    querySelectedIndices.clear()
                                } else {
                                    allTokens = emptyList()
                                    triggerSelectedIndices.clear()
                                    querySelectedIndices.clear()
                                }
                            },
                            label = { Text(languageManager.getString("voice_input_label")) },
                            languageManager = languageManager,
                            voiceLanguage = voiceLanguage,
                            voiceProcessor = voiceProcessor,
                            isModelOnDevice = isDefaultModelOnDevice,
                            onVoiceResult = onVoiceResult
                        )

                        // --- DUAL TOKEN SELECTOR ---
                        if (allTokens.isNotEmpty()) {
                            // Trigger tokens
                            TokenSelectorSection(
                                title = languageManager.getString("trigger_section_title"),
                                tokens = allTokens,
                                selectedIndices = triggerSelectedIndices,
                                greyedIndices = querySelectedIndices,
                                onToggle = { index ->
                                    if (triggerSelectedIndices.contains(index)) {
                                        triggerSelectedIndices.remove(index)
                                    } else {
                                        triggerSelectedIndices.add(index)
                                    }
                                },
                                languageManager = languageManager
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Lazy query toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = lazyQuery,
                                    onCheckedChange = {
                                        lazyQuery = it
                                        if (it) querySelectedIndices.clear()
                                    }
                                )
                                Text(
                                    text = languageManager.getString("lazy_processing_label"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Query tokens (disabled when lazy)
                            TokenSelectorSection(
                                title = if (lazyQuery) languageManager.getString("query_auto_title") else languageManager.getString("query_manual_title"),
                                tokens = allTokens,
                                selectedIndices = querySelectedIndices,
                                greyedIndices = triggerSelectedIndices,
                                onToggle = { index ->
                                    if (lazyQuery) return@TokenSelectorSection
                                    if (querySelectedIndices.contains(index)) {
                                        querySelectedIndices.remove(index)
                                    } else {
                                        querySelectedIndices.add(index)
                                    }
                                },
                                languageManager = languageManager
                            )
                        }

                        // --- APP SELECTOR ---
                        AppSelectorDropdown(
                            selectedPackage = selectedTargetPackage,
                            onAppSelected = { app ->
                                selectedTargetPackage = app?.packageName
                            },
                            domain = null,
                            label = languageManager.getString("target_app_label"),
                            modifier = Modifier.fillMaxWidth(),
                            allowNone = false,
                            languageManager = languageManager
                        )

                        // --- INTENT DROPDOWN ---
                        if (availableIntents.isNotEmpty()) {
                            var intentExpanded by remember { mutableStateOf(false) }
                            val selectedOption = availableIntents.getOrNull(selectedIntentIndex) ?: availableIntents.first()
                            val selectedIntentLabel = selectedOption.variant.label

                            ExposedDropdownMenuBox(
                                expanded = intentExpanded,
                                onExpandedChange = { intentExpanded = !intentExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedIntentLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(languageManager.getString("intent_action_label")) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intentExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = intentExpanded, onDismissRequest = { intentExpanded = false }) {
                                    availableIntents.forEachIndexed { idx, option ->
                                        DropdownMenuItem(
                                            text = { Text(option.variant.label) },
                                            onClick = {
                                                selectedIntentIndex = idx
                                                intentExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // --- SAVE BUTTON ---
                        val canSave = (triggerSelectedIndices.isNotEmpty() || querySelectedIndices.isNotEmpty() || lazyQuery) &&
                                      selectedTargetPackage != null

                        Button(
                            onClick = {
                                scope.launch {
                                    val triggerWords = triggerSelectedIndices.sorted().map { allTokens[it] }
                                    val queryWords = querySelectedIndices.sorted().map { allTokens[it] }
                                    val selectedOption = availableIntents.getOrNull(selectedIntentIndex)
                                    val rule = FastMapRule(
                                        id = editingRuleId ?: 0,
                                        allWords = allTokens,
                                        triggerWords = triggerWords,
                                        queryWords = queryWords,
                                        targetPackage = selectedTargetPackage ?: "",
                                        intentAction = selectedOption?.action ?: "",
                                        uriTemplate = selectedOption?.variant?.uriTemplate,
                                        lazyQuery = lazyQuery
                                    )
                                    fastMapDao.insertRule(rule)
                                    resetForm()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave
                        ) {
                            Text(if (editingRuleId == null) languageManager.getString("add_rule_button") else languageManager.getString("update_rule"))
                        }
                    }
                }
            }

            // --- LIST OF EXISTING RULES ---
            if (rules.isNotEmpty()) {
                item {
                    Text(text = languageManager.getString("active_fast_triggers"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }

                items(rules) { rule ->
                    RuleItem(
                        rule = rule,
                        isEditing = editingRuleId == rule.id,
                        onClick = {
                            editingRuleId = rule.id
                            allTokens = rule.allWords
                            triggerSelectedIndices.clear()
                            querySelectedIndices.clear()
                            // Reconstruct selected indices from words
                            rule.triggerWords.forEach { word ->
                                val idx = allTokens.indexOf(word)
                                if (idx >= 0) triggerSelectedIndices.add(idx)
                            }
                            rule.queryWords.forEach { word ->
                                val idx = allTokens.indexOf(word)
                                if (idx >= 0) querySelectedIndices.add(idx)
                            }
                            selectedTargetPackage = rule.targetPackage.ifBlank { null }
                            lazyQuery = rule.lazyQuery
                            // Re-probe to get available intents, then find matching index
                            if (!rule.targetPackage.isNullOrBlank()) {
                                availableIntents = AppRegistry.KnownIntents.probeSupported(context, rule.targetPackage)
                                selectedIntentIndex = availableIntents.indexOfFirst {
                                    it.action == rule.intentAction && it.variant.uriTemplate == rule.uriTemplate
                                }.let { if (it < 0) 0 else it }
                            } else {
                                availableIntents = emptyList()
                                selectedIntentIndex = -1
                            }
                            voiceInputText = rule.allWords.joinToString(" ")
                        },
                        onDelete = {
                            scope.launch {
                                fastMapDao.deleteRule(rule)
                                if (editingRuleId == rule.id) {
                                    resetForm()
                                }
                            }
                        },
                        languageManager = languageManager
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
fun TokenSelectorSection(
    title: String,
    tokens: List<String>,
    selectedIndices: List<Int>,
    greyedIndices: List<Int>,
    onToggle: (Int) -> Unit,
    languageManager: LanguageManager
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tokens.forEachIndexed { index, token ->
                    val isSelected = selectedIndices.contains(index)
                    val isGreyed = greyedIndices.contains(index)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(index) },
                        label = { Text(token) },
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isGreyed,
                        colors = FilterChipDefaults.filterChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            if (selectedIndices.isEmpty()) {
                Text(
                    text = languageManager.getString("tap_words_hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RuleItem(rule: FastMapRule, isEditing: Boolean, onClick: () -> Unit, onDelete: () -> Unit, languageManager: LanguageManager) {
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
                val triggerText = if (rule.triggerWords.isNotEmpty()) rule.triggerWords.joinToString(" ") else languageManager.getString("no_trigger")
                Text(text = languageManager.getString("trigger_prefix").format(triggerText), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                val detail = buildString {
                    if (rule.queryWords.isNotEmpty()) append(languageManager.getString("query_prefix").format(rule.queryWords.joinToString(" ")))
                    if (rule.targetPackage.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append(languageManager.getString("app_prefix").format(rule.targetPackage))
                    }
                    if (rule.intentAction.isNotBlank()) append(" | Intent: ${rule.intentAction}")
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
