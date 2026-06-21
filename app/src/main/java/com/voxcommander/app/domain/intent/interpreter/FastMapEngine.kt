package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.IntentPayload

import kotlinx.coroutines.flow.first

class FastMapEngine(
    private val fastMapDao: FastMapDao
) : AssistantEngine {

    override suspend fun processCommand(spokenText: String): IntentPayload? {
        val rules = fastMapDao.getAllRules().first()

        for (rule in rules) {
            val regex = Regex(rule.triggerPattern, RegexOption.IGNORE_CASE)
            val matchResult = regex.find(spokenText)

            if (matchResult != null) {
                // Extract the first capture group if it exists
                val extractedValue = if (matchResult.groups.size > 1) {
                    matchResult.groups[1]?.value
                } else {
                    null
                }

                // Map the rule fields to the intent payload
                return IntentPayload(
                    category = rule.category,
                    actionType = rule.actionType,
                    artist = rule.artist,
                    track = rule.track ?: extractedValue, // Capture group takes precedence if track is null
                    album = rule.album,
                    destination = rule.destination ?: if (rule.category == "maps") extractedValue else null
                )
            }
        }

        return null
    }
}
