package com.voxcommander.app.state

import android.content.Context
import app.cash.turbine.test
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.utils.Strings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var stateManager: AppStateManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        
        // Mock default values to avoid nulls
        every { settingsManager.getVoiceProcessor() } returns Strings.Processors.WHISPER_NEON
        every { settingsManager.getVoiceLanguage() } returns "en"
        every { settingsManager.getSelectedWhisperModelId() } returns "base"
        every { settingsManager.getSelectedVoskModelName() } returns null
        every { settingsManager.getCustomWhisperModelPath() } returns null
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        every { settingsManager.getSelectedLlamaModelId() } returns "3.2-1b"
        every { settingsManager.isCloudIntelligenceEnabled() } returns true
        every { settingsManager.getWakeWord() } returns "hello"
        every { settingsManager.isWakeWordEnabled() } returns false
        every { settingsManager.getApiKey() } returns null
        every { settingsManager.isModelDownloaded(any()) } returns false
        every { settingsManager.isGeminiIncompatible() } returns false
        every { settingsManager.getCustomVoskModelPath(any()) } returns null

        // Reset singleton
        val field = AppStateManager::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        
        stateManager = AppStateManager.getInstance(settingsManager, context)
    }

    @Test
    fun `when voice processor is updated, uiState emits new value`() = runTest {
        stateManager.uiState.test {
            val initial = awaitItem()
            assertEquals(Strings.Processors.WHISPER_NEON, initial.voiceProcessor)
            
            stateManager.setVoiceProcessor(Strings.Processors.VOSK)
            val updated = awaitItem()
            assertEquals(Strings.Processors.VOSK, updated.voiceProcessor)
        }
    }

    @Test
    fun `when language is changed, uiState reflects it instantly`() = runTest {
        stateManager.uiState.test {
            val initial = awaitItem()
            assertEquals("en", initial.voiceLanguage)
            
            stateManager.setVoiceLanguage("ro")
            val updated = awaitItem()
            assertEquals("ro", updated.voiceLanguage)
        }
    }

    @Test
    fun `refreshAll reloads all state from SettingsManager`() = runTest {
        stateManager.uiState.test {
            val initial = awaitItem()
            assertEquals(Strings.Processors.WHISPER_NEON, initial.voiceProcessor)
            
            // Change mock values
            every { settingsManager.getVoiceProcessor() } returns Strings.Processors.VOSK
            every { settingsManager.getVoiceLanguage() } returns "ro"
            
            stateManager.refreshAll()
            val updated = awaitItem()
            assertEquals(Strings.Processors.VOSK, updated.voiceProcessor)
            assertEquals("ro", updated.voiceLanguage)
        }
    }

    @Test
    fun `intentModelReady reflects AI processor changes reactively`() = runTest {
        stateManager.uiState.test {
            // 1. Initial OpenAI (always ready)
            val initial = awaitItem()
            assertTrue(initial.intentModelReady)

            // 2. Switch to NLU_LOCAL (Not ready - model not downloaded)
            every { settingsManager.isModelDownloaded("3.2-1b") } returns false
            stateManager.setAiProcessor(Strings.AiProcessors.NLU_LOCAL)
            val updated = awaitItem()
            assertFalse(updated.intentModelReady)
            
            // 3. Mark model as downloaded and refresh
            every { settingsManager.isModelDownloaded("3.2-1b") } returns true
            stateManager.refreshAll()
            val refreshed = awaitItem()
            assertTrue(refreshed.intentModelReady)
        }
    }

    @Test
    fun `voiceModelReady reflects processor and model changes`() = runTest {
        stateManager.uiState.test {
            // 1. Initial WHISPER_NEON with no model downloaded
            val initial = awaitItem()
            assertFalse(initial.voiceModelReady)

            // 2. Mark model as downloaded
            every { settingsManager.isModelDownloaded("base") } returns true
            stateManager.refreshAll()
            val updated = awaitItem()
            assertTrue(updated.voiceModelReady)

            // 3. Switch to VOSK with no model
            every { settingsManager.getSelectedVoskModelName() } returns null
            every { settingsManager.isModelDownloaded(any()) } returns false
            stateManager.setVoiceProcessor(Strings.Processors.VOSK)
            val voskState = awaitItem()
            assertFalse(voskState.voiceModelReady)

            // 4. Switch to GOOGLE (always ready)
            stateManager.setVoiceProcessor(Strings.Processors.GOOGLE)
            val googleState = awaitItem()
            assertTrue(googleState.voiceModelReady)
        }
    }

    @Test
    fun `multiple state updates are atomic`() = runTest {
        stateManager.uiState.test {
            val initial = awaitItem()
            
            // Perform multiple updates
            stateManager.setVoiceProcessor(Strings.Processors.VOSK)
            stateManager.setVoiceLanguage("ro")
            stateManager.setCloudIntelligenceEnabled(false)
            
            // Each update should emit a new state
            val state1 = awaitItem()
            assertEquals(Strings.Processors.VOSK, state1.voiceProcessor)
            
            val state2 = awaitItem()
            assertEquals("ro", state2.voiceLanguage)
            
            val state3 = awaitItem()
            assertFalse(state3.cloudIntelligenceEnabled)
        }
    }
}
