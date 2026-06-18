package com.voxcommander.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.ui.screens.main.ListeningScreen

@Composable
fun VoiceInputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    languageManager: LanguageManager,
    voiceLanguage: String,
    voiceProcessor: String,
    isModelOnDevice: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Collect the global listening state to keep UI in sync
    val isGloballyListening by VoiceManager.isListeningFlow.collectAsState()
    
    // Local state to track IF this specific field started the recording
    var startedRecordingHere by remember { mutableStateOf(false) }

    // Reset local flag if global state stops
    LaunchedEffect(isGloballyListening) {
        if (!isGloballyListening) {
            startedRecordingHere = false
        }
    }

    val isRecording = isGloballyListening && startedRecordingHere

    OutlinedTextField(
        value = if (isRecording) languageManager.getString("recording_status") else value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth(),
        colors = if (isRecording) {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Red,
                unfocusedBorderColor = Color.Red
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    if (isGloballyListening) {
                        VoiceManager.stopListening()
                    } else {
                        startedRecordingHere = true
                        VoiceManager.startListening(voiceLanguage, voiceProcessor) { transcription ->
                            // IMPORTANT: transcription could be an error message
                            if (transcription.isNotEmpty() && !transcription.startsWith("Error:")) {
                                onValueChange(transcription)
                            }
                        }
                    }
                },
                enabled = isModelOnDevice
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = languageManager.getString("content_desc_record"),
                    tint = if (isRecording) Color.Red else if (isModelOnDevice) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    )

    ListeningScreen(languageManager = languageManager)
}
