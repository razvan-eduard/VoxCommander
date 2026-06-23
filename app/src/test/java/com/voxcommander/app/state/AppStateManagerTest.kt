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
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        every { settingsManager.getSelectedLlamaModelId() } returns "3.2-1b"
        every { settingsManager.isCurrentVoiceModelReady(any()) } returns false

        // Reset singleton
        val field = AppStateManager::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        
        stateManager = AppStateManager.getInstance(settingsManager, context)
    }

    @Test
    fun `when voice processor is updated, flow emits new value`() = runTest {
        stateManager.voiceProcessor.test {
            assertEquals(Strings.Processors.WHISPER_NEON, awaitItem())
            stateManager.setVoiceProcessor(Strings.Processors.VOSK)
            assertEquals(Strings.Processors.VOSK, awaitItem())
        }
    }

    @Test
    fun `when language is changed, StateFlow reflects it instantly`() = runTest {
        stateManager.voiceLanguage.test {
            assertEquals("en", awaitItem())
            stateManager.setVoiceLanguage("ro")
            assertEquals("ro", awaitItem())
        }
    }

    @Test
    fun `refreshTrigger increments on each refreshAll call`() = runTest {
        stateManager.refreshTrigger.test {
            val initial = awaitItem()
            stateManager.refreshAll()
            assertEquals(initial + 1, awaitItem())
        }
    }

    @Test
    fun `intentModelReady reflects AI processor changes reactively`() = runTest {
        stateManager.intentModelReady.test {
            // 1. Initial OpenAI
            assertTrue(awaitItem())

            // 2. Switch to Llama (Not ready)
            every { settingsManager.isModelDownloaded("3.2-1b") } returns false
            stateManager.setAiProcessor(Strings.AiProcessors.LLAMA_LOCAL)
            assertFalse(awaitItem())
            
            // 3. Download and Refresh
            every { settingsManager.isModelDownloaded("3.2-1b") } returns true
            stateManager.refreshAll()
            assertTrue(awaitItem())
        }
    }
}
