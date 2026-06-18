package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.IntentPayload

class FastMapEngine(
    private val fastMapDao: FastMapDao
) : AssistantEngine {

    override suspend fun processCommand(spokenText: String): IntentPayload? {
        val rules = fastMapDao.getAllRules()

        for (rule in rules) {
            val regex = Regex(rule.triggerPattern, RegexOption.IGNORE_CASE)
            val matchResult = regex.find(spokenText)

            if (matchResult != null) {
                // Extract the first capture group if it exists
                val query = if (matchResult.groups.size > 1) {
                    matchResult.groups[1]?.value
                } else {
                    null
                }

                return IntentPayload(
                    category = rule.category,
                    actionType = rule.actionType,
                    target = rule.target,
                    query = query
                )
            }
        }

        return null
    }
}