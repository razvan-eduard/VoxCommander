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
    private var wakeWordEngine: WakeWordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    private val CHANNEL_ID = "wake_word_service_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService created")
        Logger.log("WakeWordService created")
        settingsManager = SettingsManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxCommander:WakeWordLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        serviceScope.launch {
            VoiceStateManager.state.collectLatest { state ->
                handleVoiceStateChange(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "WakeWordService onStartCommand: $action")
        Logger.log("WakeWordService onStartCommand: $action")

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
        Log.d(TAG, "WakeWordService destroyed")
        Logger.log("WakeWordService destroyed")
        stopWakeWordDetection()
        wakeLock?.release()
        VoiceStateManager.setIdle()
    }

    private fun startWakeWordDetection() {
        Log.d(TAG, "Starting wake word detection")
        Logger.log("startWakeWordDetection called")
        
        if (!VoiceStateManager.canStartWakeWord()) {
            Log.w(TAG, "Cannot start wake word detection: current state is ${VoiceStateManager.state.value}")
            Logger.log("Cannot start - not IDLE state: ${VoiceStateManager.state.value}")
            return
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

            Log.d(TAG, "Wake word: $wakeWord, Path: ${modelPath ?: "null"}")
            Logger.log("Wake word: $wakeWord, Path: ${modelPath ?: "null"}")

            if (modelPath == null) {
                Logger.log("No Vosk model available")
                stopSelf()
                return@launch
            }

            wakeWordEngine = WakeWordEngine(this@WakeWordService, settingsManager) {
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
        Log.d(TAG, "Stopping wake word detection")
        wakeWordEngine?.stopListening()
        wakeWordEngine?.release()
        wakeWordEngine = null
        VoiceStateManager.setIdle()
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word detected!")
        Logger.log("Wake word detected!")
        
        VoiceStateManager.onWakeWordDetected()
        playHapticFeedback()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("action", "WAKE_WORD_DETECTED")
        }
        startActivity(intent)
    }

    private fun handleVoiceStateChange(state: VoiceStateManager.VoiceState) {
        when (state) {
            VoiceStateManager.VoiceState.IDLE -> {
                if (wakeWordEngine != null) {
                    wakeWordEngine?.startListening()
                    updateNotification("Listening for '${settingsManager.getWakeWord()}'...")
                }
            }
            VoiceStateManager.VoiceState.LISTENING_COMMAND -> {
                wakeWordEngine?.stopListening()
                updateNotification("Wake Word paused (App is listening)")
            }
            else -> {}
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
                NotificationManager.IMPORTANCE_LOW
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
