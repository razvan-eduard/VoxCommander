package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.domain.intent.model.IntentPayload

interface AssistantEngine {
    suspend fun processCommand(spokenText: String): IntentPayload?
}