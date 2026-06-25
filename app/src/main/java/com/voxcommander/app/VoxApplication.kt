package com.voxcommander.app

import android.app.Application
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.di.AppContainer
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
        
        // Initial fetch of the remote model registry - Force update on start to bypass CDN caching
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            RemoteModelRegistry.fetchJson(container.settingsManager, force = true)
        }
    }
}
