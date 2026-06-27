package com.voxcommander.app.domain.intent

import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.utils.Strings
import android.util.Log

/**
 * Master orchestrator for command interpretation.
 * Implements the Advanced Triple AI Architecture:
 * L1 (Fast Trigger Map - Regex) 
 *  -> L2 (Primary Selected Model - Could be Cloud or Local)
 *  -> L3 (User-defined Default Offline Fallback)
 */
class IntentDecisionMap(
    private val l1Engine: AssistantEngine,
    private val l2CloudEngine: AssistantEngine,
    private val l3LocalEngine: AssistantEngine,
    private val geminiNanoEngine: AssistantEngine,
    private val geminiCloudEngine: AssistantEngine,
    private val settingsRepo: SettingsRepository
) : AssistantEngine {

    private val TAG = Strings.Tags.INTENT_DECISION_MAP

    override suspend fun processCommand(spokenText: String): IntentPayload? {
        if (spokenText.isBlank()) return null
        
        Log.d(TAG, "🧠 Triple AI Brain: Processing '$spokenText'")

        // --- LEVEL 1: Fast Trigger Map (Local Regex) ---
        val l1Result = l1Engine.processCommand(spokenText)
        if (l1Result != null) {
            Log.d(TAG, "✅ L1 MATCH: $l1Result")
            return l1Result
        }

        // --- LEVEL 2: Primary Selected Model ---
        val snapshot = settingsRepo.getSettingsSnapshot()
        val isCloudIntelligenceEnabled = snapshot.cloudIntelligenceEnabled
        val primaryProcessor = snapshot.aiProcessor
        
        Log.d(TAG, "🔍 L1 Miss. Trying Primary L2 AI ($primaryProcessor)...")
        
        val l2Result = try {
            when (primaryProcessor) {
                Strings.AiProcessors.OPENAI -> {
                    if (isCloudIntelligenceEnabled) l2CloudEngine.processCommand(spokenText) else null
                }
                Strings.AiProcessors.GEMINI_NATIVE -> {
                    geminiNanoEngine.processCommand(spokenText)
                }
                Strings.AiProcessors.GEMINI_CLOUD -> {
                    if (isCloudIntelligenceEnabled) geminiCloudEngine.processCommand(spokenText) else null
                }
                else -> {
                    // JSON-defined LLM engines
                    if (com.voxcommander.app.data.remote.RemoteModelRegistry.isLlmEngine(primaryProcessor)) {
                        l3LocalEngine.processCommand(spokenText)
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "L2 Processing failed: ${e.message}")
            null
        }

        if (l2Result != null) {
            Log.d(TAG, "✅ L2 MATCH ($primaryProcessor): $l2Result")
            return l2Result
        }

        // --- LEVEL 3: Default Offline Fallback ---
        // Triggered if L2 fails (e.g., no internet for Cloud, or Llama failed/not present)
        val fallbackModel = snapshot.defaultIntentFallbackModel
        val fallbackProcessor = snapshot.defaultIntentFallbackProcessor

        if (fallbackModel != null && fallbackProcessor != null) {
            // Avoid re-running the same model if it was already tried in L2
            if (fallbackProcessor == primaryProcessor) {
                Log.d(TAG, "ℹ️ Fallback is same as Primary ($fallbackProcessor). Skipping redundant check.")
            } else {
                Log.d(TAG, "🏠 L2 Miss/Failure. Triggering L3 Offline Fallback ($fallbackProcessor)...")
                val l3Result = when (fallbackProcessor) {
                    Strings.AiProcessors.OPENAI -> l2CloudEngine.processCommand(spokenText)
                    Strings.AiProcessors.GEMINI_CLOUD -> geminiCloudEngine.processCommand(spokenText)
                    Strings.AiProcessors.GEMINI_NATIVE -> geminiNanoEngine.processCommand(spokenText)
                    else -> {
                        if (com.voxcommander.app.data.remote.RemoteModelRegistry.isLlmEngine(fallbackProcessor)) {
                            l3LocalEngine.processCommand(spokenText)
                        } else null
                    }
                }
                
                if (l3Result != null) {
                    Log.d(TAG, "✅ L3 FALLBACK MATCH: $l3Result")
                    return l3Result
                }
            }
        }

        Log.d(TAG, "🚫 NO INTENT DETECTED at any level.")
        return null
    }
}
