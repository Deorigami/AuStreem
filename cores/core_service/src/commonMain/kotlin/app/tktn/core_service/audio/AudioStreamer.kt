package app.tktn.core_service.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific audio capture interface.
 * Implemented by Android to capture microphone audio.
 */
expect class AudioRecorder() {
    val isRecording: StateFlow<Boolean>
    
    /**
     * Start recording audio from the microphone.
     * @return Flow of raw PCM audio bytes
     */
    fun startRecording(): Flow<ByteArray>
    
    /**
     * Stop recording audio.
     */
    fun stopRecording()
}

/**
 * Platform-specific audio playback interface.
 * Implemented by JVM to play received audio (acts as virtual mic input).
 */
expect class AudioPlayer() {
    val isPlaying: StateFlow<Boolean>
    
    /**
     * The name of the target audio device to use.
     * If null, the default system device will be used.
     */
    var targetDeviceName: String?
    
    /**
     * Start the audio player.
     */
    fun start()
    
    /**
     * Play audio bytes received from the stream.
     */
    fun playAudio(audioData: ByteArray)
    
    /**
     * Stop the audio player.
     */
    fun stop()
    
    /**
     * Get names of available output devices.
     */
    fun getAvailableDevices(): List<String>
    
    /**
     * Toggle monitoring (hearing yourself locally).
     */
    fun setMonitoring(enabled: Boolean)
}
