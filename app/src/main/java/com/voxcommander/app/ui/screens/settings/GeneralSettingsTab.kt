package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.engine.whisper.WhisperModelRegistry

object GeneralSettingsTabConfig {
    const val SHOW_SAVE_BUTTON = true
}

@Composable
fun GeneralSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager
) {
    // Manage own state
    var apiKey by remember { mutableStateOf(settingsManager.getApiKey() ?: "") }
    var selectedLanguage by remember { mutableStateOf(settingsManager.getLanguage()) }
    var offlineFallbackTimeout by remember { mutableStateOf(settingsManager.getOfflineFallbackTimeout()) }

    var expanded by remember { mutableStateOf(false) }
    val languages = languageManager.getAvailableLanguages()

    Text(text = languageManager.getString("app_settings_section"), style = MaterialTheme.typography.titleMedium)

    TextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text(languageManager.getString("api_key")) },
        modifier = Modifier.fillMaxWidth()
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

    HorizontalDivider()

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

    Spacer(modifier = Modifier.height(8.dp))

    // Default Offline Model (simple text - shows current default tuple)
    Text(text = languageManager.getString("default_offline_model"), style = MaterialTheme.typography.labelLarge)
    val whisperModels = WhisperModelRegistry.models
    val defaultProcessor = settingsManager.getDefaultOfflineFallbackProcessor()
    val defaultModel = settingsManager.getDefaultOfflineFallbackModel()
    val selectedModel = whisperModels.find { it.id == defaultModel }
    Text(
        text = if (defaultProcessor != null && defaultModel != null) {
            "$defaultProcessor: ${selectedModel?.let { "${it.label} (${it.sizeDescription})" } ?: defaultModel}"
        } else {
            "None"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}
