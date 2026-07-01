package com.voxcommander.app.ui.screens.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.TtsManager

/**
 * Overlay shown when TTS is speaking.
 * Displays the spoken text and a stop button.
 * Mirrors the ListeningScreen pattern.
 */
@Composable
fun SpeakingOverlay(
    languageManager: LanguageManager,
    onStop: () -> Unit = { TtsManager.stop() }
) {
    val isSpeaking by TtsManager.isSpeakingFlow.collectAsStateWithLifecycle()
    val currentText by TtsManager.currentTextFlow.collectAsStateWithLifecycle()

    if (isSpeaking) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp).navigationBarsPadding()
                ) {
                    // Speaker icon with pulse animation
                    val pulseAlpha by animateFloatAsState(
                        targetValue = 0.3f,
                        label = "ttsPulse"
                    )

                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha),
                            shape = RoundedCornerShape(100.dp)
                        ) {}
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "TTS Speaking",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Spoken text
                    Text(
                        text = currentText.ifEmpty { languageManager.getString("vox_speaking") ?: "Speaking..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 4,
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
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(languageManager.getString("stop_speaking") ?: "Stop")
                    }
                }
            }
        }
    }
}
