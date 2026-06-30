package com.voxcommander.app.service

interface IWakeWordEngine {
    suspend fun initialize(modelPath: String, wakeWord: String): Boolean
    fun startListening(): Boolean
    fun stopListening()
    fun stopService()
    fun release()
}
