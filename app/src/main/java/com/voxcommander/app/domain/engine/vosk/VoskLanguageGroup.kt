package com.voxcommander.app.domain.engine.vosk

data class VoskLanguageGroup(
    val language: String,
    val models: List<VoskModelInfo>
)
