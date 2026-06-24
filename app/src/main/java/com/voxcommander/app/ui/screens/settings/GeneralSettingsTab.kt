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
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import kotlinx.coroutines.launch

@Composable
fun GeneralSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // REACTIVE STATE (observed first)
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    // Manage own state, synchronized with uiState
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey ?: "") }
    var modelRepoUrl by remember { mutableStateOf(settingsManager.getModelRepoBaseUrl()) }
    var selectedLanguage by remember(uiState.voiceLanguage) { mutableStateOf(uiState.voiceLanguage) }
    var offlineFallbackTimeout by remember(uiState.refreshTrigger) { mutableIntStateOf(settingsManager.getOfflineFallbackTimeout()) }

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

        // Repository Base URL with gray-out logic
        var isRepoFocused by remember { mutableStateOf(false) }
        TextField(
            value = modelRepoUrl,
            onValueChange = { 
                modelRepoUrl = it
                settingsManager.saveModelRepoBaseUrl(it)
            },
            label = { Text(languageManager.getString("model_repository_url")) },
            placeholder = { Text(languageManager.getString("repository_url_placeholder")) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isRepoFocused = it.isFocused },
            trailingIcon = {
                IconButton(onClick = { 
                    scope.launch { 
                        val success = com.voxcommander.app.data.remote.RemoteModelRegistry.fetchJson(settingsManager, force = true)
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
            Text(
                text = "Voice: ${uiState.defaultVoiceFallbackProcessor} (${uiState.defaultVoiceFallbackModel})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // --- INTENT FALLBACK INFO (Compact) ---
        if (uiState.defaultIntentFallbackProcessor != null && uiState.defaultIntentFallbackModel != null) {
            Text(
                text = "Intent: ${uiState.defaultIntentFallbackProcessor} (${uiState.defaultIntentFallbackModel})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
