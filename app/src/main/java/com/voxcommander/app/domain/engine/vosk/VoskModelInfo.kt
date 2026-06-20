package com.voxcommander.app.domain.engine.vosk

import com.voxcommander.app.domain.model.AppModel

data class VoskModelInfo(
    val name: String,
    override val url: String,
    val size: String?
) : AppModel {
    override val id: String get() = name
    override val label: String get() = name
    override val sizeDescription: String get() = size ?: "±50MB"
    override val engineType: String get() = "Vosk"
}
