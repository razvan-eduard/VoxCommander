package com.voxcommander.app.di

import android.content.Context
import androidx.room.Room
import com.voxcommander.app.data.local.db.VoxDatabase
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.domain.intent.IntentDecisionMap
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import com.voxcommander.app.domain.intent.interpreter.LocalLlmInterpreter
import com.voxcommander.app.domain.intent.interpreter.OpenAiInterpreter
import com.voxcommander.app.domain.intent.interpreter.GeminiNanoInterpreter
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.viewmodels.MainViewModel
import com.voxcommander.app.ui.viewmodels.ModelManagementViewModel
import com.whispercpp.whisper.WhisperLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependency Injection Container for Vox Commander.
 * Centralizes initialization of all application components.
 */
class AppContainer(context: Context) {

    // Always use the application context to avoid leaking an Activity.
    private val appContext = context.applicationContext

    // --- SINGLETON MANAGERS ---
    val settingsManager = SettingsManager(appContext)
    val appStateManager = AppStateManager.getInstance(settingsManager, appContext)
    val modelDownloader = ModelDownloader(appContext)
    val languageManager = LanguageManager(appContext)

    // --- DATABASE ---
    val database = Room.databaseBuilder(
        appContext,
        VoxDatabase::class.java,
        DB_NAME
    ).fallbackToDestructiveMigration().build()

    val fastMapDao = database.fastMapDao()

    // --- INTENT ENGINES ---
    private val l1Engine = FastMapEngine(fastMapDao)
    private val l2Engine = OpenAiInterpreter(settingsManager)
    private val l3Engine = LocalLlmInterpreter(appContext, settingsManager)
    private val geminiEngine = GeminiNanoInterpreter(appContext)
    val masterIntentEngine = IntentDecisionMap(l1Engine, l2Engine, l3Engine, geminiEngine, settingsManager)

    // --- VIEW MODELS ---
    val mainViewModel = MainViewModel(masterIntentEngine)
    val modelManagementViewModel = ModelManagementViewModel(
        settingsManager,
        appStateManager,
        modelDownloader,
        appContext
    )

    init {
        android.util.Log.d("AppContainer", "AppContainer init - starting compatibility checks")
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
            settingsManager.setGeminiIncompatible(false)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.w("GeminiProbe", "AICore not found - marking Gemini Nano incompatible")
            settingsManager.setGeminiIncompatible(true)
        } catch (e: Exception) {
            android.util.Log.e("GeminiProbe", "Error probing Gemini support", e)
        }
    }

    /**
     * Crash-cookie check. Before a real GPU transcription, [SettingsManager.setVulkanRuntimeAttempt]
     * is committed synchronously. If the process crashed natively during that GPU work,
     * the flag survives to the next launch. Finding it pending here means the last GPU
     * attempt killed the process, so we mark Vulkan incompatible and clear the cookie.
     */
    private fun checkVulkanCrashCookie() {
        android.util.Log.d("VulkanProbe", "checkVulkanCrashCookie: pending=${settingsManager.isVulkanRuntimeAttemptPending()}")
        if (settingsManager.isVulkanRuntimeAttemptPending()) {
            settingsManager.setVulkanIncompatible(true)
            settingsManager.setVulkanRuntimeAttempt(false)
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
            settingsManager,
            appStateManager
        )

        // Set offline fallback settings in VoiceManager
        VoiceManager.setOfflineFallbackSettings(
            settingsManager.getOfflineFallbackTimeout(),
            settingsManager.getDefaultOfflineModel()
        )
    }

    companion object {
        private const val DB_NAME = "vox-database"
    }
}
