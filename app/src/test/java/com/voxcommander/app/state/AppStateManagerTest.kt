package com.voxcommander.app.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppStateManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var stateManager: AppStateManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
        // Ensure a fresh instance for each test
        val field = AppStateManager::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        
        stateManager = AppStateManager.getInstance(settingsManager, context)
    }

    @Test
    fun `when voice processor is updated, StateFlow emits new value and it is persisted`() = runTest {
        // Act
        stateManager.setVoiceProcessor(Strings.Processors.VOSK)

        // Assert
        assertEquals(Strings.Processors.VOSK, stateManager.voiceProcessor.value)
        assertEquals(Strings.Processors.VOSK, settingsManager.getVoiceProcessor())
    }

    @Test
    fun `when language is changed, StateFlow reflects it instantly`() = runTest {
        // Act
        stateManager.setVoiceLanguage("ro")

        // Assert
        assertEquals("ro", stateManager.voiceLanguage.value)
    }

    @Test
    fun `voiceModelReady should be false if model file is missing`() = runTest {
        // Arrange
        stateManager.setVoiceProcessor(Strings.Processors.WHISPER_VULKAN)
        stateManager.setSelectedWhisperModelId("base")
        settingsManager.setModelDownloaded("base", false)

        // Force refresh to re-evaluate disk check
        stateManager.refreshAll()

        // Assert
        assertFalse(stateManager.voiceModelReady.value)
    }

    @Test
    fun `refreshTrigger increments on each refreshAll call`() = runTest {
        // Arrange
        val initialTrigger = stateManager.refreshTrigger.value

        // Act
        stateManager.refreshAll()
        val secondTrigger = stateManager.refreshTrigger.value
        
        stateManager.refreshAll()
        val thirdTrigger = stateManager.refreshTrigger.value

        // Assert
        assertTrue(secondTrigger > initialTrigger)
        assertTrue(thirdTrigger > secondTrigger)
    }

    @Test
    fun `intentModelReady reflects AI processor changes`() = runTest {
        // Act & Assert for OpenAI (Always ready)
        stateManager.setAiProcessor(Strings.AiProcessors.OPENAI)
        assertTrue(stateManager.intentModelReady.value)

        // Act & Assert for Llama (Depends on download)
        stateManager.setAiProcessor(Strings.AiProcessors.LLAMA_LOCAL)
        stateManager.setSelectedLlamaModelId("3.2-1b")
        settingsManager.setModelDownloaded("3.2-1b", false)
        stateManager.refreshAll()
        
        assertFalse(stateManager.intentModelReady.value)
    }
}
