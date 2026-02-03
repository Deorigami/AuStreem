package app.tktn.core_service.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

actual class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private val recordingStateMutex = kotlinx.coroutines.sync.Mutex()
    
    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording
    
    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ),
        AudioConfig.BUFFER_SIZE_BYTES
    )
    
    @SuppressLint("MissingPermission")
    actual fun startRecording(): Flow<ByteArray> = flow {
        recordingStateMutex.withLock {
            if (_isRecording.value) {
                Logger.d("AudioRecorder") { "Already recording, ignoring start request" }
                return@flow
            }

            Logger.d("AudioRecorder") { "Initializing AudioRecord: ${AudioConfig.SAMPLE_RATE}Hz, Mono, 16-bit" }
            
            // VOICE_COMMUNICATION is often more reliable on modern Android versions
            // and includes AEC/NS processing.
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e("AudioRecorder") { "Primary source failed, trying default MIC..." }
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConfig.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e("AudioRecorder") { "CRITICAL: AudioRecord failed to initialize after 2 attempts!" }
                audioRecord?.release()
                audioRecord = null
                return@flow
            }
            
            try {
                audioRecord?.startRecording()
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Logger.e("AudioRecorder") { "AudioRecord is initialized but failed to start recording!" }
                    return@flow
                }
                _isRecording.value = true
                Logger.d("AudioRecorder") { "Recording started - Watch for the green mic icon in system status bar!" }
            } catch (e: Exception) {
                Logger.e("AudioRecorder", e) { "Failed to start recording" }
                audioRecord?.release()
                audioRecord = null
                return@flow
            }
        }
        
        val buffer = ByteArray(bufferSize)
        
        try {
            var chunkCount = 0
            while (coroutineContext.isActive && _isRecording.value) {
                val currentRecord = audioRecord
                if (currentRecord == null || currentRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Logger.e("AudioRecorder") { "AudioRecord became unavailable or uninitialized" }
                    break
                }
                
                val bytesRead = currentRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    chunkCount++
                    
                    // Simple Peak Detection to verify sound is actually captured
                    if (chunkCount % 50 == 0) {
                        var maxPeak = 0
                        for (i in 0 until bytesRead step 2) {
                            val sample = ((buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
                            val absSample = Math.abs(sample)
                            if (absSample > maxPeak) maxPeak = absSample
                        }
                        Logger.d("AudioRecorder") { "Capturing... Vol level: $maxPeak/32768" }
                    }
                    
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead < 0) {
                    Logger.e("AudioRecorder") { "Read error: $bytesRead" }
                    break
                }
            }
        } catch (e: Exception) {
            Logger.e("AudioRecorder", e) { "Exception in capture loop" }
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)
    
    actual fun stopRecording() {
        // Run on IO since stop/release can be blocking
        CoroutineScope(Dispatchers.IO).launch {
            recordingStateMutex.withLock {
                if (!_isRecording.value && audioRecord == null) return@withLock
                
                Logger.d("AudioRecorder") { "Stopping and releasing AudioRecord" }
                _isRecording.value = false
                try {
                    audioRecord?.let { record ->
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                record.stop()
                            }
                        }
                        record.release()
                    }
                } catch (e: Exception) {
                    Logger.e("AudioRecorder", e) { "Error during AudioRecord stop/release" }
                } finally {
                    audioRecord = null
                }
            }
        }
    }
}

actual class AudioPlayer {
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying
    
    actual var targetDeviceName: String? = null
    
    actual fun getAvailableDevices(): List<String> = emptyList()

    actual fun setMonitoring(enabled: Boolean) {
        // No-op on Android
    }
    
    actual fun start() {
        // Android client doesn't need audio playback - it's the mic source
        Logger.d("AudioPlayer") { "AudioPlayer.start() called on Android - no-op (Android is the mic source)" }
        _isPlaying.value = true
    }
    
    actual fun playAudio(audioData: ByteArray) {
        // No-op on Android - this device is the audio source, not sink
    }
    
    actual fun stop() {
        _isPlaying.value = false
    }
}
