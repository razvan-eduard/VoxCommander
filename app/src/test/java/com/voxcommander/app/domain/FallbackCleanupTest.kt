package com.voxcommander.app.domain

import android.content.Context
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.ui.viewmodels.ModelManagementViewModel
import com.voxcommander.app.utils.Strings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FallbackCleanupTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var appStateManager: AppStateManager
    private lateinit var modelDownloader: ModelDownloader
    private lateinit var viewModel: ModelManagementViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        appStateManager = mockk(relaxed = true)
        modelDownloader = mockk(relaxed = true)

        viewModel = ModelManagementViewModel(
            settingsManager,
            appStateManager,
            modelDownloader,
            context
        )
    }

    @Test
    fun `deleteUnusedModels should protect active models and wake word model`() = runTest {
        // Arrange
        every { settingsManager.getVoiceProcessor() } returns Strings.Processors.VOSK
        every { settingsManager.getSelectedVoskModelName() } returns "vosk-ro"
        every { settingsManager.getSelectedWhisperModelId() } returns "base"
        every { settingsManager.getSelectedLlamaModelId() } returns "3.2-1b"
        every { settingsManager.getWakeWordModelPath() } returns "vosk-en-small"
        
        every { context.getExternalFilesDir(null) } returns null 

        // Act
        viewModel.deleteUnusedModels()

        // Assert
        verify {
            modelDownloader.deleteUnusedModels(
                protectedVoskModels = setOf("vosk-ro", "vosk-en-small"),
                protectedWhisperModels = setOf("base"),
                protectedLlamaModels = setOf("3.2-1b")
            )
        }
    }

    @Test
    fun `when clearDefaultFallback is requested via ViewModel, settings manager receives the command`() = runTest {
        // Act: This simulates the button press in AdvancedSettingsTab
        viewModel.clearDefaultOfflineFallback()

        // Assert: Verify the ViewModel delegated the call to the mock manager
        verify(exactly = 1) { settingsManager.clearDefaultOfflineFallback() }
        
        // Also verify it triggered a UI refresh
        verify { appStateManager.refreshAll() }
    }
}
