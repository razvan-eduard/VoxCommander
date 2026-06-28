package com.voxcommander.app.domain.intent.resolver

import com.voxcommander.app.data.preferences.AppSettings
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.domain.intent.registry.AppRegistry
import com.voxcommander.app.utils.Logger

/**
 * Resolves the target AppEntry for an NluIntent.
 *
 * Resolution chain:
 * 1. Explicit targetApp from the intent (package name lookup, if installed)
 * 2. User's default app for the domain (from SettingsRepository/DataStore)
 * 3. Domain default (first installed app in domain)
 * 4. null (system default / implicit intent)
 *
 * Returns the full AppEntry (with URI templates) or null for system default.
 */
object AppResolver {

    private const val TAG = "AppResolver"

    /**
     * Resolves the target app for an NluIntent.
     * @param intent The intent to resolve.
     * @param settings Current app settings snapshot (for user default app preferences). null = skip user defaults.
     * @return AppEntry if resolved, null for system default / implicit intent.
     */
    fun resolve(intent: NluIntent, settings: AppSettings? = null): AppRegistry.AppEntry? {
        // 1. Try explicit targetApp (package name lookup)
        val explicit = AppRegistry.resolveByPackage(intent.targetApp)
        if (explicit != null) {
            Logger.log("Resolved '${intent.targetApp}' -> ${explicit.packageName} (EXPLICIT)", TAG)
            return explicit
        }

        // 2. Try user's default app for this domain (from DataStore preferences)
        if (settings != null) {
            val userDefaultPkg = settings.defaultAppPackages[intent.domain]
            if (!userDefaultPkg.isNullOrBlank()) {
                val userDefault = AppRegistry.resolveByPackage(userDefaultPkg)
                if (userDefault != null) {
                    Logger.log("Using user default for '${intent.domain}' -> ${userDefault.packageName}", TAG)
                    return userDefault
                }
            }
        }

        // 3. Try domain default (first installed app)
        val domainDefault = AppRegistry.getDefaultAppForDomain(intent.domain)
        if (domainDefault != null) {
            Logger.log("Using domain default for '${intent.domain}' -> ${domainDefault.packageName}", TAG)
            return domainDefault
        }

        // 4. System default / implicit
        Logger.log("No app found for domain='${intent.domain}', targetApp='${intent.targetApp}'. Using system default.", TAG)
        return null
    }

    /**
     * Resolves an app by explicit package name (used by FastMap rules with targetPackage).
     * @return AppEntry if installed, null otherwise.
     */
    fun resolveByPackage(packageName: String?): AppRegistry.AppEntry? {
        return AppRegistry.resolveByPackage(packageName)
    }
}
