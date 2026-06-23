package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.domain.model.AppModel

/**
 * Dynamic LLM Model Info for NLU tasks (Qwen, Gemma, etc).
 * Maps from RemoteModelRegistry dynamic items.
 */
data class LlmModelInfo(
    override val id: String,
    override val label: String,
    override val sizeDescription: String,
    override val url: String,
    val engineTypeTag: String // e.g. "MEDIAPIPE_GENAI"
) : AppModel {
    override val engineType: String get() = "NLU_LLM"
}
