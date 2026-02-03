package app.tktn.core_service.audio

import app.tktn.core_service.base.IO
import app.tktn.core_service.network.WebSocketClient
import app.tktn.core_service.network.WebSocketServer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

/**
 * Service that manages audio streaming between devices.
 * 
 * On Android (Server/Mic mode):
 * - Captures audio from the microphone
 * - Streams it via WebSocket to connected clients
 * 
 * On JVM/Windows (Client/Speaker mode):
 * - Connects to the Android server's audio stream
 * - Plays received audio through the default audio output
 * 
 * For virtual microphone functionality on Windows, you'll need a virtual audio cable
 * like VB-CABLE. Route audio output to the virtual cable input, then select the
 * virtual cable as your microphone in other apps.
 */
@Single
class AudioStreamingService(
    private val webSocketServer: WebSocketServer,
    private val webSocketClient: WebSocketClient
) {
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    
    private var streamingJob: Job? = null
    private var receivingJob: Job? = null
    
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming
    
    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Get list of available output devices (mixers).
     */
    fun getAvailableDevices(): List<String> = audioPlayer.getAvailableDevices()
    
    /**
     * Set the target audio device by name.
     */
    fun setTargetDevice(name: String?) {
        audioPlayer.targetDeviceName = name
    }
    
    fun setMonitoring(enabled: Boolean) {
        audioPlayer.setMonitoring(enabled)
    }
    
    /**
     * Start streaming audio from the microphone to all connected WebSocket clients.
     * Call this on Android when acting as the audio source.
     */
    fun startStreaming() {
        if (streamingJob != null) {
            Logger.d("AudioStreamingService") { "Already streaming" }
            return
        }
        
        Logger.d("AudioStreamingService") { "Starting audio streaming..." }
        _isStreaming.value = true
        
        streamingJob = scope.launch {
            audioRecorder.startRecording().collect { audioData ->
                // Broadcast audio data to all connected clients
                webSocketServer.broadcastAudio(audioData)
            }
        }
    }
    
    /**
     * Stop streaming audio.
     */
    fun stopStreaming() {
        Logger.d("AudioStreamingService") { "Stopping audio streaming..." }
        streamingJob?.cancel()
        streamingJob = null
        audioRecorder.stopRecording()
        _isStreaming.value = false
    }
    
    /**
     * Connect to a server and start receiving audio.
     * Call this on JVM/Windows when acting as the audio receiver.
     * 
     * @param host The server's IP address
     * @param port The server's port
     */
    fun startReceiving(host: String, port: Int) {
        if (receivingJob != null) {
            Logger.d("AudioStreamingService") { "Already receiving" }
            return
        }
        
        Logger.d("AudioStreamingService") { "Starting audio receiving from $host:$port..." }
        _isReceiving.value = true
        
        // Start the audio player
        audioPlayer.start()
        
        receivingJob = scope.launch {
            // Connect to the audio WebSocket in a separate coroutine
            val connectionJob = launch {
                webSocketClient.connectAudio(host, port)
            }
            
            // Process incoming audio data
            webSocketClient.audioData.collect { audioData ->
                audioPlayer.playAudio(audioData)
            }
            
            connectionJob.join()
        }
    }
    
    /**
     * Stop receiving audio.
     */
    fun stopReceiving() {
        Logger.d("AudioStreamingService") { "Stopping audio receiving..." }
        receivingJob?.cancel()
        receivingJob = null
        audioPlayer.stop()
        scope.launch {
            webSocketClient.disconnectAudio()
        }
        _isReceiving.value = false
    }
    
    /**
     * Clean up all resources.
     */
    fun dispose() {
        stopStreaming()
        stopReceiving()
        scope.cancel()
    }
}
