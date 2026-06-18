package com.voxcommander.app.domain.engine

import com.voxcommander.app.data.local.dao.FastMapDao
import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.domain.intent.interpreter.FastMapEngine
import io.mockk.coEvery
import io.mockk.mockk
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
            target = "LIGHTS",
            triggerPattern = "turn on the (.*)"
        )
        coEvery { fastMapDao.getAllRules() } returns listOf(rule)

        // Act
        val result = engine.processCommand("turn on the kitchen lights")

        // Assert
        assertNotNull(result)
        assertEquals("SMART_HOME", result?.category)
        assertEquals("TOGGLE", result?.actionType)
        assertEquals("LIGHTS", result?.target)
        assertEquals("kitchen lights", result?.query)
    }

    @Test
    fun `processCommand is case insensitive`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "SMART_HOME",
            actionType = "TOGGLE",
            target = "LIGHTS",
            triggerPattern = "TURN ON THE (.*)"
        )
        coEvery { fastMapDao.getAllRules() } returns listOf(rule)

        // Act
        val result = engine.processCommand("turn on the kitchen lights")

        // Assert
        assertNotNull(result)
        assertEquals("kitchen lights", result?.query)
    }

    @Test
    fun `processCommand returns null when no pattern matches`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "SMART_HOME",
            actionType = "TOGGLE",
            target = "LIGHTS",
            triggerPattern = "turn on the (.*)"
        )
        coEvery { fastMapDao.getAllRules() } returns listOf(rule)

        // Act
        val result = engine.processCommand("play some music")

        // Assert
        assertNull(result)
    }

    @Test
    fun `processCommand returns null when query field is null if no capture group present`() = runTest {
        // Arrange
        val rule = FastMapRule(
            id = 1,
            category = "GREETING",
            actionType = "HELLO",
            target = "SYSTEM",
            triggerPattern = "hello assistant"
        )
        coEvery { fastMapDao.getAllRules() } returns listOf(rule)

        // Act
        val result = engine.processCommand("hello assistant")

        // Assert
        assertNotNull(result)
        assertEquals("GREETING", result?.category)
        assertEquals("HELLO", result?.actionType)
        assertNull(result?.query)
    }
}
