package com.voxcommander.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState

@Composable
fun MicrophoneButton(
    languageManager: LanguageManager,
    appStateManager: AppStateManager,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModelOnDevice by appStateManager.voiceModelReady.collectAsState()
    val globalVoiceState by appStateManager.voiceState.collectAsState()
    
    val isCurrentlyProcessing = globalVoiceState == VoiceState.PROCESSING || isProcessing
    val isRecording = globalVoiceState == VoiceState.LISTENING_COMMAND

    val buttonColor by animateColorAsState(
        targetValue = when {
            !isModelOnDevice -> Color.Gray
            isRecording -> MaterialTheme.colorScheme.error // RED
            isCurrentlyProcessing -> Color(0xFFFFA000) // ORANGE
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 300),
        label = "ButtonColor"
    )

    Button(
        onClick = onClick,
        modifier = modifier.size(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        enabled = isModelOnDevice,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCurrentlyProcessing) {
                // Spinning indicator for "AI is thinking"
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = Color.White,
                    strokeWidth = 6.dp
                )
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = languageManager.getString("content_desc_record"),
                    modifier = Modifier.size(80.dp),
                    tint = if (isModelOnDevice) Color.White else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun ModelNotPresentMessage(
    languageManager: LanguageManager,
    appStateManager: AppStateManager
) {
    val isModelOnDevice by appStateManager.voiceModelReady.collectAsState()

    if (!isModelOnDevice) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = languageManager.getString("model_not_present"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
