package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.LogLevel
import com.voxcommander.app.utils.LoggingFlags

@Composable
fun AdvancedSettingsTab(
    languageManager: LanguageManager,
    settingsManager: SettingsManager,
    appStateManager: AppStateManager,
    onCleanupRequest: () -> Unit,
    onClearDefaultFallback: () -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current

    // Manage own state with logging flags
    var loggingFlags by remember {
        mutableStateOf(
            LoggingFlags.fromLogLevel(
                when (settingsManager.getLogLevel()) {
                    "NONE" -> LogLevel.NONE
                    "TOAST_ONLY" -> LogLevel.TOAST_ONLY
                    "LOGCAT_ONLY" -> LogLevel.LOGCAT_ONLY
                    else -> LogLevel.TOAST_AND_LOGCAT
                }
            )
        )
    }
    var verboseLoggingEnabled by remember(loggingFlags.logcatEnabled) {
        mutableStateOf(
            if (loggingFlags.logcatEnabled) {
                settingsManager.isVerboseLoggingEnabled()
            } else {
                false
            }
        )
    }

    // Reset verbose logging when logcat is not enabled, restore it when enabled
    LaunchedEffect(loggingFlags.logcatEnabled) {
        if (!loggingFlags.logcatEnabled) {
            verboseLoggingEnabled = false
            settingsManager.saveVerboseLoggingEnabled(false)
            Logger.setVerboseLoggingEnabled(false)
            onVerboseLoggingChange(false)
        } else {
            // Restore verbose logging setting from SettingsManager when logcat is enabled
            val savedVerbose = settingsManager.isVerboseLoggingEnabled()
            verboseLoggingEnabled = savedVerbose
            Logger.setVerboseLoggingEnabled(savedVerbose)
            onVerboseLoggingChange(savedVerbose)
        }
    }

    // Notify parent when verbose logging changes
    LaunchedEffect(verboseLoggingEnabled) {
        onVerboseLoggingChange(verboseLoggingEnabled)
    }

    // Keep subscription active
    val _uiState by appStateManager.uiState.collectAsStateWithLifecycle()

    // Initialize Logger
    LaunchedEffect(Unit) {
        Logger.initialize(context, LoggingFlags.toLogLevel(loggingFlags))
        Logger.setLoggingFlags(loggingFlags)
        Logger.setVerboseLoggingEnabled(verboseLoggingEnabled)
    }

    Text(text = languageManager.getString("advanced_settings_section"), style = MaterialTheme.typography.titleMedium)

    Spacer(modifier = Modifier.height(8.dp))

    // Logging Level Checkboxes
    Text(text = languageManager.getString("logging_level"), style = MaterialTheme.typography.labelLarge)

    Spacer(modifier = Modifier.height(8.dp))

    // Toast checkbox
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageManager.getString("toast_label"))
        Checkbox(
            checked = loggingFlags.toastEnabled,
            onCheckedChange = { enabled ->
                loggingFlags = loggingFlags.copy(toastEnabled = enabled)
                val newLogLevel = LoggingFlags.toLogLevel(loggingFlags)
                settingsManager.saveLogLevel(newLogLevel.name)
                Logger.setLoggingFlags(loggingFlags)
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Logcat checkbox
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageManager.getString("logcat_label"))
        Checkbox(
            checked = loggingFlags.logcatEnabled,
            onCheckedChange = { enabled ->
                loggingFlags = loggingFlags.copy(logcatEnabled = enabled)
                val newLogLevel = LoggingFlags.toLogLevel(loggingFlags)
                settingsManager.saveLogLevel(newLogLevel.name)
                Logger.setLoggingFlags(loggingFlags)
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Verbose Logging Switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            languageManager.getString("verbose_logging"),
            color = if (loggingFlags.logcatEnabled) LocalContentColor.current else Color.Gray
        )
        Switch(
            checked = verboseLoggingEnabled,
            onCheckedChange = { enabled ->
                verboseLoggingEnabled = enabled
                settingsManager.saveVerboseLoggingEnabled(enabled)
                Logger.setVerboseLoggingEnabled(enabled)
            },
            enabled = loggingFlags.logcatEnabled
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // --- SYSTEM MAINTENANCE ---
    Text(text = languageManager.getString("system_maintenance"), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCleanupRequest, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(languageManager.getString("delete_unused_models"))
            }
            Button(onClick = onClearDefaultFallback, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text(languageManager.getString("clear_default_fallback"))
            }
            Text(text = languageManager.getString("maintenance_warning"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
