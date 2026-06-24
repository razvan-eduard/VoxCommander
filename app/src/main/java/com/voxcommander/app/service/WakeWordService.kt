package com.voxcommander.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voxcommander.app.MainActivity
import com.voxcommander.app.R
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that listens for the wake word using Vosk.
 * When the wake word is detected, it triggers the main voice command listening.
 */
class WakeWordService : Service() {

    private val TAG = "WakeWordService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var settingsManager: SettingsManager
    private lateinit var appStateManager: AppStateManager
    private lateinit var languageManager: com.voxcommander.app.domain.localization.LanguageManager
    private lateinit var voiceOverlayManager: com.voxcommander.app.ui.components.VoiceOverlayManager
    private var wakeWordEngine: WakeWordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    private val CHANNEL_ID = "wake_word_service_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        Logger.log("WakeWordService created", TAG)
        settingsManager = SettingsManager(this)
        appStateManager = AppStateManager.getInstance(settingsManager, this)
        languageManager = com.voxcommander.app.domain.localization.LanguageManager(this).apply {
            loadLanguage(settingsManager.getLanguage())
        }
        voiceOverlayManager = com.voxcommander.app.ui.components.VoiceOverlayManager(this, languageManager, appStateManager)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxCommander:WakeWordLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        serviceScope.launch {
            appStateManager.uiState.collectLatest { uiState ->
                handleVoiceStateChange(uiState.voiceState)
            }
        }

        // Overlay observer
        serviceScope.launch {
            VoiceManager.isListeningFlow.collectLatest { isListening ->
                val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(this@WakeWordService)
                } else true
                
                if (isListening && canDraw) {
                    voiceOverlayManager.show()
                } else {
                    voiceOverlayManager.hide()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Logger.log("WakeWordService onStartCommand: $action", TAG)

        when (action) {
            ACTION_START -> startWakeWordDetection()
            ACTION_STOP -> {
                stopWakeWordDetection()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.log("WakeWordService destroyed", TAG)
        stopWakeWordDetection()
        wakeLock?.release()
        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    private fun startWakeWordDetection() {
        Logger.log("Starting wake word detection", TAG)
        
        // --- FORCE RESET IF STUCK ---
        if (appStateManager.uiState.value.voiceState != VoiceState.IDLE) {
            Log.w(TAG, "Service was in state ${appStateManager.uiState.value.voiceState}. Forcing IDLE...")
            appStateManager.setVoiceState(VoiceState.IDLE)
        }

        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Foreground notification shown")

        serviceScope.launch {
            val wakeWord = settingsManager.getWakeWord()
            val wakeWordModelName = settingsManager.getWakeWordModelPath()
            val voiceLanguage = settingsManager.getVoiceLanguage()

            val rootDir = getExternalFilesDir(null)
            val modelPath = if (!wakeWordModelName.isNullOrBlank()) {
                val directFile = File(rootDir, wakeWordModelName)
                if (directFile.exists()) {
                    directFile.absolutePath
                } else {
                    rootDir?.listFiles()?.find { 
                        it.isDirectory && it.name.contains(wakeWordModelName, ignoreCase = true) 
                    }?.absolutePath
                }
            } else {
                rootDir?.listFiles()?.find { 
                    it.isDirectory && it.name.startsWith("vosk-model-") && it.name.contains(voiceLanguage, ignoreCase = true) 
                }?.absolutePath
            }

            Logger.log("Wake word: $wakeWord, Path: ${modelPath ?: "null"}", TAG)

            if (modelPath == null) {
                Logger.log("No Vosk model available")
                stopSelf()
                return@launch
            }

            wakeWordEngine = WakeWordEngine(this@WakeWordService, settingsManager, appStateManager) {
                onWakeWordDetected()
            }

            val initialized = wakeWordEngine?.initialize(modelPath, wakeWord) ?: false
            Logger.log("Engine initialized: $initialized")

            if (initialized) {
                wakeWordEngine?.startListening()
                updateNotification("Listening for '$wakeWord'...")
            } else {
                Logger.log("Failed to initialize engine")
                stopSelf()
            }
        }
    }

    private fun stopWakeWordDetection() {
        Logger.log("Stopping wake word detection", TAG)
        wakeWordEngine?.stopService()
        wakeWordEngine?.release()
        wakeWordEngine = null
    }

    private fun onWakeWordDetected() {
        Logger.log("Wake word detected!", TAG)
        
        // --- REACTIVE TRIGGER (AppStateManager) ---
        appStateManager.onWakeWordDetected()
        
        playHapticFeedback()
        // Removed startActivity(MainActivity) logic to keep Activity clean
        // and let System Overlay handle the UI globally.
    }

    private fun handleVoiceStateChange(state: VoiceState) {
        val currentUiState = appStateManager.uiState.value
        Logger.log("handleVoiceStateChange: $state, serviceListeningProp: ${currentUiState.isWakeWordServiceListening}", TAG)
        
        when (state) {
            VoiceState.IDLE -> {
                // Resume listening if service is logically active
                if (wakeWordEngine != null && currentUiState.isWakeWordServiceListening) {
                    val wakeWord = settingsManager.getWakeWord()
                    wakeWordEngine?.startListening()
                    updateNotification(languageManager.getString("ww_listening_for").format(wakeWord))
                }
            }
            VoiceState.LISTENING_WAKEWORD -> {
                // Active listening state, keep it running
                updateNotification(languageManager.getString("ww_listening_for").format(settingsManager.getWakeWord()))
            }
            VoiceState.LISTENING_COMMAND -> {
                wakeWordEngine?.stopListening()
                updateNotification(languageManager.getString("ww_paused_app_listening"))
            }
            VoiceState.PROCESSING -> {
                wakeWordEngine?.stopListening()
                updateNotification(languageManager.getString("ww_paused_ai_thinking"))
            }
            VoiceState.BENCHMARKING -> {
                wakeWordEngine?.stopListening()
                updateNotification(languageManager.getString("ww_paused_diagnostics"))
            }
            else -> {
                // For CLEANING or other states, keep it paused but alive
                wakeWordEngine?.stopListening()
            }
        }
    }

    private fun playHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Monitors microphone for wake word"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vox Commander")
            .setContentText("Wake Word Service is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vox Commander")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.voxcommander.app.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.voxcommander.app.action.STOP_WAKE_WORD"

        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
