package com.voxcommander.app.domain.intent.model

data class IntentPayload(
    val category: String,
    val actionType: String,
    val target: String,
    val query: String? = null
)