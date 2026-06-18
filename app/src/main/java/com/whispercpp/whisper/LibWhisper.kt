package com.whispercpp.whisper

import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors

private const val LOG_TAG = "LibWhisper"

/**
 * HIGH-LEVEL CONTEXT: Manages Whisper engine lifecycle and thread-safe transcription.
 */
class WhisperContext private constructor(private var ptr: Long) {
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(data: FloatArray, threads: Int, printTimestamp: Boolean = true): String = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        Log.d(LOG_TAG, "Transcribing with $threads threads")
        WhisperLib.fullTranscribe(ptr, threads, data)
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                if (printTimestamp) {
                    val textTimestamp = "[${toTimestamp(WhisperLib.getTextSegmentT0(ptr, i))} --> ${toTimestamp(WhisperLib.getTextSegmentT1(ptr, i))}]"
                    val textSegment = WhisperLib.getTextSegment(ptr, i)
                    append("$textTimestamp: $textSegment\n")
                } else {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }
        }
    }

    fun release() {
        runBlocking {
            withContext(scope.coroutineContext) {
                if (ptr != 0L) {
                    WhisperLib.freeContext(ptr)
                    ptr = 0
                }
            }
        }
    }

    protected fun finalize() {
        release()
    }

    companion object {
        fun createContextFromFile(filePath: String, useGpu: Boolean): WhisperContext {
            val ptr = WhisperLib.initContext(filePath, useGpu)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context with path $filePath")
            }
            return WhisperContext(ptr)
        }
    }
}

/**
 * JNI BRIDGE: Low-level access to the compiled C++ code.
 * Aligned with symbols: Java_com_whispercpp_whisper_WhisperLib_00024Companion_...
 */
class WhisperLib {
    companion object {
        private var isLoaded = false

        fun load(): Boolean {
            if (isLoaded) return true
            return try {
                Log.d(LOG_TAG, "Loading native libraries...")
                System.loadLibrary("ggml-base")
                System.loadLibrary("ggml-vulkan")
                System.loadLibrary("ggml-cpu")
                System.loadLibrary("ggml")
                System.loadLibrary("whisper")
                isLoaded = true
                Log.d(LOG_TAG, "Libraries loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(LOG_TAG, "Native load failed: ${e.message}")
                false
            }
        }

        fun isReady(): Boolean = isLoaded

        // CRITICAL: NO @JvmStatic to match _00024Companion naming in our compiled libwhisper.so
        external fun initContext(modelPath: String, useGpu: Boolean): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSystemInfo(): String
    }
}

private fun toTimestamp(t: Long, comma: Boolean = false): String {
    var msec = t * 10
    val hr = msec / (1000 * 60 * 60)
    msec -= hr * (1000 * 60 * 60)
    val min = msec / (1000 * 60)
    msec -= min * (1000 * 60)
    val sec = msec / 1000
    msec -= sec * 1000

    val delimiter = if (comma) "," else "."
    return String.format("%02d:%02d:%02d%s%03d", hr.toInt(), min.toInt(), sec.toInt(), delimiter, msec.toInt())
}
