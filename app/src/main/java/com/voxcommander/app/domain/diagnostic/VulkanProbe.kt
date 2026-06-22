package com.voxcommander.app.domain.diagnostic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager

/**
 * Orchestrates the isolated Vulkan GPU self-test. Binds to [VulkanProbeService] (which
 * runs in a separate process), asks it to run a real GPU workload, and decides Vulkan
 * compatibility based on the outcome:
 *
 *  - result ok=true  -> compatible
 *  - result ok=false -> incompatible (GPU produced wrong/non-finite output)
 *  - process died before replying -> incompatible (native crash, isolated safely)
 *  - timeout         -> undecided, retried on next launch
 *
 * Runs at most once per install (gated by the probe-done flag).
 */
class VulkanProbe(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var gotResult = false

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == VulkanProbeService.MSG_RESULT) {
                gotResult = true
                finish(supported = msg.arg1 == 1, reason = "result")
            }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val m = Message.obtain(null, VulkanProbeService.MSG_RUN_TEST)
                m.replyTo = replyMessenger
                Messenger(service).send(m)
            } catch (e: Exception) {
                // Couldn't even start the test; don't penalize the device.
                Log.e(TAG, "Failed to start self-test: ${e.message}")
                finishUndecided("send-failed")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Process lost. If no result was received, the self-test crashed it natively.
            if (!gotResult) finish(supported = false, reason = "process-crash")
        }
    }

    fun start() {
        if (settingsManager.isVulkanProbeDone()) return
        try {
            val intent = Intent(context, VulkanProbeService::class.java)
            // Pass the Whisper model path for full inference test
            val modelPath = getWhisperModelPath()
            if (modelPath == null) {
                Log.w(TAG, "No Whisper model available for Vulkan probe")
                finishUndecided("no-model")
                return
            }
            intent.putExtra(VulkanProbeService.EXTRA_MODEL_PATH, modelPath)
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.w(TAG, "Could not bind VulkanProbeService")
                finishUndecided("bind-failed")
                return
            }
            handler.postDelayed({ if (!finished) finishUndecided("timeout") }, TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "start() failed: ${e.message}")
        }
    }

    private fun getWhisperModelPath(): String? {
        val customPath = settingsManager.getCustomWhisperModelPath()
        if (!customPath.isNullOrBlank()) {
            val file = java.io.File(customPath)
            if (file.exists()) return customPath
        }
        val selectedModelId = settingsManager.getSelectedWhisperModelId()
        val file = java.io.File(
            context.getExternalFilesDir(null),
            "whisper-model-$selectedModelId.bin"
        )
        return if (file.exists()) file.absolutePath else null
    }

    private fun finish(supported: Boolean, reason: String) {
        if (finished) return
        finished = true
        if (!supported) settingsManager.setVulkanIncompatible(true)
        settingsManager.setVulkanProbeDone(true)
        Log.d(TAG, "Vulkan self-test done: supported=$supported reason=$reason incompatible=${settingsManager.isVulkanIncompatible()}")
        unbind()
    }

    private fun finishUndecided(reason: String) {
        if (finished) return
        finished = true
        // Leave probe-done unset so we can retry on a future launch.
        Log.w(TAG, "Vulkan self-test undecided ($reason); will retry next launch")
        unbind()
    }

    private fun unbind() {
        try {
            context.unbindService(connection)
        } catch (_: Exception) {
            // Already unbound / never bound.
        }
    }

    companion object {
        private const val TAG = "VulkanProbe"
        private const val TIMEOUT_MS = 30_000L
    }
}
