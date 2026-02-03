package app.tktn.core_service.audio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.sound.sampled.*

actual class AudioRecorder {
    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording
    
    actual fun startRecording(): Flow<ByteArray> {
        // JVM/Windows is the client receiving audio, not recording
        Logger.d("AudioRecorder") { "AudioRecorder.startRecording() called on JVM - no-op (JVM receives audio)" }
        return emptyFlow()
    }
    
    actual fun stopRecording() {
        _isRecording.value = false
    }
}

actual class AudioPlayer {
    private var sourceLine: SourceDataLine? = null
    
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying
    
    actual var targetDeviceName: String? = null
    
    private val audioFormat = AudioFormat(
        AudioConfig.SAMPLE_RATE.toFloat(),
        AudioConfig.BITS_PER_SAMPLE,
        2, // Use 2 channels (Stereo) as many virtual drivers (like AudioRelay/VB-Cable) require it
        true,  // signed
        false  // little endian
    )
    
    actual fun getAvailableDevices(): List<String> {
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        return AudioSystem.getMixerInfo().filter { mixerInfo ->
            try {
                AudioSystem.getMixer(mixerInfo).isLineSupported(info)
            } catch (e: Exception) {
                false
            }
        }.map { "${it.name}|${it.description}" }
    }
    
    actual fun start() {
        try {
            Logger.d("AudioPlayer") { "Initializing Audio Player (48kHz Stereo)..." }
            val mixers = AudioSystem.getMixerInfo()
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            
            // Priority search for Virtual Mic modes
            val targetMixerInfo = if (targetDeviceName != null) {
                val cleanName = targetDeviceName?.substringBefore("|") ?: ""
                mixers.find { it.name == cleanName }
            } else {
                // Auto-detect common virtual drivers
                mixers.find { mixer ->
                    val name = mixer.name.lowercase()
                    val desc = mixer.description.lowercase()
                    name.contains("audiorelay") || desc.contains("audiorelay") ||
                    name.contains("virtual") || name.contains("cable") ||
                    desc.contains("virtual") || desc.contains("cable")
                }
            }
            
            sourceLine = if (targetMixerInfo != null) {
                Logger.d("AudioPlayer") { "Virtual Mic Bridge Found: ${targetMixerInfo.name}" }
                AudioSystem.getMixer(targetMixerInfo).getLine(info) as SourceDataLine
            } else {
                Logger.d("AudioPlayer") { "No virtual driver found. Using primary speaker as monitoring." }
                AudioSystem.getLine(info) as SourceDataLine
            }
            
            sourceLine?.apply {
                open(audioFormat, AudioConfig.BUFFER_SIZE_BYTES * 6)
                start()
            }
            
            _isPlaying.value = true
        } catch (e: Exception) {
            Logger.e("AudioPlayer", e) { "Failed to start audio sink" }
        }
    }
    
    private var receivedChunkCount = 0
    actual fun playAudio(audioData: ByteArray) {
        try {
            sourceLine?.let { line ->
                if (line.isOpen) {
                    // Convert mono incoming to stereo outgoing
                    val stereoData = ByteArray(audioData.size * 2)
                    for (i in 0 until audioData.size step 2) {
                        if (i + 1 < audioData.size) {
                            // Left Channel
                            stereoData[i * 2] = audioData[i]
                            stereoData[i * 2 + 1] = audioData[i + 1]
                            // Right Channel
                            stereoData[i * 2 + 2] = audioData[i]
                            stereoData[i * 2 + 3] = audioData[i + 1]
                        }
                    }
                    
                    receivedChunkCount++
                    if (receivedChunkCount % 100 == 0) {
                        Logger.d("AudioPlayer") { "Streaming to Virtual Mic... ($receivedChunkCount packets processed)" }
                    }
                    line.write(stereoData, 0, stereoData.size)
                }
            }
        } catch (e: Exception) {
            Logger.e("AudioPlayer", e) { "Error playing chunk: ${e.message}" }
        }
    }
    
    actual fun stop() {
        _isPlaying.value = false
        try {
            sourceLine?.apply {
                stop()
                flush()
                close()
            }
        } catch (e: Exception) {
            Logger.e("AudioPlayer") { "Error closing: ${e.message}" }
        }
        sourceLine = null
    }
}
