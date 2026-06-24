package com.voxcommander.app.service

import android.app.*
import android.content.Context
import android.content.Intent
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

class WakeWordService : Service() {

    private val TAG = "WakeWordService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var settingsManager: SettingsManager
    private lateinit var appStateManager: AppStateManager
    private lateinit var languageManager: com.voxcommander.app.domain.localization.LanguageManager
    private lateinit var voiceOverlayManager: com.voxcommander.app.ui.components.VoiceOverlayManager
    private var wakeWordEngine: WakeWordEngine? = null
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

        serviceScope.launch {
            appStateManager.uiState.collectLatest { uiState ->
                handleVoiceStateChange(uiState.voiceState)
            }
        }

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
            ACTION_STOP, ACTION_EXIT -> {
                stopWakeWordDetection()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                notificationManager?.cancel(NOTIFICATION_ID)
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
        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    private fun startWakeWordDetection() {
        Logger.log("Starting wake word detection", TAG)

        if (appStateManager.uiState.value.voiceState != VoiceState.IDLE) {
            Log.w(TAG, "Service was in state \${appStateManager.uiState.value.voiceState}. Forcing IDLE...")
            appStateManager.setVoiceState(VoiceState.IDLE)
        }

        startForeground(NOTIFICATION_ID, createNotification())

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

            if (modelPath == null) {
                Logger.log("No Vosk model available")
                stopSelf()
                return@launch
            }

            wakeWordEngine?.release()
            wakeWordEngine = WakeWordEngine(this@WakeWordService, settingsManager, appStateManager) {
                onWakeWordDetected()
            }

            val initialized = wakeWordEngine?.initialize(modelPath, wakeWord) ?: false
            if (initialized) {
                wakeWordEngine?.startListening()
                updateNotification()
            } else {
                Logger.log("Failed to initialize engine")
                stopSelf()
            }
        }
    }

    private fun stopWakeWordDetection() {
        Logger.log("Stopping wake word detection and releasing", TAG)
        wakeWordEngine?.release()
        wakeWordEngine = null
    }

    private fun pauseWakeWordDetection() {
        Logger.log("Pausing wake word detection", TAG)
        wakeWordEngine?.stopListening()
        appStateManager.setWakeWordServiceListening(false)
        updateNotification()
    }

    private fun resumeWakeWordDetection() {
        Logger.log("Resuming wake word detection", TAG)
        wakeWordEngine?.startListening()
        appStateManager.setWakeWordServiceListening(true)
        updateNotification()
    }

    private fun onWakeWordDetected() {
        Logger.log("Wake word detected!", TAG)
        appStateManager.onWakeWordDetected()
        playHapticFeedback()
    }

    private fun handleVoiceStateChange(state: VoiceState) {
        val currentUiState = appStateManager.uiState.value
        val isServiceActive = wakeWordEngine != null

        when (state) {
            VoiceState.IDLE -> {
                if (isServiceActive && currentUiState.isWakeWordServiceListening) {
                    wakeWordEngine?.startListening()
                    updateNotification()
                }
            }
            VoiceState.LISTENING_WAKEWORD -> {
                if (currentUiState.isWakeWordServiceListening) updateNotification()
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
            ).apply { description = "Monitors microphone for wake word" }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String? = null): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val uiState = appStateManager.uiState.value
        val isListening = uiState.isWakeWordServiceListening

        val finalContentText = contentText ?: if (isListening) {
            languageManager.getString("ww_listening_for").format(settingsManager.getWakeWord())
        } else {
            languageManager.getString("ww_paused")
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vox Commander")
            .setContentText(finalContentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))

        // 1. Action: Pause/Resume Toggle
        if (isListening) {
            val pauseIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_PAUSE }
            builder.addAction(android.R.drawable.ic_media_pause, languageManager.getString("notification_pause"), PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            val resumeIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_RESUME }
            builder.addAction(android.R.drawable.ic_media_play, languageManager.getString("notification_resume"), PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE))
        }

        // 2. Action: Stop (Exit)
        val stopIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, languageManager.getString("notification_stop"), PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE))

        return builder.build()
    }

    private fun updateNotification(text: String? = null) {
        notificationManager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    companion object {
        const val ACTION_START = "com.voxcommander.app.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.voxcommander.app.action.STOP_WAKE_WORD"
        const val ACTION_PAUSE = "com.voxcommander.app.action.PAUSE_WAKE_WORD"
        const val ACTION_RESUME = "com.voxcommander.app.action.RESUME_WAKE_WORD"
        const val ACTION_EXIT = "com.voxcommander.app.action.EXIT_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopService(context: Context) {
            context.startService(Intent(context, WakeWordService::class.java).apply { action = ACTION_STOP })
        }
    }
}
