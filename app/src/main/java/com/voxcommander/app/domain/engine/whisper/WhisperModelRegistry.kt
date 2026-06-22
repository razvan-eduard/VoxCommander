package com.voxcommander.app.domain.engine.whisper

import com.voxcommander.app.data.remote.RemoteModelRegistry
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
    val models: List<WhisperModelInfo>
        get() = RemoteModelRegistry.getWhisperModels().map {
            WhisperModelInfo(
                id = it.id,
                label = it.label,
                url = it.path,
                sizeDescription = if (it.size_mb > 0) "${it.size_mb} MB" else it.size_mb.toString(),
                isMultilingual = it.is_multilingual ?: true
            )
        }

    fun getModelById(id: String): WhisperModelInfo? = models.find { it.id == id }
}
