package com.voxcommander.app.domain.intent.interpreter

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy
import com.voxcommander.app.utils.Logger

/**
 * Parses LLM JSON output into NluIntent.
 * Supports both the new schema (domain, action, targetApp, parameters, confidence)
 * and the legacy schema (category, actionType, artist, track, album, destination).
 */
object NluIntentParser {

    private val TAG = "NluIntentParser"
    private val gson = Gson()

    fun parse(json: String): NluIntent? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.has("domain")) {
                parseNewSchema(obj)
            } else if (obj.has("category")) {
                parseLegacySchema(obj)
            } else {
                Logger.log("Unknown JSON schema — no 'domain' or 'category' key", TAG)
                null
            }
        } catch (e: Exception) {
            Logger.log("Failed to parse NluIntent JSON: ${e.message}", TAG)
            null
        }
    }

    private fun parseNewSchema(obj: JsonObject): NluIntent {
        val domain = obj.get("domain")?.asString ?: ""
        val action = obj.get("action")?.asString ?: ""
        val targetApp = if (obj.has("targetApp") && !obj.get("targetApp").isJsonNull) {
            obj.get("targetApp")?.asString
        } else null

        val parameters: Map<String, String> = if (obj.has("parameters") && !obj.get("parameters").isJsonNull) {
            val type = TypeToken.getParameterized(
                Map::class.java, String::class.java, String::class.java
            ).type
            gson.fromJson(obj.get("parameters"), type) ?: emptyMap()
        } else emptyMap()

        val confidence = if (obj.has("confidence") && !obj.get("confidence").isJsonNull) {
            obj.get("confidence").asFloat
        } else 1.0f

        return NluIntent(domain, action, targetApp, parameters, confidence)
    }

    /**
     * Legacy schema: {category, actionType, artist, track, album, destination}
     * Maps to NluIntent using IntentTaxonomy.LegacyMapper.
     */
    private fun parseLegacySchema(obj: JsonObject): NluIntent? {
        val category = obj.get("category")?.asString ?: ""
        val actionType = obj.get("actionType")?.asString ?: ""

        val mapped = IntentTaxonomy.LegacyMapper.fromActionType(actionType)
        val domain = mapped?.domain ?: category
        val action = mapped?.action ?: actionType
        val targetApp = mapped?.targetApp

        val params = mutableMapOf<String, String>()
        obj.get("artist")?.takeIf { !it.isJsonNull }?.asString?.let { params[NluIntent.PARAM_ARTIST] = it }
        obj.get("track")?.takeIf { !it.isJsonNull }?.asString?.let { params[NluIntent.PARAM_TRACK] = it }
        obj.get("album")?.takeIf { !it.isJsonNull }?.asString?.let { params[NluIntent.PARAM_ALBUM] = it }
        obj.get("destination")?.takeIf { !it.isJsonNull }?.asString?.let { params[NluIntent.PARAM_DESTINATION] = it }

        return NluIntent(domain, action, targetApp, params, 1.0f)
    }
}
