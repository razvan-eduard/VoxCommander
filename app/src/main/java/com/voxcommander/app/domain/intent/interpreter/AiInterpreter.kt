package com.voxcommander.app.domain.intent.interpreter

import android.content.Context
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.utils.Strings
import android.util.Log

/**
 * L2 Engine: Semantic AI interpretation Dispatcher.
 * Routes to OpenAI (Cloud), Llama (Local), or Gemini (Native).
 */
class AiInterpreter(
    private val context: Context,
    private val settingsManager: SettingsManager
) : AssistantEngine {
    
    private val TAG = "AiInterpreter"
    
    // We'll lazy-load these as needed
    private val llamaInterpreter by lazy { LocalLlmInterpreter(context, settingsManager) }
    private val openAiInterpreter by lazy { OpenAiInterpreter(settingsManager) }
    // private val geminiInterpreter by lazy { GeminiInterpreter(context) }

    override suspend fun processCommand(spokenText: String): IntentPayload? {
        if (!settingsManager.isCloudIntelligenceEnabled()) return null
        
        val processor = settingsManager.getAiProcessor()
        Log.d(TAG, "🤖 Dispatching L2 command to: $processor")

        return when (processor) {
            Strings.AiProcessors.LLAMA_LOCAL -> llamaInterpreter.processCommand(spokenText)
            Strings.AiProcessors.OPENAI -> openAiInterpreter.processCommand(spokenText)
            // Strings.AiProcessors.GEMINI_NATIVE -> geminiInterpreter.processCommand(spokenText)
            else -> {
                Log.w(TAG, "Processor $processor not fully implemented yet. Returning mock.")
                mockL2Response(spokenText)
            }
        }
    }

    private fun mockL2Response(spokenText: String): IntentPayload? {
        return when {
            spokenText.contains("help", ignoreCase = true) -> {
                IntentPayload(category = "settings", actionType = "help")
            }
            else -> null
        }
    }
}
