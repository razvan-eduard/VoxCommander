package com.voxcommander.app.domain.engine.vosk

import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.data.remote.VoskModelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VoskModelResult(
    val groups: List<VoskLanguageGroup>,
    val isOnline: Boolean,
    val errorMessage: String? = null
)

object VoskModelRegistry {
    private var cachedResult: VoskModelResult? = null

    /**
     * Fetches Vosk models. Now integrates data from RemoteModelRegistry.
     */
    suspend fun getModels(forceRefresh: Boolean = false): VoskModelResult = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedResult != null) {
            return@withContext cachedResult!!
        }

        // Group by language code from JSON
        val remoteGroups = RemoteModelRegistry.getVoskModels()
            .groupBy { it.lang_code ?: "unknown" }
            .map { (lang, items) ->
                VoskLanguageGroup(lang.uppercase(), items.map { 
                    VoskModelInfo(it.id, it.path, it.size_label ?: "${it.size_mb} MB")
                })
            }

        val result = if (remoteGroups.isNotEmpty()) {
            VoskModelResult(remoteGroups, isOnline = true)
        } else {
            // Fallback to legacy parser if no remote models found in JSON
            VoskModelParser.fetchModels().fold(
                onSuccess = { VoskModelResult(it, isOnline = true) },
                onFailure = { VoskModelResult(emptyList(), isOnline = false, errorMessage = it.message) }
            )
        }
        
        cachedResult = result
        return@withContext result
    }
}
