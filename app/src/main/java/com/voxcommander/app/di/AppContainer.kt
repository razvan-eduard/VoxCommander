package com.voxcommander.app.di

import android.content.Context
import androidx.room.Room
import com.voxcommander.app.data.local.db.VoxDatabase
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.data.preferences.SettingsRepositoryImpl
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.domain.intent.IntentDecisionMap
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import com.voxcommander.app.domain.intent.interpreter.LocalLlmInterpreter
import com.voxcommander.app.domain.intent.interpreter.OpenAiInterpreter
import com.voxcommander.app.domain.intent.interpreter.GeminiNanoInterpreter
import com.voxcommander.app.domain.intent.interpreter.GeminiCloudInterpreter
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.viewmodels.MainViewModel
import com.voxcommander.app.ui.viewmodels.ModelManagementViewModel
import com.whispercpp.whisper.WhisperLib

/**
 * Dependency Injection Container for Vox Commander.
 * Centralizes initialization of all application components.
 */
class AppContainer(context: Context) {

    // Always use the application context to avoid leaking an Activity.
    private val appContext = context.applicationContext

    // --- SETTINGS REPOSITORY (DataStore-backed, singleton) ---
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext)

    // --- SINGLETON MANAGERS ---
    val appStateManager = AppStateManager.getInstance(settingsRepository, appContext)
    val modelDownloader = ModelDownloader(appContext)
    val languageManager = LanguageManager(appContext)
    val voiceOverlayManager = com.voxcommander.app.ui.components.VoiceOverlayManager(appContext, languageManager, appStateManager)

    // --- DATABASE ---
    val database = Room.databaseBuilder(
        appContext,
        VoxDatabase::class.java,
        DB_NAME
    ).fallbackToDestructiveMigration().build()

    val fastMapDao = database.fastMapDao()

    // --- INTENT ENGINES ---
    private val l1Engine = FastMapEngine(fastMapDao)
    private val l2Engine = OpenAiInterpreter(settingsRepository)
    val localLlmInterpreter = LocalLlmInterpreter(appContext, settingsRepository, modelDownloader)
    val geminiNanoInterpreter = GeminiNanoInterpreter(appContext, settingsRepository)
    val geminiCloudInterpreter = GeminiCloudInterpreter(settingsRepository)
    val masterIntentEngine = IntentDecisionMap(l1Engine, l2Engine, localLlmInterpreter, geminiNanoInterpreter, geminiCloudInterpreter, settingsRepository)

    // --- VIEW MODELS ---
    val mainViewModel = MainViewModel(masterIntentEngine, appStateManager, languageManager)
    val modelManagementViewModel = ModelManagementViewModel(
        settingsRepository,
        appStateManager,
        modelDownloader,
        languageManager,
        appContext
    )

    init {
        android.util.Log.d("AppContainer", "AppContainer init - starting compatibility checks")
        // Migrate old EncryptedSharedPreferences to DataStore synchronously (runs once, fast)
        kotlinx.coroutines.runBlocking {
            (settingsRepository as SettingsRepositoryImpl).migrateFromSharedPreferencesIfNeeded()
        }
        checkVulkanCrashCookie()
        detectGeminiSupport()
    }

    /**
     * Checks if Gemini Nano (AICore) is available on the system.
     */
    private fun detectGeminiSupport() {
        try {
            val pm = appContext.packageManager
            pm.getPackageInfo("com.google.android.aicore", 0)
            android.util.Log.d("GeminiProbe", "AICore detected - Gemini Nano supported")
            kotlinx.coroutines.runBlocking { settingsRepository.setGeminiIncompatible(false) }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.w("GeminiProbe", "AICore not found - marking Gemini Nano incompatible")
            kotlinx.coroutines.runBlocking { settingsRepository.setGeminiIncompatible(true) }
        } catch (e: Exception) {
            android.util.Log.e("GeminiProbe", "Error probing Gemini support", e)
        }
    }

    /**
     * Crash-cookie check. Before a real GPU transcription, [SettingsRepository.setVulkanRuntimeAttemptSync]
     * is committed synchronously. If the process crashed natively during that GPU work,
     * the flag survives to the next launch. Finding it pending here means the last GPU
     * attempt killed the process, so we mark Vulkan incompatible and clear the cookie.
     */
    private fun checkVulkanCrashCookie() {
        val snapshot = settingsRepository.getSettingsSnapshot()
        android.util.Log.d("VulkanProbe", "checkVulkanCrashCookie: pending=${snapshot.vulkanRuntimeAttempt}")
        if (snapshot.vulkanRuntimeAttempt) {
            kotlinx.coroutines.runBlocking { settingsRepository.setVulkanIncompatible(true) }
            kotlinx.coroutines.runBlocking { settingsRepository.setVulkanRuntimeAttemptSync(false) }
            android.util.Log.w(
                "VulkanProbe",
                "Detected native crash during previous Vulkan GPU use -> marking incompatible"
            )
        }
    }


    // --- VOICE MANAGER INITIALIZATION ---
    fun initVoiceManager(context: Context, voiceIntentLauncher: com.voxcommander.app.utils.VoiceIntentLauncher) {
        VoiceManager.init(
            context,
            null, // Engines are now managed internally by VoiceManager via observation
            null,
            null,
            null,
            { langCode -> voiceIntentLauncher.launch(langCode) },
            settingsRepository,
            appStateManager
        )

        // Set offline fallback settings in VoiceManager
        val snapshot = settingsRepository.getSettingsSnapshot()
        VoiceManager.setOfflineFallbackSettings(
            snapshot.offlineFallbackTimeout,
            snapshot.defaultOfflineModel
        )
    }

    companion object {
        private const val DB_NAME = "vox-database"
    }
}
