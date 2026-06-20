package com.voxcommander.app.domain.engine.whisper

import com.voxcommander.app.domain.model.AppModel

data class WhisperModelInfo(
    override val id: String,
    override val label: String,
    override val url: String,
    override val sizeDescription: String,
    val isMultilingual: Boolean = true
) : AppModel {
    override val engineType: String get() = "Whisper"
}

object WhisperModelRegistry {
    val models = listOf(
        // Multilingual Models
        WhisperModelInfo(
            id = "tiny",
            label = "Tiny",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            sizeDescription = "~75 MB"
        ),
        WhisperModelInfo(
            id = "base",
            label = "Base",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            sizeDescription = "~145 MB"
        ),
        WhisperModelInfo(
            id = "small",
            label = "Small",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            sizeDescription = "~480 MB"
        ),
        // English-only Models
        WhisperModelInfo(
            id = "tiny.en",
            label = "Tiny (English Only)",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            sizeDescription = "~75 MB",
            isMultilingual = false
        ),
        WhisperModelInfo(
            id = "base.en",
            label = "Base (English Only)",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
            sizeDescription = "~145 MB",
            isMultilingual = false
        ),
        WhisperModelInfo(
            id = "small.en",
            label = "Small (English Only)",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin",
            sizeDescription = "~480 MB",
            isMultilingual = false
        )
    )

    fun getModelById(id: String): WhisperModelInfo? = models.find { it.id == id }
}
