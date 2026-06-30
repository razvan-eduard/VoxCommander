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
            val cleaned = extractJsonBlock(json)
            val obj = JsonParser.parseString(cleaned).asJsonObject
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

    /**
     * Extracts the first valid JSON object from an LLM response.
     * Handles markdown fences (```json ... ```) and multiple JSON blocks.
     */
    private fun extractJsonBlock(raw: String): String {
        var text = raw.trim()

        // Strip markdown code fences
        if (text.startsWith("```")) {
            // Remove opening fence (```json or ```)
            text = text.replace(Regex("^```[a-zA-Z]*\\s*"), "")
            // Remove all closing fences
            text = text.replace(Regex("```"), "")
        }

        // Find the first { ... } block (handles multiple JSON objects)
        val firstBrace = text.indexOf('{')
        if (firstBrace < 0) return text

        var depth = 0
        var endIdx = -1
        for (i in firstBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }

        if (endIdx >= 0) {
            return text.substring(firstBrace, endIdx + 1)
        }

        return text
    }

    private fun JsonObject.getSafeString(key: String): String {
        val el = get(key) ?: return ""
        if (el.isJsonNull) return ""
        return try { el.asString } catch (e: Exception) { "" }
    }

    private fun parseNewSchema(obj: JsonObject): NluIntent? {
        val domain = obj.getSafeString("domain")
        val action = obj.getSafeString("action")

        if (domain.isBlank() && action.isBlank()) {
            Logger.log("LLM returned null domain and action — treating as no intent", TAG)
            return null
        }
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

        val normalizedDomain = normalizeDomain(domain)
        val normalizedAction = normalizeAction(action)
        return NluIntent(normalizedDomain, normalizedAction, targetApp, parameters, confidence)
    }

    private val domainSynonyms = mapOf(
        "music" to IntentTaxonomy.Domains.AUDIO,
        "media" to IntentTaxonomy.Domains.AUDIO,
        "navigation" to IntentTaxonomy.Domains.MAPS,
        "map" to IntentTaxonomy.Domains.MAPS,
        "message" to IntentTaxonomy.Domains.MESSAGING,
        "chat" to IntentTaxonomy.Domains.MESSAGING,
        "volume" to IntentTaxonomy.Domains.SETTINGS,
        "device" to IntentTaxonomy.Domains.SETTINGS
    )

    private val actionSynonyms = mapOf(
        "search" to IntentTaxonomy.Actions.PLAY,
        "start" to IntentTaxonomy.Actions.PLAY,
        "skip" to IntentTaxonomy.Actions.NEXT,
        "previous" to IntentTaxonomy.Actions.PREV,
        "back" to IntentTaxonomy.Actions.PREV,
        "vol_up" to IntentTaxonomy.Actions.VOLUME_UP,
        "vol_down" to IntentTaxonomy.Actions.VOLUME_DOWN,
        "louder" to IntentTaxonomy.Actions.VOLUME_UP,
        "quieter" to IntentTaxonomy.Actions.VOLUME_DOWN
    )

    private fun normalizeDomain(domain: String): String {
        val lower = domain.lowercase().trim()
        return domainSynonyms[lower] ?: lower
    }

    private fun normalizeAction(action: String): String {
        val lower = action.lowercase().trim()
        return actionSynonyms[lower] ?: lower
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
