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
import androidx.media.app.NotificationCompat as MediaNotificationCompat
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
            ACTION_PAUSE -> pauseWakeWordDetection()
            ACTION_RESUME -> resumeWakeWordDetection()
            ACTION_STOP -> stopWakeWordDetectionLocally()
            ACTION_EXIT -> {
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
        Logger.log("Stopping wake word detection and releasing", TAG)
        wakeWordEngine?.stopService()
        wakeWordEngine?.release()
        wakeWordEngine = null
    }

    private fun stopWakeWordDetectionLocally() {
        Logger.log("Stopping wake word detection (Service stays alive)", TAG)
        wakeWordEngine?.stopService()
        wakeWordEngine?.release()
        wakeWordEngine = null
        updateNotification(languageManager.getString("service_stopped"))
    }

    private fun pauseWakeWordDetection() {
        Logger.log("Pausing wake word detection", TAG)
        wakeWordEngine?.stopListening()
        appStateManager.setWakeWordServiceListening(false)
        updateNotification(languageManager.getString("ww_paused"))
    }

    private fun resumeWakeWordDetection() {
        Logger.log("Resuming wake word detection", TAG)
        val wakeWord = settingsManager.getWakeWord()
        wakeWordEngine?.startListening()
        updateNotification(languageManager.getString("ww_listening_for").format(wakeWord))
        appStateManager.setWakeWordServiceListening(true)
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
        
        // If engine is null, the service is globally "Stopped" in notification
        val isServiceActive = wakeWordEngine != null

        when (state) {
            VoiceState.IDLE -> {
                // Resume listening ONLY if service was NOT manually paused/stopped
                if (isServiceActive && currentUiState.isWakeWordServiceListening) {
                    val wakeWord = settingsManager.getWakeWord()
                    wakeWordEngine?.startListening()
                    updateNotification() // Will automatically use "Listening for..." or "Paused" logic
                }
            }
            VoiceState.LISTENING_WAKEWORD -> {
                // Already listening, just ensuring notification is correct
                if (currentUiState.isWakeWordServiceListening) {
                    updateNotification()
                }
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

    private fun createNotification(contentText: String? = null): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val uiState = appStateManager.uiState.value
        val isListening = uiState.isWakeWordServiceListening
        val isServiceActive = wakeWordEngine != null

        val finalContentText = contentText ?: when {
            isListening -> languageManager.getString("ww_listening_for").format(settingsManager.getWakeWord())
            isServiceActive -> languageManager.getString("ww_paused")
            else -> languageManager.getString("service_stopped")
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vox Commander")
            .setContentText(finalContentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))

        // Action Buttons logic
        if (isServiceActive) {
            if (isListening) {
                // ACTIVE -> Show [Pause] [Stop]
                val pauseIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_PAUSE }
                val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_pause, languageManager.getString("notification_pause"), pausePendingIntent)
            } else {
                // PAUSED -> Show [Resume] [Stop]
                val resumeIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_RESUME }
                val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_play, languageManager.getString("notification_resume"), resumePendingIntent)
            }
            
            // Stop button (local stop, keeps service alive)
            val stopIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP }
            val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, languageManager.getString("notification_stop"), stopPendingIntent)
        } else {
            // STOPPED -> Show [Start] [Exit]
            val runIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_START }
            val runPendingIntent = PendingIntent.getService(this, 4, runIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_play, languageManager.getString("notification_start"), runPendingIntent)

            val exitIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_EXIT }
            val exitPendingIntent = PendingIntent.getService(this, 5, exitIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, languageManager.getString("notification_exit"), exitPendingIntent)
            
            // For stopped state, maybe different compact actions
            builder.setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))
        }

        return builder.build()
    }

    private fun updateNotification(text: String? = null) {
        val notification = createNotification(text)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.voxcommander.app.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.voxcommander.app.action.STOP_WAKE_WORD"
        const val ACTION_PAUSE = "com.voxcommander.app.action.PAUSE_WAKE_WORD"
        const val ACTION_RESUME = "com.voxcommander.app.action.RESUME_WAKE_WORD"
        const val ACTION_EXIT = "com.voxcommander.app.action.EXIT_SERVICE"

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
