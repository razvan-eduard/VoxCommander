package com.voxcommander.app.domain.engine

interface SttEngine {
    /**
     * Transcribes audio data to text.
     */
    suspend fun transcribe(audio: ByteArray): String

    /**
     * Processes a chunk of audio and returns a partial transcription if available.
     */
    suspend fun processChunk(audio: ByteArray): String? = null
}
