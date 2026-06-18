package com.voxcommander.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global voice state manager to coordinate between wake word service and command recognition.
 * Acts as a semaphore to prevent microphone conflicts between Vosk and Whisper/Google.
 */
object VoiceStateManager {
    
    enum class VoiceState {
        IDLE,               // No one is listening
        LISTENING_WAKEWORD, // Vosk is active (wake word detection)
        LISTENING_COMMAND,  // Whisper/Google is active (command recognition)
        PROCESSING          // Whisper is transcribing text
    }
    
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    
    private val _wakeWordDetected = MutableStateFlow(false)
    val wakeWordDetected: StateFlow<Boolean> = _wakeWordDetected.asStateFlow()
    
    /**
     * Transition to wake word listening state
     */
    fun startWakeWordListening() {
        _state.value = VoiceState.LISTENING_WAKEWORD
    }
    
    /**
     * Transition to command listening state (after wake word detected)
     */
    fun startCommandListening() {
        _state.value = VoiceState.LISTENING_COMMAND
    }
    
    /**
     * Transition to processing state
     */
    fun startProcessing() {
        _state.value = VoiceState.PROCESSING
    }
    
    /**
     * Return to idle state
     */
    fun setIdle() {
        _state.value = VoiceState.IDLE
    }
    
    /**
     * Signal that wake word was detected
     */
    fun onWakeWordDetected() {
        _wakeWordDetected.value = true
    }
    
    /**
     * Reset wake word detection flag
     */
    fun resetWakeWordDetection() {
        _wakeWordDetected.value = false
    }
    
    /**
     * Check if microphone is available for wake word
     */
    fun canStartWakeWord(): Boolean {
        return _state.value == VoiceState.IDLE
    }
    
    /**
     * Check if microphone is available for command
     */
    fun canStartCommand(): Boolean {
        return _state.value == VoiceState.LISTENING_WAKEWORD
    }
}
