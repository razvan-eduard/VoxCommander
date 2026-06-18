package com.voxcommander.app.domain.intent.registry

object IntentRegistry {
    private val categories: Map<String, List<String>> = mapOf(
        "MEDIA" to listOf("PLAY", "PAUSE", "STOP", "NEXT", "PREVIOUS"),
        "SYSTEM" to listOf("SETTING", "OPEN_APP", "GREETING", "WIFI_TOGGLE"),
        "APP" to listOf("OPEN", "CLOSE", "SEARCH"),
        "HOME" to listOf("LIGHT_ON", "LIGHT_OFF", "THERMOSTAT_SET"),
        "SEARCH" to listOf("QUERY", "IMAGE_SEARCH")
    )

    fun getAllCategories(): List<String> = categories.keys.toList()

    fun getActionsForCategory(category: String): List<String> =
        categories[category] ?: emptyList()
}