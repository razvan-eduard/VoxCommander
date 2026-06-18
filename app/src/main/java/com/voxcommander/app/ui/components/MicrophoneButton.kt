package com.voxcommander.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager

@Composable
fun MicrophoneButton(
    languageManager: LanguageManager,
    appStateManager: AppStateManager,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModelOnDevice by appStateManager.voiceModelReady.collectAsState()

    Button(
        onClick = onClick,
        modifier = modifier.size(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        enabled = isModelOnDevice,
        colors = if (isProcessing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        else if (!isModelOnDevice) ButtonDefaults.buttonColors(containerColor = Color.Gray)
        else ButtonDefaults.buttonColors()
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = languageManager.getString("content_desc_record"),
            modifier = Modifier.size(80.dp),
            tint = if (isModelOnDevice) Color.White else Color.LightGray
        )
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
