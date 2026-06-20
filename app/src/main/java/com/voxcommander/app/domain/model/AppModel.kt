package com.voxcommander.app.domain.model

/**
 * Common interface for all AI models (Whisper, Vosk, Llama) 
 * to enable unified UI management (dialogs, progress, etc).
 */
interface AppModel {
    val id: String
    val label: String
    val sizeDescription: String
    val url: String
    val engineType: String // "Whisper", "Vosk", "Llama"
}
