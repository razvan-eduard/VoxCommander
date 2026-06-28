package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.utils.RegexGenerator

import kotlinx.coroutines.flow.first

class FastMapEngine(
    private val fastMapDao: FastMapDao
) : AssistantEngine {

    override suspend fun processCommand(spokenText: String): NluIntent? {
        val rules = fastMapDao.getAllRules().first()

        for (rule in rules) {
            // Build trigger regex from triggerWords (if any)
            val triggerRegexStr = RegexGenerator.fromWords(rule.triggerWords)
            val hasTrigger = triggerRegexStr.isNotBlank()
            val hasQuery = rule.queryWords.isNotEmpty()

            if (!hasTrigger && !hasQuery) continue

            // If no trigger, always match (query-only rule)
            val triggerMatched = if (!hasTrigger) {
                true
            } else {
                Regex(triggerRegexStr, RegexOption.IGNORE_CASE).containsMatchIn(spokenText)
            }

            if (triggerMatched) {
                // Build query
                val query = if (rule.lazyQuery) {
                    // Lazy: extract everything from spokenText except trigger words + app name
                    var remaining = spokenText
                    if (hasTrigger) {
                        remaining = remaining.replace(Regex(triggerRegexStr, RegexOption.IGNORE_CASE), " ")
                    }
                    // Remove app display name if present
                    val appEntry = com.voxcommander.app.domain.intent.registry.AppRegistry.resolveByPackage(rule.targetPackage)
                    if (appEntry != null) {
                        val appNamePattern = Regex("(?i)\\b${Regex.escape(appEntry.displayName)}\\b")
                        remaining = remaining.replace(appNamePattern, " ")
                    }
                    remaining.trim().replace(Regex("\\s+"), " ").ifBlank { null }
                } else {
                    rule.queryWords.joinToString(" ").ifBlank { null }
                }

                val params = mutableMapOf<String, String>()
                query?.let { params[NluIntent.PARAM_QUERY] = it }

                return NluIntent(
                    domain = "custom",
                    action = "launch",
                    targetApp = rule.targetPackage,
                    parameters = params,
                    confidence = 1.0f,
                    intentAction = rule.intentAction.ifBlank { null },
                    uriTemplate = rule.uriTemplate
                )
            }
        }

        return null
    }
}
