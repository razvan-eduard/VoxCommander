package com.voxcommander.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.domain.voice.WakeWordCalibrator
import com.voxcommander.app.domain.voice.WakeWordProfile
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.components.DropdownGroup
import com.voxcommander.app.ui.components.GroupedDropdownContent
import com.voxcommander.app.ui.components.GroupedDropdownMenu
import com.voxcommander.app.ui.components.VoiceInputTextField
import com.voxcommander.app.ui.screens.main.ListeningScreen
import com.voxcommander.app.utils.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSettingsTab(
    languageManager: LanguageManager,
    settingsRepo: SettingsRepository,
    appStateManager: AppStateManager,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    downloadedColor: Color,
    onDownloadRequest: (AppModel) -> Unit,
    onDeleteRequest: (AppModel) -> Unit,
    onCancelDownload: () -> Unit,
    downloadProgress: Float?,
    downloadingItem: Any? = null,
    refreshTrigger: Int = 0
) {
    val uiState by appStateManager.uiState.collectAsStateWithLifecycle()
    val voskKey = RemoteModelRegistry.getEngineKeyByExtension(".zip") ?: ""
    val voskModels = uiState.availableModels[voskKey] ?: emptyList()
    
    val isVoskMultilingual = RemoteModelRegistry.isMultilingual(voskKey)
    val availableVoskLanguages = RemoteModelRegistry.getLanguages(voskKey)

    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter Vosk models by selected language if not multilingual
    val filteredVoskModels = remember(voskModels, uiState.voiceLanguage, isVoskMultilingual) {
        if (isVoskMultilingual) {
            voskModels
        } else {
            voskModels.filter { it.langCode == uiState.voiceLanguage }
        }
    }

    // Get selected wake word model from uiState, default to first downloaded if none selected
    val selectedWakeWordModel = remember(filteredVoskModels, uiState.wakeWordModelPath, refreshTrigger) {
        val path = uiState.wakeWordModelPath
        if (path != null) {
            filteredVoskModels.find { it.id == path }
        } else {
            // Auto-select first downloaded model
            filteredVoskModels.firstOrNull { model ->
                uiState.isModelDownloaded(model.id)
            }
        }
    }

    Text(text = languageManager.getString("service_settings_section"), style = MaterialTheme.typography.titleMedium)

    // Wake Word Enable Switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageManager.getString("wake_word_enabled"))
        Switch(
            checked = uiState.wakeWordEnabled,
            onCheckedChange = { enabled ->
                appStateManager.setWakeWordEnabled(enabled)
                if (!enabled && uiState.isWakeWordServiceListening) {
                    onStopService()
                }
            }
        )
    }

    if (uiState.wakeWordEnabled) {
        // Command Queue Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(languageManager.getString("command_queue_enabled"))
            Switch(
                checked = uiState.commandQueueEnabled,
                onCheckedChange = { enabled ->
                    appStateManager.setCommandQueueEnabled(enabled)
                }
            )
        }

        // Voice Language Selection (only for non-multilingual Vosk)
        if (!isVoskMultilingual && availableVoskLanguages.isNotEmpty()) {
            val languages = availableVoskLanguages.map { lang ->
                lang to lang.uppercase()
            }

            var showLanguageSheet by remember { mutableStateOf(false) }
            val languageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            val languageGroups = listOf(DropdownGroup("AVAILABLE LANGUAGES", languages))
            val selectedLangPair = languages.find { it.first == uiState.voiceLanguage }

            Text(text = languageManager.getString("voice_language"), style = MaterialTheme.typography.labelLarge)

            GroupedDropdownMenu(
                selectedItem = selectedLangPair,
                groups = languageGroups,
                itemLabel = { it.second },
                isDownloaded = { true },
                onDeviceLabel = "",
                onItemSelected = { pair, _ -> appStateManager.setVoiceLanguage(pair.first) },
                onExpandedChange = { showLanguageSheet = it },
                languageManager = languageManager
            )

            if (showLanguageSheet) {
                ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, sheetState = languageSheetState) {
                    GroupedDropdownContent(
                        title = languageManager.getString("voice_language"),
                        groups = languageGroups,
                        itemLabel = { it.second },
                        isDownloaded = { true },
                        onDeviceLabel = "",
                        onItemSelected = { pair, _ -> appStateManager.setVoiceLanguage(pair.first); showLanguageSheet = false },
                        languageManager = languageManager
                    )
                }
            }

            HorizontalDivider()
        }

        // Voice Calibration Card
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = languageManager.getString("ww_calibrate_title"),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = languageManager.getString("ww_calibrate_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val context = LocalContext.current
        val calibrator = remember { WakeWordCalibrator(context) { } }
        val calibrationState by calibrator.state.collectAsStateWithLifecycle()
        val hasProfile = uiState.wakeWordProfileJson != null
        var showCalibrationDialog by remember { mutableStateOf(false) }

        // Show current profile status
        if (hasProfile) {
            Text(
                text = languageManager.getString("ww_calibrate_profile"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = languageManager.getString("ww_calibrate_default"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        var showProfileNameDialog by remember { mutableStateOf(false) }
        var pendingProfile by remember { mutableStateOf<WakeWordProfile?>(null) }

        // Auto-save profile on completion
        LaunchedEffect(calibrationState) {
            if (calibrationState is WakeWordCalibrator.CalibrationState.Complete) {
                VoiceManager.setCalibrationListening(false)
                val profile = (calibrationState as WakeWordCalibrator.CalibrationState.Complete).profile
                pendingProfile = profile
                showProfileNameDialog = true
                delay(1500)
                showCalibrationDialog = false
                calibrator.stop()
            } else if (calibrationState is WakeWordCalibrator.CalibrationState.Failed) {
                VoiceManager.setCalibrationListening(false)
                delay(3000)
                calibrator.stop()
            } else if (calibrationState is WakeWordCalibrator.CalibrationState.Listening) {
                VoiceManager.setCalibrationListening(true)
            } else if (calibrationState is WakeWordCalibrator.CalibrationState.Analyzing) {
                VoiceManager.setCalibrationListening(false)
            } else if (calibrationState is WakeWordCalibrator.CalibrationState.Waiting) {
                VoiceManager.setCalibrationListening(false)
            }
        }

        // Pipe calibrator volume to VoiceManager so ListeningScreen shows live audio
        LaunchedEffect(calibrator) {
            calibrator.volumeFlow.collect { vol ->
                VoiceManager.setCalibrationVolume(vol)
            }
        }

        // Calibration buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isCalibrating = calibrationState is WakeWordCalibrator.CalibrationState.Waiting ||
                calibrationState is WakeWordCalibrator.CalibrationState.Listening ||
                calibrationState is WakeWordCalibrator.CalibrationState.Analyzing

            Button(
                onClick = {
                    showCalibrationDialog = true
                    calibrator.startCalibration()
                },
                enabled = !isCalibrating && !hasProfile,
                modifier = Modifier.weight(1f)
            ) {
                Text(languageManager.getString("ww_calibrate_start"))
            }

            if (hasProfile) {
                OutlinedButton(
                    onClick = { appStateManager.clearWakeWordProfile() },
                    enabled = !isCalibrating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(languageManager.getString("ww_calibrate_delete"))
                }
            }
        }

        // Calibration Dialog
        if (showCalibrationDialog) {
            CalibrationDialog(
                state = calibrationState,
                languageManager = languageManager,
                appStateManager = appStateManager,
                onReady = { round -> calibrator.signalReady(round) },
                onDismiss = {
                    VoiceManager.setCalibrationListening(false)
                    calibrator.stop()
                    showCalibrationDialog = false
                }
            )
        }

        // Profile Name Dialog — shown after calibration completes
        if (showProfileNameDialog && pendingProfile != null) {
            var profileName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    // Save without name if dismissed
                    appStateManager.setWakeWordProfile(WakeWordProfile.toJson(pendingProfile!!))
                    showProfileNameDialog = false
                    pendingProfile = null
                },
                title = { Text(languageManager.getString("profile_name_title")) },
                text = {
                    Column {
                        Text(
                            text = languageManager.getString("profile_name_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text(languageManager.getString("profile_name_label")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val namedProfile = pendingProfile!!.copy(
                                profileName = profileName.trim().ifBlank { null }
                            )
                            appStateManager.setWakeWordProfile(WakeWordProfile.toJson(namedProfile))
                            showProfileNameDialog = false
                            pendingProfile = null
                        }
                    ) { Text(languageManager.getString("profile_name_save")) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            appStateManager.setWakeWordProfile(WakeWordProfile.toJson(pendingProfile!!))
                            showProfileNameDialog = false
                            pendingProfile = null
                        }
                    ) { Text(languageManager.getString("profile_name_skip")) }
                }
            )
        }

        // Wake Word Text Field
        val isWakeWordModelOnDevice = remember(selectedWakeWordModel, refreshTrigger) {
            selectedWakeWordModel != null && uiState.isModelDownloaded(selectedWakeWordModel.id)
        }

        // Local state for immediate text field updates (avoids async DataStore round-trip lag)
        var localWakeWord by remember { mutableStateOf(uiState.wakeWord) }
        LaunchedEffect(uiState.wakeWord) {
            if (uiState.wakeWord != localWakeWord) {
                localWakeWord = uiState.wakeWord
            }
        }

        Logger.log("WW_TextField: hasProfile=$hasProfile, wakeWord='${uiState.wakeWord}', local='$localWakeWord', profileJson=${uiState.wakeWordProfileJson != null}", "WW_UI")

        VoiceInputTextField(
            value = if (hasProfile) "" else localWakeWord,
            onValueChange = {
                if (!hasProfile) {
                    localWakeWord = it
                    appStateManager.setWakeWord(it)
                }
            },
            label = { Text(languageManager.getString("wake_word_label")) },
            placeholder = { Text(if (hasProfile) languageManager.getString("ww_profile_used") else languageManager.getString("wake_word_hint")) },
            languageManager = languageManager,
            voiceLanguage = uiState.voiceLanguage,
            voiceProcessor = uiState.voiceProcessor,
            isModelOnDevice = isWakeWordModelOnDevice,
            readOnly = hasProfile
        )

        // Calibration status indicator (shown below text field)
        if (hasProfile) {
            Text(
                text = languageManager.getString("ww_calibrate_profile"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }

        // Wake Word Model Selection
        Text(text = languageManager.getString("wake_word_model"), style = MaterialTheme.typography.labelLarge)

        val groupedModels = remember(filteredVoskModels, uiState) {
            if (filteredVoskModels.isNotEmpty()) {
                listOf(DropdownGroup("AVAILABLE MODELS", filteredVoskModels))
            } else emptyList()
        }

        GroupedDropdownMenu(
            selectedItem = selectedWakeWordModel,
            groups = groupedModels,
            itemLabel = { it.label + " (" + it.sizeDescription + ")" },
            isDownloaded = { model -> uiState.isModelDownloaded(model.id) },
            onDeviceLabel = languageManager.getString("on_device_label"),
            onItemSelected = { model, isDownloaded ->
                appStateManager.setWakeWordModelPath(model.id)
            },
            onExpandedChange = { showModelSheet = it },
            onDownloadRequest = onDownloadRequest,
            onDeleteRequest = onDeleteRequest,
            onCancelDownload = onCancelDownload,
            downloadProgress = downloadProgress,
            downloadingItem = downloadingItem as? AppModel,
            languageManager = languageManager
        )

        // ModalBottomSheet for model selection
        if (showModelSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModelSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                GroupedDropdownContent(
                    title = languageManager.getString("wake_word_model"),
                    groups = groupedModels,
                    itemLabel = { it.label + " (" + it.sizeDescription + ")" },
                    isDownloaded = { model -> uiState.isModelDownloaded(model.id) },
                    onDeviceLabel = languageManager.getString("on_device_label"),
                    onItemSelected = { model, isDownloaded ->
                        appStateManager.setWakeWordModelPath(model.id)
                        showModelSheet = false
                    },
                    onDownloadRequest = onDownloadRequest,
                    onDeleteRequest = onDeleteRequest,
                    onCancelDownload = onCancelDownload,
                    downloadProgress = downloadProgress,
                    downloadingItem = downloadingItem as? AppModel,
                    languageManager = languageManager
                )
            }
        }

        // Service Status
        Text(text = languageManager.getString("service_status"), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (uiState.isWakeWordServiceListening) languageManager.getString("service_running") else languageManager.getString("service_stopped"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.isWakeWordServiceListening) downloadedColor else MaterialTheme.colorScheme.secondary
        )

        // Check if selected model is on device
        val isModelOnDevice = remember(selectedWakeWordModel, uiState, refreshTrigger) {
            selectedWakeWordModel != null && uiState.isModelDownloaded(selectedWakeWordModel.id)
        }

        // Start/Stop Service Button
        Button(
            onClick = if (uiState.isWakeWordServiceListening) onStopService else onStartService,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isWakeWordServiceListening || isModelOnDevice,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isWakeWordServiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (uiState.isWakeWordServiceListening) languageManager.getString("stop_service") else languageManager.getString("start_service"))
        }

        // Show warning if model not on device
        if (!isModelOnDevice && !uiState.isWakeWordServiceListening) {
            Text(
                text = "Selected model not on device. Please download the model first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CalibrationDialog(
    state: WakeWordCalibrator.CalibrationState,
    languageManager: LanguageManager,
    appStateManager: AppStateManager,
    onReady: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(languageManager.getString("ww_calibrate_title"))
        },
        text = {
            when (state) {
                is WakeWordCalibrator.CalibrationState.Waiting -> {
                    val roundText = languageManager.getString("ww_calibrate_round")
                        .replace("{0}", state.round.toString())
                        .replace("{1}", state.total.toString())
                    Column {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = state.round.toFloat() / state.total,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        Text(
                            text = roundText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.instruction,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = languageManager.getString("ww_calibrate_tap_ready"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is WakeWordCalibrator.CalibrationState.Listening -> {
                    val roundText = languageManager.getString("ww_calibrate_round")
                        .replace("{0}", state.round.toString())
                        .replace("{1}", state.total.toString())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = state.round.toFloat() / state.total,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        Text(
                            text = roundText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = languageManager.getString("ww_calibrate_listening"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        ListeningScreen(
                            languageManager = languageManager,
                            appStateManager = appStateManager,
                            onStop = onDismiss
                        )
                    }
                }
                is WakeWordCalibrator.CalibrationState.Analyzing -> {
                    val roundText = languageManager.getString("ww_calibrate_round")
                        .replace("{0}", state.round.toString())
                        .replace("{1}", "5")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = state.round.toFloat() / 5,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        Text(
                            text = "$roundText - ${languageManager.getString("ww_calibrate_analyzing")}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(12.dp))
                        ListeningScreen(
                            languageManager = languageManager,
                            appStateManager = appStateManager,
                            onStop = onDismiss
                        )
                    }
                }
                is WakeWordCalibrator.CalibrationState.Complete -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        Text(
                            text = languageManager.getString("ww_calibrate_complete"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is WakeWordCalibrator.CalibrationState.Failed -> {
                    Text(
                        text = languageManager.getString("ww_calibrate_failed") + ": ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        },
        confirmButton = {
            when (state) {
                is WakeWordCalibrator.CalibrationState.Waiting -> {
                    Button(onClick = { onReady(state.round) }) {
                        Text(languageManager.getString("ww_calibrate_ready"))
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            when (state) {
                is WakeWordCalibrator.CalibrationState.Complete -> {}
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(languageManager.getString("cancel_button"))
                    }
                }
            }
        }
    )
}
