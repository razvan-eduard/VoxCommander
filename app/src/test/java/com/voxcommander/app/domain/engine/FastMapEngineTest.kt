package com.voxcommander.app.domain.engine

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FastMapEngineTest {

    private lateinit var fastMapDao: FastMapDao
    private lateinit var engine: FastMapEngine

    @Before
    fun setup() {
        fastMapDao = mockk()
        engine = FastMapEngine(fastMapDao)
    }

    @Test
    fun `processCommand returns IntentPayload when pattern matches`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "SMART_HOME",
            actionType = "TOGGLE",
            triggerPattern = "turn on the (.*)"
        )
        every { fastMapDao.getAllRules() } returns flowOf(listOf(rule))

        // Act
        val result = engine.processCommand("turn on the kitchen lights")

        // Assert
        assertNotNull(result)
        assertEquals("SMART_HOME", result?.category)
        assertEquals("TOGGLE", result?.actionType)
    }

    @Test
    fun `processCommand is case insensitive`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "SMART_HOME",
            actionType = "TOGGLE",
            triggerPattern = "TURN ON THE (.*)"
        )
        every { fastMapDao.getAllRules() } returns flowOf(listOf(rule))

        // Act
        val result = engine.processCommand("turn on the kitchen lights")

        // Assert
        assertNotNull(result)
    }

    @Test
    fun `processCommand returns null when no pattern matches`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "SMART_HOME",
            actionType = "TOGGLE",
            triggerPattern = "turn on the (.*)"
        )
        every { fastMapDao.getAllRules() } returns flowOf(listOf(rule))

        // Act
        val result = engine.processCommand("play some music")

        // Assert
        assertNull(result)
    }

    @Test
    fun `processCommand extracts track from capture group correctly`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "MUSIC",
            actionType = "PLAY",
            triggerPattern = "pune melodia (.*)"
        )
        every { fastMapDao.getAllRules() } returns flowOf(listOf(rule))

        // Act
        val result = engine.processCommand("pune melodia perfect")

        // Assert
        assertNotNull(result)
        assertEquals("perfect", result?.track)
    }
}
