package com.voxcommander.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.Strings

@Composable
fun ListeningScreen(
    languageManager: LanguageManager,
    appStateManager: AppStateManager,
    onStop: () -> Unit = { VoiceManager.stopListening() }
) {
    val isListening by VoiceManager.isListeningFlow.collectAsState()
    val partialTranscription by VoiceManager.partialTranscriptionFlow.collectAsState()
    val volume by VoiceManager.volumeFlow.collectAsState()
    val uiState by appStateManager.uiState.collectAsState()

    // GOOGLE VOICE EXCLUSION: Google provides its own native overlay.
    // We hide our custom overlay to avoid UI clutter.
    if (isListening && uiState.voiceProcessor != Strings.Processors.GOOGLE) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f), // Increased height to prevent clipping
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp).navigationBarsPadding() // Added navigation bars padding
                ) {
                    // Microphone Icon with Volume Visualization
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing Volume indicator ring
                        Surface(
                            modifier = Modifier.size(
                                (100 + (volume * 100)).dp
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = (0.1f + (volume * 0.4f)).coerceIn(0.1f, 0.5f)
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {}

                        Icon(
                            Icons.Default.Mic,
                            contentDescription = languageManager.getString("content_desc_listening"),
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Partial Transcription
                    Text(
                        text = partialTranscription.ifEmpty { languageManager.getString("recording_status") },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Stop Button
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(languageManager.getString("stop_recording_button") ?: "Stop Recording")
                    }
                }
            }
        }
    }
}
