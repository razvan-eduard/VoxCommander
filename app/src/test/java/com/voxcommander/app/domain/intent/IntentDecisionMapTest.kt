package com.voxcommander.app.domain.intent

import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.utils.Strings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntentDecisionMapTest {

    private lateinit var l1Engine: AssistantEngine
    private lateinit var l2CloudEngine: AssistantEngine
    private lateinit var l3LocalEngine: AssistantEngine
    private lateinit var geminiEngine: AssistantEngine
    private lateinit var settingsManager: SettingsManager
    private lateinit var decisionMap: IntentDecisionMap

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        l1Engine = mockk()
        l2CloudEngine = mockk()
        l3LocalEngine = mockk()
        geminiEngine = mockk()
        settingsManager = mockk(relaxed = true)

        decisionMap = IntentDecisionMap(
            l1Engine,
            l2CloudEngine,
            l3LocalEngine,
            geminiEngine,
            settingsManager
        )

        // Mock default behavior for settings to avoid nulls
        every { settingsManager.isCloudIntelligenceEnabled() } returns true
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        every { settingsManager.getDefaultIntentFallbackModel() } returns null
        every { settingsManager.getDefaultIntentFallbackProcessor() } returns null
    }

    @Test
    fun `when L1 engine matches, should return result immediately without calling other engines`() = runTest {
        // Arrange
        val command = "pune muzica"
        val expectedPayload = IntentPayload(category = "music", actionType = "play")
        
        coEvery { l1Engine.processCommand(command) } returns expectedPayload
        
        // Act
        val result = decisionMap.processCommand(command)

        // Assert
        assertEquals(expectedPayload, result)
        coVerify(exactly = 1) { l1Engine.processCommand(command) }
        coVerify(exactly = 0) { l2CloudEngine.processCommand(any()) }
        coVerify(exactly = 0) { l3LocalEngine.processCommand(any()) }
    }

    @Test
    fun `when L1 misses and primary is OpenAI, should call L2 Cloud engine`() = runTest {
        // Arrange
        val command = "vreau la brasov"
        val expectedPayload = IntentPayload(category = "navigation", actionType = "navigate", destination = "brasov")
        
        coEvery { l1Engine.processCommand(command) } returns null
        coEvery { l2CloudEngine.processCommand(command) } returns expectedPayload
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        
        // Act
        val result = decisionMap.processCommand(command)

        // Assert
        assertEquals(expectedPayload, result)
        coVerify { l1Engine.processCommand(command) }
        coVerify { l2CloudEngine.processCommand(command) }
    }

    @Test
    fun `when L2 Cloud fails and fallback is Llama, should call L3 fallback`() = runTest {
        // Arrange
        val command = "cat e ceasul"
        val expectedPayload = IntentPayload(category = "system", actionType = "time")
        
        coEvery { l1Engine.processCommand(command) } returns null
        coEvery { l2CloudEngine.processCommand(command) } returns null // Cloud fail (e.g. no internet)
        coEvery { l3LocalEngine.processCommand(command) } returns expectedPayload
        
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        every { settingsManager.getDefaultIntentFallbackProcessor() } returns Strings.AiProcessors.LLAMA_LOCAL
        every { settingsManager.getDefaultIntentFallbackModel() } returns "3.2-1b"

        // Act
        val result = decisionMap.processCommand(command)

        // Assert
        assertEquals(expectedPayload, result)
        coVerify { l2CloudEngine.processCommand(command) }
        coVerify { l3LocalEngine.processCommand(command) }
    }

    @Test
    fun `when all engines fail, should return null`() = runTest {
        // Arrange
        coEvery { l1Engine.processCommand(any()) } returns null
        coEvery { l2CloudEngine.processCommand(any()) } returns null
        coEvery { l3LocalEngine.processCommand(any()) } returns null
        
        // Act
        val result = decisionMap.processCommand("bla bla")

        // Assert
        assertNull(result)
    }
}
