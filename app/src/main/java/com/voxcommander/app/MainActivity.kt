package com.voxcommander.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.voxcommander.app.data.local.db.VoxDatabase
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.domain.engine.whisper.WhisperCppSttEngine
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import com.voxcommander.app.domain.engine.google.GoogleSttEngine
import com.voxcommander.app.domain.engine.vosk.VoskSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperSttEngine
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.ui.screens.main.MainScreen
import com.voxcommander.app.ui.theme.VoxCommanderTheme
import com.voxcommander.app.ui.viewmodels.MainViewModel
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var modelDownloader: ModelDownloader
    private lateinit var languageManager: LanguageManager
    private var pendingModelLanguage: String? = null
    private var lastDownloadedVoskModelName: String? = null
    private var lastDownloadedWhisperModelId: String? = null
    private var lastDownloadType: String? = null // "vosk" or "whisper"
    
    // Progress tracking state
    private val _downloadProgress = mutableStateOf<Float?>(null)
    private var progressJob: Job? = null
    private var currentDownloadId: Long? = null
    
    // Success message state
    private val _selectionSuccessMessage = mutableStateOf<String?>(null)
    private var messageJob: Job? = null
    
    // Vulkan incompatibility state
    private val _showVulkanError = mutableStateOf(false)

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
            val path = it.path ?: it.toString()
            pendingModelLanguage?.let { lang ->
                settingsManager.saveCustomVoskModelPath(lang, path)
                Toast.makeText(this, "Custom Vosk model path saved", Toast.LENGTH_SHORT).show()
                updateVoiceEngine()
            }
        }
    }

    private val customWhisperModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            copyUriToInternal(it, WHISPER_CUSTOM_MODEL_FILENAME)?.let { path ->
                settingsManager.saveCustomWhisperModelPath(path)
                Toast.makeText(this, "Custom Whisper model saved", Toast.LENGTH_SHORT).show()
                updateVoiceEngine()
            }
        }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val textVorbit = matches?.firstOrNull() ?: ""
            
            android.util.Log.d(Strings.Tags.VOX_COMMANDER, "Am auzit prin Intent: $textVorbit")
            
            // Send result to VoiceManager
            VoiceManager.handleIntentResult(textVorbit)
        }
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id != -1L) {
                _downloadProgress.value = null
                progressJob?.cancel()

                // Use lastDownloadType instead of current processor to handle cross-tab downloads
                when (lastDownloadType) {
                    "whisper" -> {
                        val modelId = lastDownloadedWhisperModelId ?: settingsManager.getSelectedWhisperModelId()
                        modelDownloader.verifyWhisperModel(modelId) { success ->
                            if (success) {
                                settingsManager.setModelDownloaded(modelId, true)
                                showSuccessMessage("Whisper Model $modelId ready!")
                                runOnUiThread {
                                    updateVoiceEngine()
                                }
                            }
                        }
                    }
                    "vosk" -> {
                        val modelName = lastDownloadedVoskModelName ?: ""
                        modelDownloader.unzipVoskModel(modelName) { success ->
                            if (success) {
                                settingsManager.setModelDownloaded(modelName, true)
                                showSuccessMessage("Vosk Model $modelName ready!")
                                runOnUiThread {
                                    updateVoiceEngine()
                                }
                            }
                        }
                    }
                }
                lastDownloadType = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.log("MainActivity: onCreate called")

        settingsManager = SettingsManager(this)
        modelDownloader = ModelDownloader(this)
        languageManager = LanguageManager(this)
        languageManager.loadLanguage(settingsManager.getLanguage())
        Logger.log("MainActivity: Language loaded: ${settingsManager.getLanguage()}")

        ContextCompat.registerReceiver(
            this,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        val db = Room.databaseBuilder(
            applicationContext,
            VoxDatabase::class.java, DB_NAME
        ).fallbackToDestructiveMigration().build()

        val fastMapDao = db.fastMapDao()
        val assistantEngine = FastMapEngine(fastMapDao)

        updateVoiceEngine()

        // Set offline fallback settings in VoiceManager
        VoiceManager.setOfflineFallbackSettings(
            settingsManager.getOfflineFallbackTimeout(),
            settingsManager.getDefaultOfflineModel()
        )

        // Check Google STT availability
        val googleSttAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(this)

        val mainViewModel = MainViewModel(assistantEngine)

        checkPermissions()

        enableEdgeToEdge()
        setContent {
            VoxCommanderTheme {
                val navController = rememberNavController()
                val currentProgress by _downloadProgress
                val successMessage by _selectionSuccessMessage
                val showVulkanError by _showVulkanError

                if (showVulkanError) {
                    AlertDialog(
                        onDismissRequest = { _showVulkanError.value = false },
                        title = { Text(languageManager.getString("vulkan_incompatible_title")) },
                        text = { Text(languageManager.getString("vulkan_incompatible_msg")) },
                        confirmButton = {
                            TextButton(onClick = { _showVulkanError.value = false }) {
                                Text(languageManager.getString("ok"))
                            }
                        }
                    )
                }

                NavHost(navController = navController, startDestination = Strings.Routes.MAIN) {
                    composable(Strings.Routes.MAIN) {
                        MainScreen(
                            languageManager = languageManager,
                            settingsManager = settingsManager,
                            fastMapDao = fastMapDao,
                            viewModel = mainViewModel,
                            onDownloadVoskModel = { lang, url, name ->
                                lastDownloadedVoskModelName = name
                                lastDownloadType = "vosk"
                                val id = modelDownloader.downloadVoskModel(lang, url, name)
                                startProgressTracking(id)
                                Toast.makeText(this@MainActivity, languageManager.getString("vosk_download_started"), Toast.LENGTH_SHORT).show()
                            },
                            onDownloadWhisperModel = { modelId, url ->
                                lastDownloadedWhisperModelId = modelId
                                lastDownloadType = "whisper"
                                val id = modelDownloader.downloadWhisperModel(modelId, url)
                                startProgressTracking(id)
                                Toast.makeText(this@MainActivity, languageManager.getString("whisper_download_started"), Toast.LENGTH_SHORT).show()
                            },
                            onSelectCustomVoskModel = { lang ->
                                pendingModelLanguage = lang
                                customVoskModelLauncher.launch(null)
                            },
                            onSelectCustomWhisperModel = {
                                customWhisperModelLauncher.launch(arrayOf(MIME_TYPE_ALL))
                            },
                            onDeleteUnusedModels = {
                                val currentVoskLang = settingsManager.getVoiceLanguage()
                                val currentWhisperId = settingsManager.getSelectedWhisperModelId()
                                modelDownloader.deleteUnusedModels(currentVoskLang, currentWhisperId)
                                settingsManager.clearUnusedModelFlags(currentVoskLang, currentWhisperId)
                                Toast.makeText(this@MainActivity, languageManager.getString("unused_models_deleted"), Toast.LENGTH_SHORT).show()
                            },
                            onCancelDownload = { cancelDownload() },
                            downloadProgress = currentProgress,
                            selectionSuccessMessage = successMessage,
                            googleSttAvailable = googleSttAvailable,
                            updateVoiceEngine = { updateVoiceEngine() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.log("MainActivity: onDestroy called")
        VoiceManager.release() // Release C++ memory
        unregisterReceiver(onDownloadComplete)
        progressJob?.cancel()
    }

    private fun updateVoiceEngine() {
        val apiKey = settingsManager.getApiKey()
        val voiceLang = settingsManager.getVoiceLanguage()
        val processor = settingsManager.getVoiceProcessor()

        Logger.log("MainActivity: Updating voice engine - processor: $processor, language: $voiceLang")

        // Initialize all STT engines
        val whisperCpp = WhisperCppSttEngine(
            this, 
            settingsManager, 
            forceGpu = (processor == Strings.Processors.WHISPER_VULKAN),
            onVulkanIncompatible = {
                _showVulkanError.value = true
                settingsManager.saveVoiceProcessor(Strings.Processors.WHISPER_NEON)
            }
        )
        val whisperApi = if (!apiKey.isNullOrBlank()) WhisperSttEngine(apiKey) else null
        val google = GoogleSttEngine(this)
        val vosk = VoskSttEngine(this, settingsManager, voiceLang)

        VoiceManager.init(
            this,
            whisperCpp,
            whisperApi,
            google,
            vosk,
            { langCode -> startGoogleVoiceIntent(langCode) },
            settingsManager
        )

        Logger.log("MainActivity: Voice engine updated successfully")
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @SuppressLint("Range")
    private fun startProgressTracking(downloadId: Long) {
        progressJob?.cancel()
        currentDownloadId = downloadId
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        progressJob = CoroutineScope(Dispatchers.IO).launch {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    
                    if (bytesTotal > 0) {
                        _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                    }
                    
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                        if (status == DownloadManager.STATUS_FAILED) {
                            _downloadProgress.value = null
                        }
                    }
                }
                cursor.close()
                delay(500)
            }
        }
    }
    
    private fun cancelDownload() {
        currentDownloadId?.let { downloadId ->
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            progressJob?.cancel()
            _downloadProgress.value = null
            currentDownloadId = null
            
            // Delete partial download files
            lastDownloadedVoskModelName?.let { modelName ->
                val zipFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "vosk-model-$modelName.zip")
                if (zipFile.exists()) zipFile.delete()
            }
            lastDownloadedWhisperModelId?.let { modelId ->
                val modelFile = File(getExternalFilesDir(null), "whisper-model-$modelId.bin")
                if (modelFile.exists()) modelFile.delete()
            }
            
            Toast.makeText(this, languageManager.getString("download_cancelled"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccessMessage(message: String) {
        messageJob?.cancel()
        _selectionSuccessMessage.value = message
        messageJob = CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            _selectionSuccessMessage.value = null
        }
    }

    private fun copyUriToInternal(uri: Uri, targetName: String): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(getExternalFilesDir(null), targetName)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun startGoogleVoiceIntent(langCode: String = "ro-RO") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ascult comanda...")
        }
        
        try {
            speechLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Aplicația Google nu este instalată pe acest dispozitiv!", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val DB_NAME = "vox-database"
        private const val WHISPER_CUSTOM_MODEL_FILENAME = "custom-whisper-model.bin"
        private const val MIME_TYPE_ALL = "*/*"
    }
}
