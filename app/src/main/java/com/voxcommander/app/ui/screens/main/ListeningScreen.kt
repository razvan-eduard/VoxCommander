package com.voxcommander.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager

@Composable
fun ListeningScreen(
    languageManager: LanguageManager,
    onStop: () -> Unit = { VoiceManager.stopListening() }
) {
    val isListening by VoiceManager.isListeningFlow.collectAsState()
    val partialTranscription by VoiceManager.partialTranscriptionFlow.collectAsState()
    val volume by VoiceManager.volumeFlow.collectAsState()

    if (isListening) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Microphone Icon with Volume Visualization
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Volume indicator ring
                    // Scale alpha based on normalized volume (0.0 to 1.0)
                    Surface(
                        modifier = Modifier.size(
                            (180 + (volume * 150)).dp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = (0.1f + (volume * 0.4f)).coerceIn(0.1f, 0.5f)
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {}

                    Icon(
                        Icons.Default.Mic,
                        contentDescription = languageManager.getString("content_desc_listening"),
                        modifier = Modifier.size(120.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Partial Transcription
                if (partialTranscription.isNotEmpty()) {
                    Text(
                        text = partialTranscription,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                } else {
                    Text(
                        text = languageManager.getString("recording_status"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))

                // Stop Button
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp)
                        .height(56.dp)
                ) {
                    Text(languageManager.getString("stop_recording_button") ?: "Stop Recording", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
