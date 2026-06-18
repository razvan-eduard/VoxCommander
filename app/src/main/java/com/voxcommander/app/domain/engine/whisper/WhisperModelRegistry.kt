package com.voxcommander.app.domain.engine.whisper

data class WhisperModelInfo(
    val id: String,
    val label: String,
    val url: String,
    val sizeDescription: String,
    val isMultilingual: Boolean = true
)

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
