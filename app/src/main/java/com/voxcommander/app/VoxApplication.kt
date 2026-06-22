package com.voxcommander.app

import android.app.Application
import com.voxcommander.app.di.AppContainer

/**
 * Application class for Vox Commander.
 * Holds the AppContainer as an application-scoped singleton, ensuring that
 * dependencies (database, engines, ViewModels, broadcast receivers) are created
 * exactly once and survive configuration changes (e.g. screen rotation).
 */
class VoxApplication : Application() {

    // Lazily created so heavy initialization (DB, engines) happens on first access
    // (when MainActivity is created), matching the previous Activity.onCreate timing.
    val container: AppContainer by lazy { AppContainer(this) }
}
