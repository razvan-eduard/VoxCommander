package com.voxcommander.app.domain.engine.whisper

import com.voxcommander.app.domain.engine.SttEngine
import com.voxcommander.app.utils.Strings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface WhisperApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part = MultipartBody.Part.createFormData(WhisperSttEngine.PART_MODEL, WhisperSttEngine.MODEL_NAME)
    ): WhisperResponse
}

data class WhisperResponse(val text: String)

class WhisperSttEngine(private val apiKey: String) : SttEngine {
    
    private val api = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WhisperApi::class.java)

    override suspend fun transcribe(audio: ByteArray): String {
        val requestBody = audio.toRequestBody(MEDIA_TYPE_WAV.toMediaType())
        val filePart = MultipartBody.Part.createFormData(PART_FILE, FILENAME_WAV, requestBody)
        
        return try {
            val response = api.transcribe(AUTH_PREFIX + apiKey, filePart)
            response.text
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    override fun releaseHardware() {
        // No hardware resources to release for API engine
    }

    override fun releaseResources() {
        // API is persistent but doesn't hold large memory
    }

    companion object {
        private const val BASE_URL = "https://api.openai.com/"
        private const val AUTH_PREFIX = "Bearer "
        private const val MEDIA_TYPE_WAV = "audio/wav"
        private const val FILENAME_WAV = "audio.wav"
        private const val PART_FILE = Strings.Api.PART_FILE
        const val PART_MODEL = Strings.Api.PART_MODEL
        const val MODEL_NAME = "whisper-1"
    }
}
