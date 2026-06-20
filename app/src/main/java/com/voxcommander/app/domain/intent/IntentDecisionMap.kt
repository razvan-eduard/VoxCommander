package com.voxcommander.app.domain.intent

import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.model.IntentPayload
import android.util.Log

/**
 * Master orchestrator for command interpretation.
 * Implements the hierarchical routing logic:
 * L1 (Fast Trigger Map - Regex) -> L2 (AI Fallback)
 */
class IntentDecisionMap(
    private val l1Engine: AssistantEngine,
    private val l2Engine: AssistantEngine? = null
) : AssistantEngine {

    private val TAG = "IntentDecisionMap"

    override suspend fun processCommand(spokenText: String): IntentPayload? {
        if (spokenText.isBlank()) return null
        
        Log.d(TAG, "🧠 Decision Brain: Processing '$spokenText'")

        // STEP 1: L1 - Fast Trigger Map (Local Regex)
        // Instant, private, no battery cost.
        val l1Result = l1Engine.processCommand(spokenText)
        if (l1Result != null) {
            Log.d(TAG, "✅ L1 MATCH FOUND: $l1Result")
            return l1Result
        }

        Log.d(TAG, "❌ L1 MISS. Falling back to L2 (AI)...")

        // STEP 2: L2 - AI Fallback (Local LLM or Cloud)
        // Semantic understanding when exact patterns fail.
        val l2Result = l2Engine?.processCommand(spokenText)
        if (l2Result != null) {
            Log.d(TAG, "✅ L2 MATCH FOUND: $l2Result")
            return l2Result
        }

        Log.d(TAG, "🚫 NO INTENT DETECTED at any level.")
        return null
    }
}
