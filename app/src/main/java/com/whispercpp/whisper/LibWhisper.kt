package com.whispercpp.whisper

import android.content.res.AssetManager
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

    suspend fun transcribeData(data: FloatArray, threads: Int, language: String? = null, printTimestamp: Boolean = true): String = withContext(scope.coroutineContext) {
        if (ptr == 0L) return@withContext "Error: Context released"
        
        Log.d(LOG_TAG, "Transcribing with $threads threads, lang: ${language ?: "auto"}")
        WhisperLib.fullTranscribe(ptr, threads, data, language)
        
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                if (printTimestamp) {
                    val t0 = WhisperLib.getTextSegmentT0(ptr, i)
                    val t1 = WhisperLib.getTextSegmentT1(ptr, i)
                    val textTimestamp = "[${toTimestamp(t0)} --> ${toTimestamp(t1)}]"
                    val textSegment = WhisperLib.getTextSegment(ptr, i)
                    append("$textTimestamp: $textSegment\n")
                } else {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }
        }.trim()
    }

    fun release() {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        release()
    }

    companion object {
        fun createContextFromFile(filePath: String, useGpu: Boolean): WhisperContext {
            val ptr = WhisperLib.initContext(filePath, useGpu)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context with path $filePath (GPU=$useGpu)")
            }
            return WhisperContext(ptr)
        }
    }
}

/**
 * JNI BRIDGE: Low-level access to the compiled C++ code.
 */
class WhisperLib {
    companion object {
        private var isLoaded = false

        fun load(): Boolean {
            if (isLoaded) return true
            return try {
                Log.d(LOG_TAG, "Loading native libraries (libwhisper.so containing JNI)...")
                
                // Load dependencies first
                System.loadLibrary("omp")
                System.loadLibrary("ggml")
                System.loadLibrary("ggml-base")
                System.loadLibrary("ggml-cpu")
                System.loadLibrary("ggml-vulkan")
                
                // Load the main library which now contains our JNI symbols
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

        // JNI Methods - Signature matching libwhisper.so (contains JNI Patch)
        external fun initContext(modelPath: String, useGpu: Boolean): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String?)
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
