package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import kotlinx.coroutines.launch

object GeneralSettingsTabConfig {
    const val SHOW_SAVE_BUTTON = true
}

@Composable
fun GeneralSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: com.voxcommander.app.state.AppStateManager
) {
    val scope = rememberCoroutineScope()
    
    // Manage own state
    var apiKey by remember { mutableStateOf(settingsManager.getApiKey() ?: "") }
    var modelRepoUrl by remember { mutableStateOf(settingsManager.getModelRepoBaseUrl()) }
    var selectedLanguage by remember { mutableStateOf(settingsManager.getLanguage()) }
    var offlineFallbackTimeout by remember { mutableStateOf(settingsManager.getOfflineFallbackTimeout()) }

    // REACTIVE FALLBACK INFO (re-renders when appStateManager.refreshAll is called)
    val refreshTrigger by appStateManager.refreshTrigger.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    val languages = languageManager.getAvailableLanguages()

    Text(text = languageManager.getString("app_settings_section"), style = MaterialTheme.typography.titleMedium)

    TextField(
        value = apiKey,
        onValueChange = { apiKey = it; settingsManager.saveApiKey(it) },
        label = { Text(languageManager.getString("api_key")) },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    TextField(
        value = modelRepoUrl,
        onValueChange = { 
            modelRepoUrl = it
            settingsManager.saveModelRepoBaseUrl(it)
        },
        label = { Text("Model Repository Base URL") },
        placeholder = { Text("https://github.com/user/repo") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { 
                scope.launch { 
                    com.voxcommander.app.data.remote.RemoteModelRegistry.fetchJson(settingsManager, force = true)
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync JSON")
            }
        }
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
                        settingsManager.saveLanguage(lang)
                        expanded = false
                    }
                )
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

    Text(text = languageManager.getString("offline_fallback_section"), style = MaterialTheme.typography.titleMedium)

    // Offline Fallback Timeout
    Text(text = languageManager.getString("offline_fallback_timeout"), style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = offlineFallbackTimeout.toString(),
        onValueChange = { newValue ->
            newValue.toIntOrNull()?.let { if (it > 0) {
                offlineFallbackTimeout = it
                settingsManager.saveOfflineFallbackTimeout(it)
            } }
        },
        label = { Text(languageManager.getString("timeout_seconds")) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // --- VOICE FALLBACK INFO ---
    Text(text = "Voice Fallback: ${languageManager.getString("default_offline_model")}", style = MaterialTheme.typography.labelLarge)

    val defaultVoiceProcessor = remember(refreshTrigger) { settingsManager.getDefaultVoiceFallbackProcessor() }
    val defaultVoiceModel = remember(refreshTrigger) { settingsManager.getDefaultVoiceFallbackModel() }
    Text(
        text = if (defaultVoiceProcessor != null && defaultVoiceModel != null) {
            "$defaultVoiceProcessor: $defaultVoiceModel"
        } else {
            "" // Empty as requested
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary
    )

    Spacer(modifier = Modifier.height(8.dp))

    // --- INTENT FALLBACK INFO ---
    Text(text = "Intent Fallback: ${languageManager.getString("default_offline_model")}", style = MaterialTheme.typography.labelLarge)
    val defaultIntentProcessor = remember(refreshTrigger) { settingsManager.getDefaultIntentFallbackProcessor() }
    val defaultIntentModel = remember(refreshTrigger) { settingsManager.getDefaultIntentFallbackModel() }
    Text(
        text = if (defaultIntentProcessor != null && defaultIntentModel != null) {
            "$defaultIntentProcessor: $defaultIntentModel"
        } else {
            "" // Empty as requested
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}
