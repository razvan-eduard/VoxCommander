package com.voxcommander.app.data.remote

import android.util.Log
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelInfo
import com.voxcommander.app.utils.Strings
import org.jsoup.Jsoup
import java.io.IOException

object VoskModelParser {
    private const val TAG = Strings.Tags.VOSK_MODEL_PARSER
    private const val VOSK_MODELS_URL = Strings.Urls.VOSK_MODELS
    private const val TIMEOUT_MS = 30000
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Parses the Vosk models page.
     * CRITICAL: Uses .maxBodySize(0) because the models page is very large!
     */
    fun fetchModels(): Result<List<VoskLanguageGroup>> {
        return try {
            Log.d(TAG, "Fetching Vosk models (Unlimited body size)...")
            val doc = Jsoup.connect(VOSK_MODELS_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(0) // UNLIMITED size to prevent truncation
                .get()
            
            val languageGroups = mutableListOf<VoskLanguageGroup>()
            
            // Select all potential headers and tables in the document
            val elements = doc.select("h2, h3, h4, table")
            Log.d(TAG, "Found ${elements.size} elements to process")
            
            var currentLanguage: String? = null
            
            for (element in elements) {
                val tag = element.tagName()
                
                // 1. Detect Language Change
                if (tag.startsWith("h")) {
                    val text = element.text().trim()
                    // Set context for any non-empty header that isn't the page title
                    if (text.isNotEmpty() && !text.contains("Vosk", ignoreCase = true)) {
                        currentLanguage = text
                        Log.v(TAG, "Current language context: $currentLanguage")
                    }
                }
                
                // 2. Process Table
                if (tag == "table" && currentLanguage != null) {
                    val models = mutableListOf<VoskModelInfo>()
                    val rows = element.select("tbody tr")
                    
                    for (row in rows) {
                        val cells = row.select("td")
                        if (cells.size < 2) continue

                        val link = cells[0].selectFirst("a")
                        val size = cells[1].text().trim()
                        
                        if (link != null) {
                            val modelName = link.text().trim()
                            val downloadUrl = link.absUrl("href")
                            
                            if (downloadUrl.endsWith(".zip")) {
                                models.add(VoskModelInfo(modelName, downloadUrl, size))
                            }
                        }
                    }
                    
                    if (models.isNotEmpty()) {
                        val existingGroup = languageGroups.find { it.language == currentLanguage }
                        if (existingGroup != null) {
                            val updatedModels = (existingGroup.models + models).distinctBy { it.url }
                            languageGroups[languageGroups.indexOf(existingGroup)] = existingGroup.copy(models = updatedModels)
                        } else {
                            languageGroups.add(VoskLanguageGroup(currentLanguage!!, models))
                        }
                    }
                }
            }
            
            if (languageGroups.isEmpty()) {
                Log.e(TAG, "Parsing yielded 0 models. Check if site is reachable or structure changed.")
                Result.failure(Exception("Could not find any models on the page."))
            } else {
                Log.d(TAG, "Successfully parsed ${languageGroups.size} language groups.")
                Result.success(languageGroups)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
