package com.voxcommander.app

import android.app.Application
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.di.AppContainer
import com.voxcommander.app.utils.LogLevel
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.LoggingFlags
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for Vox Commander.
 * Holds the AppContainer as an application-scoped singleton.
 */
class VoxApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Logger early
        val level = LogLevel.valueOf(container.settingsManager.getLogLevel())
        Logger.initialize(this, level)
        Logger.setLoggingFlags(LoggingFlags.fromLogLevel(level))
        Logger.setVerboseLoggingEnabled(container.settingsManager.isVerboseLoggingEnabled())

        // Initialize default voice language if not set
        if (container.settingsManager.getVoiceLanguage().isEmpty()) {
            container.settingsManager.saveVoiceLanguage(Strings.Preferences.DEFAULT_LANGUAGE)
        }
        
        // Initial fetch of the remote model registry - Force update on start to bypass CDN caching
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val success = RemoteModelRegistry.fetchJson(container.settingsManager, force = true)
            if (success) {
                // Force AppStateManager to rebuild its UI state with the fresh models
                container.appStateManager.refreshAll()
            }
        }
    }
}
