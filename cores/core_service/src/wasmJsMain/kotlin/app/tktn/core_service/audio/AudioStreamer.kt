package app.tktn.core_service.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

actual class AudioRecorder {
    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording
    
    actual fun startRecording(): Flow<ByteArray> {
        // Web/WASM implementation would use Web Audio API
        // Not implemented yet
        return emptyFlow()
    }
    
    actual fun stopRecording() {
        _isRecording.value = false
    }
}

actual class AudioPlayer {
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying
    
    actual var targetDeviceName: String? = null
    
    actual fun getAvailableDevices(): List<String> = emptyList()
    
    actual fun start() {
        // Web/WASM implementation would use Web Audio API
        _isPlaying.value = true
    }
    
    actual fun playAudio(audioData: ByteArray) {
        // Not implemented yet
    }
    
    actual fun stop() {
        _isPlaying.value = false
    }
}
