package com.voxcommander.app.domain

import android.util.Log
import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.IntentDecisionMap
import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import com.voxcommander.app.testutil.TestDataFactory
import com.voxcommander.app.utils.Strings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * TRUE Integration Test: Verifies the interaction between REAL components.
 * Chain: Spoken Text -> Real FastMapEngine (Regex) -> IntentDecisionMap -> IntentPayload
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationChainTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var fastMapDao: FastMapDao
    
    private lateinit var realL1Engine: FastMapEngine
    private lateinit var l2CloudEngine: AssistantEngine
    private lateinit var l3LocalEngine: AssistantEngine
    private lateinit var geminiEngine: AssistantEngine
    
    private lateinit var decisionMap: IntentDecisionMap

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        settingsManager = mockk(relaxed = true)
        fastMapDao = mockk()
        
        // Use the REAL regex engine
        realL1Engine = FastMapEngine(fastMapDao)
        
        // Keep external AI as mocks
        l2CloudEngine = mockk()
        l3LocalEngine = mockk()
        geminiEngine = mockk()

        decisionMap = IntentDecisionMap(
            realL1Engine,
            l2CloudEngine,
            l3LocalEngine,
            geminiEngine,
            settingsManager
        )
    }

    @Test
    fun `full chain flow through REAL Regex engine`() = runTest {
        // 1. Arrange: Rule that uses a capture group
        val rule = TestDataFactory.createFastMapRule(
            category = "maps",
            actionType = "NAVIGATE",
            triggerPattern = "du-ma la (.*)"
        )
        // Ensure the flow emits the list
        every { fastMapDao.getAllRules() } returns flowOf(listOf(rule))

        // 2. Act: This simulates a real voice command result
        val spokenText = "du-ma la bucuresti"
        val finalPayload = decisionMap.processCommand(spokenText)

        // 3. Assert: Verify the components worked together
        assertNotNull("Payload should not be null for a match", finalPayload)
        assertEquals("maps", finalPayload?.category)
        assertEquals("bucuresti", finalPayload?.destination)
    }

    @Test
    fun `full chain fallback when REAL Regex misses`() = runTest {
        // 1. Arrange: L1 Regex returns empty list (no matches possible)
        every { fastMapDao.getAllRules() } returns flowOf(emptyList())
        
        val spokenText = "cine a castigat meciul?"
        val expectedCloudResult = TestDataFactory.createIntentPayload(category = "knowledge", actionType = "query")
        
        every { settingsManager.getAiProcessor() } returns Strings.AiProcessors.OPENAI
        every { settingsManager.isCloudIntelligenceEnabled() } returns true
        coEvery { l2CloudEngine.processCommand(spokenText) } returns expectedCloudResult

        // 2. Act
        val finalPayload = decisionMap.processCommand(spokenText)

        // 3. Assert
        assertNotNull(finalPayload)
        assertEquals("knowledge", finalPayload?.category)
        
        // Verify the flow: L1 was called (but missed), L2 handled it
        coVerify(exactly = 1) { l2CloudEngine.processCommand(spokenText) }
    }
}
