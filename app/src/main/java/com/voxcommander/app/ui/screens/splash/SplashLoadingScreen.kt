package com.voxcommander.app.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxcommander.app.R
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import kotlinx.coroutines.delay

@Composable
fun SplashLoadingScreen(
    languageManager: LanguageManager,
    onFinished: () -> Unit
) {
    val loadStatus by RemoteModelRegistry.loadStatus.collectAsStateWithLifecycle()

    // Auto-advance when loading is done (with small delay for UX)
    LaunchedEffect(loadStatus) {
        if (loadStatus != RemoteModelRegistry.LoadStatus.LOADING) {
            delay(800)
            onFinished()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo — full width with small margins
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            AnimatedVisibility(
                visible = loadStatus == RemoteModelRegistry.LoadStatus.LOADING,
                enter = fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = languageManager.getString("splash_loading_assets"),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Loaded from remote
            AnimatedVisibility(
                visible = loadStatus == RemoteModelRegistry.LoadStatus.LOADED_FROM_REMOTE,
                enter = fadeIn()
            ) {
                Text(
                    text = languageManager.getString("splash_loaded_remote"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            // Loaded from cache
            AnimatedVisibility(
                visible = loadStatus == RemoteModelRegistry.LoadStatus.LOADED_FROM_CACHE,
                enter = fadeIn()
            ) {
                Text(
                    text = languageManager.getString("splash_no_network"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // No network at all
            AnimatedVisibility(
                visible = loadStatus == RemoteModelRegistry.LoadStatus.NO_NETWORK,
                enter = fadeIn()
            ) {
                Text(
                    text = languageManager.getString("splash_no_network"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
