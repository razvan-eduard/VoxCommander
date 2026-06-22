package com.voxcommander.app

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voxcommander.app.di.AppContainer
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.ui.screens.main.MainScreen
import com.voxcommander.app.ui.theme.VoxCommanderTheme
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import com.voxcommander.app.utils.VoiceIntentLauncher
import kotlinx.coroutines.delay

/**
 * MainActivity: Thin UI Container.
 * Manages high-level Android lifecycle, permissions, and system intents.
 * Business logic and functional state are delegated to AppStateManager and VoiceManager.
 */
class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private lateinit var voiceIntentLauncher: VoiceIntentLauncher
    private var pendingModelLanguage: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val customVoskModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            pendingModelLanguage?.let { lang ->
                appContainer.modelManagementViewModel.selectCustomVoskModel(it, lang)
            }
        }
    }

    private val customWhisperModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            appContainer.modelManagementViewModel.selectCustomWhisperModel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install Splash Screen BEFORE super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        Logger.log("MainActivity: onCreate called")

        // Application-scoped dependency container (created once, survives rotation)
        appContainer = (application as VoxApplication).container
        appContainer.languageManager.loadLanguage(appContainer.settingsManager.getLanguage())
        Logger.log("MainActivity: Language loaded: ${appContainer.settingsManager.getLanguage()}")

        // Google Voice Intent launcher (lifecycle-bound, must live in the Activity)
        voiceIntentLauncher = VoiceIntentLauncher(this) { result ->
            VoiceManager.handleIntentResult(result)
        }

        // Initialize VoiceManager (reactively manages engines from AppStateManager)
        appContainer.initVoiceManager(this, voiceIntentLauncher)

        // Check Google STT availability
        val googleSttAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(this)

        checkPermissions()

        enableEdgeToEdge()
        setContent {
            VoxCommanderTheme {
                val navController = rememberNavController()
                val currentProgress by appContainer.modelManagementViewModel.downloadProgress.collectAsState()
                val successMessage by appContainer.modelManagementViewModel.selectionSuccessMessage.collectAsState()
                val showVulkanError by appContainer.modelManagementViewModel.showVulkanError.collectAsState()

                // --- WAKE WORD DETECTION LISTENER ---
                val wakeWordDetected by appContainer.appStateManager.wakeWordDetected.collectAsState()
                LaunchedEffect(wakeWordDetected) {
                    if (wakeWordDetected) {
                        Logger.log("MainActivity: Wake word detected! (via StateFlow)")
                        appContainer.mainViewModel.processVoiceCommand(
                            appContainer.settingsManager.getVoiceLanguage(),
                            appContainer.settingsManager.getVoiceProcessor()
                        )
                        // Add a small delay to ensure processing starts before reset
                        delay(500)
                        appContainer.appStateManager.resetWakeWordDetection()
                    }
                }

                if (showVulkanError) {
                    AlertDialog(
                        onDismissRequest = { appContainer.modelManagementViewModel.dismissVulkanError() },
                        title = { Text(appContainer.languageManager.getString("vulkan_incompatible_title")) },
                        text = { Text(appContainer.languageManager.getString("vulkan_incompatible_msg")) },
                        confirmButton = {
                            TextButton(onClick = { appContainer.modelManagementViewModel.dismissVulkanError() }) {
                                Text(appContainer.languageManager.getString("ok"))
                            }
                        }
                    )
                }

                NavHost(navController = navController, startDestination = Strings.Routes.MAIN) {
                    composable(Strings.Routes.MAIN) {
                        MainScreen(
                            languageManager = appContainer.languageManager,
                            settingsManager = appContainer.settingsManager,
                            appStateManager = appContainer.appStateManager,
                            fastMapDao = appContainer.fastMapDao,
                            viewModel = appContainer.mainViewModel,
                            onDownloadVoskModel = { lang, url, name ->
                                appContainer.modelManagementViewModel.downloadVoskModel(lang, url, name)
                            },
                            onDownloadWhisperModel = { modelId, url ->
                                appContainer.modelManagementViewModel.downloadWhisperModel(modelId, url)
                            },
                            onSelectCustomVoskModel = { lang ->
                                pendingModelLanguage = lang
                                customVoskModelLauncher.launch(null)
                            },
                            onSelectCustomWhisperModel = {
                                customWhisperModelLauncher.launch(arrayOf(MIME_TYPE_ALL))
                            },
                            onDeleteUnusedModels = {
                                appContainer.modelManagementViewModel.deleteUnusedModels()
                                Toast.makeText(this@MainActivity, appContainer.languageManager.getString("unused_models_deleted"), Toast.LENGTH_SHORT).show()
                            },
                            onCancelDownload = {
                                appContainer.modelManagementViewModel.cancelDownload()
                                Toast.makeText(this@MainActivity, appContainer.languageManager.getString("download_cancelled"), Toast.LENGTH_SHORT).show()
                            },
                            onDownloadLlamaModel = { model ->
                                appContainer.modelManagementViewModel.downloadLlamaModel(model.id, model.url)
                            },
                            onDeleteLlamaModel = { model ->
                                appContainer.modelManagementViewModel.deleteLlamaModel(model.id)
                                Toast.makeText(this@MainActivity, "Llama model deleted", Toast.LENGTH_SHORT).show()
                            },
                            downloadProgress = currentProgress,
                            selectionSuccessMessage = successMessage,
                            googleSttAvailable = googleSttAvailable,
                            updateVoiceEngine = { /* Handled reactively by VoiceManager */ }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.log("MainActivity: onDestroy called")
        VoiceManager.release() // Release all native memory and resources
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.first())
        }
    }

    companion object {
        private const val MIME_TYPE_ALL = "*/*"
    }
}
