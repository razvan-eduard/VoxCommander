package com.voxcommander.app.domain.engine.vosk

import android.util.Log
import com.voxcommander.app.data.remote.VoskModelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VoskModelResult(
    val groups: List<VoskLanguageGroup>,
    val isOnline: Boolean,
    val errorMessage: String? = null
)

object VoskModelRegistry {
    private const val TAG = "VoskModelRegistry"
    private var cachedResult: VoskModelResult? = null

    suspend fun getModels(forceRefresh: Boolean = false): VoskModelResult = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedResult != null) {
            return@withContext cachedResult!!
        }

        return@withContext try {
            val result = VoskModelParser.fetchModels()
            result.fold(
                onSuccess = { groups ->
                    val newResult = VoskModelResult(groups, isOnline = true)
                    cachedResult = newResult
                    newResult
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to fetch Vosk models", error)
                    VoskModelResult(
                        groups = emptyList(),
                        isOnline = false,
                        errorMessage = error.message
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching Vosk models", e)
            VoskModelResult(
                groups = emptyList(),
                isOnline = false,
                errorMessage = e.message
            )
        }
    }
}
