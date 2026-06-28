package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import kotlinx.coroutines.launch

sealed class PipedTestStatus {
    object Idle : PipedTestStatus()
    object Testing : PipedTestStatus()
    data class Online(val url: String) : PipedTestStatus()
    data class Offline(val url: String) : PipedTestStatus()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsTab(
    languageManager: LanguageManager,
    settingsRepo: SettingsRepository,
    appStateManager: AppStateManager
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // REACTIVE STATE (observed first)
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    
    // Extract models from SSOT map
    val whisperModels = remember(uiState.availableModels) { uiState.availableModels["stt_whisper"] ?: emptyList() }
    val voskModels = remember(uiState.availableModels) { uiState.availableModels["wake_vosk"] ?: emptyList() }
    val nluModels = remember(uiState.availableModels) { uiState.availableModels["nlu_llm"] ?: emptyList() }

    // Manage own state, synchronized with uiState
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey ?: "") }
    var geminiApiKey by remember(uiState.geminiApiKey) { mutableStateOf(uiState.geminiApiKey ?: "") }
    var modelRepoUrl by remember { mutableStateOf(settingsRepo.getSettingsSnapshot().modelRepoBaseUrl) }
    var pipedApiUrl by remember { mutableStateOf(settingsRepo.getPipedApiUrlSync() ?: "") }
    var pipedRegion by remember { mutableStateOf(settingsRepo.getPipedRegionSync() ?: "") }
    var pipedTestStatus by remember { mutableStateOf<PipedTestStatus>(PipedTestStatus.Idle) }
    var selectedLanguage by remember(uiState.voiceLanguage) { mutableStateOf(uiState.voiceLanguage) }
    var offlineFallbackTimeout by remember(uiState.refreshTrigger) { mutableIntStateOf(settingsRepo.getSettingsSnapshot().offlineFallbackTimeout) }

    var expanded by remember { mutableStateOf(false) }
    val languages = languageManager.getAvailableLanguages()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { focusManager.clearFocus() }), // Global "unfocus" on tap
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = languageManager.getString("app_settings_section"), style = MaterialTheme.typography.titleMedium)

        // API KEY with masking
        var isApiFocused by remember { mutableStateOf(false) }
        TextField(
            value = apiKey,
            onValueChange = { 
                apiKey = it
                appStateManager.setApiKey(it)
            },
            label = { Text(languageManager.getString("api_key")) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isApiFocused = it.isFocused },
            visualTransformation = if (isApiFocused) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = !isApiFocused,
            maxLines = if (isApiFocused) 5 else 1,
            colors = if (!isApiFocused) TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Color.Transparent
            ) else TextFieldDefaults.colors()
        )

        // GEMINI API KEY with masking
        var isGeminiKeyFocused by remember { mutableStateOf(false) }
        TextField(
            value = geminiApiKey,
            onValueChange = {
                geminiApiKey = it
                appStateManager.setGeminiApiKey(it)
            },
            label = { Text(languageManager.getString("gemini_api_key")) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isGeminiKeyFocused = it.isFocused },
            visualTransformation = if (isGeminiKeyFocused) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = !isGeminiKeyFocused,
            maxLines = if (isGeminiKeyFocused) 5 else 1,
            colors = if (!isGeminiKeyFocused) TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Color.Transparent
            ) else TextFieldDefaults.colors()
        )

        // Repository Base URL with gray-out logic
        var isRepoFocused by remember { mutableStateOf(false) }
        TextField(
            value = modelRepoUrl,
            onValueChange = { 
                modelRepoUrl = it
                kotlinx.coroutines.runBlocking { settingsRepo.setModelRepoBaseUrl(it) }
            },
            label = { Text(languageManager.getString("model_repository_url")) },
            placeholder = { Text(languageManager.getString("repository_url_placeholder")) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isRepoFocused = it.isFocused },
            trailingIcon = {
                IconButton(onClick = {
                    scope.launch {
                        val success = RemoteModelRegistry.fetchJson(settingsRepo, force = true)
                        if (success) {
                            appStateManager.refreshAll()
                        }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync JSON")
                }
            },
            colors = if (!isRepoFocused) TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Color.Transparent
            ) else TextFieldDefaults.colors()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(text = languageManager.getString("media_services_section"), style = MaterialTheme.typography.titleMedium)

        // Piped API Instance dropdown
        val pipedInstances = com.voxcommander.app.domain.intent.handler.PipedSearchHelper.PIPED_INSTANCES
        var pipedInstanceExpanded by remember { mutableStateOf(false) }
        val pipedInstanceLabel = if (pipedApiUrl.isBlank()) {
            languageManager.getString("piped_instance_custom")
        } else if (pipedApiUrl in pipedInstances) {
            pipedApiUrl
        } else {
            "$pipedApiUrl (${languageManager.getString("piped_instance_custom")})"
        }

        // Test Piped instance connectivity when URL changes
        LaunchedEffect(pipedApiUrl) {
            if (pipedApiUrl.isBlank()) {
                pipedTestStatus = PipedTestStatus.Idle
            } else {
                pipedTestStatus = PipedTestStatus.Testing
                val ok = com.voxcommander.app.domain.intent.handler.PipedSearchHelper.testInstance(pipedApiUrl)
                pipedTestStatus = if (ok) PipedTestStatus.Online(pipedApiUrl) else PipedTestStatus.Offline(pipedApiUrl)
            }
        }

        ExposedDropdownMenuBox(
            expanded = pipedInstanceExpanded,
            onExpandedChange = { pipedInstanceExpanded = !pipedInstanceExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = pipedInstanceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(languageManager.getString("piped_api_url")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pipedInstanceExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = pipedInstanceExpanded, onDismissRequest = { pipedInstanceExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(languageManager.getString("piped_instance_custom")) },
                    onClick = {
                        pipedApiUrl = ""
                        scope.launch { settingsRepo.setPipedApiUrl(null) }
                        com.voxcommander.app.domain.intent.handler.PipedSearchHelper.setPipedApiUrl(null)
                        pipedInstanceExpanded = false
                    }
                )
                pipedInstances.forEachIndexed { index, instance ->
                    DropdownMenuItem(
                        text = { Text(if (index == 0) "$instance (Default)" else instance) },
                        onClick = {
                            pipedApiUrl = instance
                            scope.launch { settingsRepo.setPipedApiUrl(instance) }
                            com.voxcommander.app.domain.intent.handler.PipedSearchHelper.setPipedApiUrl(instance)
                            pipedInstanceExpanded = false
                        }
                    )
                }
            }
        }

        // Custom Piped URL text field (shown when custom is selected)
        if (pipedApiUrl.isBlank() || pipedApiUrl !in pipedInstances) {
            var isPipedFocused by remember { mutableStateOf(false) }
            TextField(
                value = pipedApiUrl,
                onValueChange = {
                    pipedApiUrl = it
                    scope.launch { settingsRepo.setPipedApiUrl(it.ifBlank { null }) }
                    com.voxcommander.app.domain.intent.handler.PipedSearchHelper.setPipedApiUrl(it.ifBlank { null })
                },
                label = { Text(languageManager.getString("piped_custom_url")) },
                placeholder = { Text(languageManager.getString("piped_api_url_placeholder")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isPipedFocused = it.isFocused },
                colors = if (!isPipedFocused) TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = Color.Transparent
                ) else TextFieldDefaults.colors()
            )
        }

        // Piped connection status indicator
        when (val status = pipedTestStatus) {
            is PipedTestStatus.Testing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = languageManager.getString("piped_testing"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is PipedTestStatus.Online -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(text = "\u2705", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = languageManager.getString("piped_online"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is PipedTestStatus.Offline -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(text = "\u274C", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = languageManager.getString("piped_offline"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }

        // Piped Region dropdown
        val pipedRegions = com.voxcommander.app.domain.intent.handler.PipedSearchHelper.PIPED_REGIONS
        var pipedRegionExpanded by remember { mutableStateOf(false) }
        val pipedRegionLabel = pipedRegions.find { it.first == (pipedRegion.ifBlank { null }) }?.second
            ?: pipedRegions.first().second

        ExposedDropdownMenuBox(
            expanded = pipedRegionExpanded,
            onExpandedChange = { pipedRegionExpanded = !pipedRegionExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = pipedRegionLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(languageManager.getString("piped_region")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pipedRegionExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = pipedRegionExpanded, onDismissRequest = { pipedRegionExpanded = false }) {
                pipedRegions.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(if (code == null) name else "$name ($code)") },
                        onClick = {
                            pipedRegion = code ?: ""
                            scope.launch { settingsRepo.setPipedRegion(code) }
                            com.voxcommander.app.domain.intent.handler.PipedSearchHelper.setPipedRegion(code)
                            pipedRegionExpanded = false
                        }
                    )
                }
            }
        }

        Text(text = languageManager.getString("language"), style = MaterialTheme.typography.labelLarge)

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLanguage.uppercase())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth()) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.uppercase()) },
                        onClick = {
                            selectedLanguage = lang
                            languageManager.loadLanguage(lang)
                            appStateManager.setAppLanguage(lang)
                            expanded = false
                        }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(text = languageManager.getString("offline_fallback_section"), style = MaterialTheme.typography.titleMedium)

        // Compact Timeout Row
        val timeoutOptions = listOf(
            5 to "5 s", 10 to "10 s", 20 to "20 s", 35 to "35 s", 50 to "50 s",
            60 to "1 min", 300 to "5 min", 600 to "10 min"
        )
        var timeoutExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = languageManager.getString("timeout_label"), style = MaterialTheme.typography.labelLarge)
            
            Box {
                OutlinedButton(
                    onClick = { timeoutExpanded = true },
                    modifier = Modifier.widthIn(min = 120.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    val currentLabel = timeoutOptions.find { it.first == offlineFallbackTimeout }?.second ?: "$offlineFallbackTimeout s"
                    Text(currentLabel)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = timeoutExpanded,
                    onDismissRequest = { timeoutExpanded = false }
                ) {
                    timeoutOptions.forEach { (seconds, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                offlineFallbackTimeout = seconds
                                appStateManager.setOfflineFallbackTimeout(seconds)
                                timeoutExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // --- VOICE FALLBACK INFO (Compact) ---
        if (uiState.defaultVoiceFallbackProcessor != null && uiState.defaultVoiceFallbackModel != null) {
            val allVoiceModels = (uiState.availableModels["stt_whisper"] ?: emptyList()) + 
                                (uiState.availableModels["wake_vosk"] ?: emptyList()) +
                                (uiState.availableModels["stt_google"] ?: emptyList())
                                
            val voiceModelLabel = allVoiceModels.find { it.id == uiState.defaultVoiceFallbackModel }?.label ?: uiState.defaultVoiceFallbackModel
            Text(
                text = "Voice: ${uiState.defaultVoiceFallbackProcessor} ($voiceModelLabel)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // --- INTENT FALLBACK INFO (Compact) ---
        if (uiState.defaultIntentFallbackProcessor != null && uiState.defaultIntentFallbackModel != null) {
            val intentModelLabel = nluModels.find { it.id == uiState.defaultIntentFallbackModel }?.label ?: uiState.defaultIntentFallbackModel
            Text(
                text = "Intent: ${uiState.defaultIntentFallbackProcessor} ($intentModelLabel)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
