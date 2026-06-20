package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.domain.model.AppModel

data class LlamaModelInfo(
    override val id: String,
    override val label: String,
    override val sizeDescription: String,
    override val url: String,
    val ramRequirement: String
) : AppModel {
    override val engineType: String get() = "Llama"
}

object LlamaModelRegistry {
    val models = listOf(
        LlamaModelInfo(
            id = "3.2-1b",
            label = "Llama 3.2 1B (Fast)",
            sizeDescription = "1.2 GB",
            url = "https://huggingface.co/google/gemma-2b-it-android/resolve/main/llama-3.2-1b-it.bin",
            ramRequirement = "2GB+ RAM"
        ),
        LlamaModelInfo(
            id = "3.2-3b",
            label = "Llama 3.2 3B (Smart)",
            sizeDescription = "2.5 GB",
            url = "https://huggingface.co/google/gemma-2b-it-android/resolve/main/llama-3.2-3b-it.bin",
            ramRequirement = "8GB+ RAM"
        )
    )
    
    const val DEFAULT_MODEL = "3.2-1b"
}
