package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy

import kotlinx.coroutines.flow.first

class FastMapEngine(
    private val fastMapDao: FastMapDao
) : AssistantEngine {

    override suspend fun processCommand(spokenText: String): NluIntent? {
        val rules = fastMapDao.getAllRules().first()

        for (rule in rules) {
            val regex = Regex(rule.triggerPattern, RegexOption.IGNORE_CASE)
            val matchResult = regex.find(spokenText)

            if (matchResult != null) {
                val extractedValue = if (matchResult.groups.size > 1) {
                    matchResult.groups[1]?.value
                } else {
                    null
                }

                // Map legacy actionType to new domain+action+targetApp
                val mapped = IntentTaxonomy.LegacyMapper.fromActionType(rule.actionType)
                val domain = mapped?.domain ?: rule.category
                val action = mapped?.action ?: rule.actionType
                val targetApp = mapped?.targetApp

                val params = mutableMapOf<String, String>()
                rule.artist?.let { params[NluIntent.PARAM_ARTIST] = it }
                (rule.track ?: extractedValue)?.let { params[NluIntent.PARAM_TRACK] = it }
                rule.album?.let { params[NluIntent.PARAM_ALBUM] = it }
                (rule.destination ?: if (rule.category == "maps") extractedValue else null)?.let { params[NluIntent.PARAM_DESTINATION] = it }

                return NluIntent(domain, action, targetApp, params, 1.0f)
            }
        }

        return null
    }
}
