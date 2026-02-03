package app.tktn.core_service.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single
import co.touchlab.kermit.Logger

@Single
class WebSocketClient(
    private val client: HttpClient = HttpClient {
        install(WebSockets)
    }
) {
    private var textSession: DefaultClientWebSocketSession? = null
    private var audioSession: DefaultClientWebSocketSession? = null
    
    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages
    
    // Audio data received from server
    private val _audioData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val audioData: SharedFlow<ByteArray> = _audioData

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _isAudioConnected = MutableStateFlow(false)
    val isAudioConnected: StateFlow<Boolean> = _isAudioConnected

    private val _connectedHost = MutableStateFlow<String?>(null)
    val connectedHost: StateFlow<String?> = _connectedHost

    /**
     * Connect to the text/control WebSocket endpoint.
     */
    suspend fun connect(host: String, port: Int, path: String = "/") {
        try {
            _connectedHost.value = "$host:$port"
            client.webSocket(method = HttpMethod.Get, host = host, port = port, path = path) {
                textSession = this
                _isConnected.value = true
                Logger.d("WebSocketClient") { "Connected to $host:$port$path" }
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        _messages.emit(text)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("WebSocketClient", e) { "Connection failed" }
        } finally {
            _isConnected.value = false
            _connectedHost.value = null
            textSession = null
            Logger.d("WebSocketClient") { "Disconnected from text channel" }
        }
    }
    
    /**
     * Connect to the audio WebSocket endpoint to receive audio stream.
     * This should be called after discovering a server that is streaming audio.
     */
    suspend fun connectAudio(host: String, port: Int) {
        try {
            Logger.d("WebSocketClient") { "Connecting to audio stream at $host:$port/audio" }
            client.webSocket(method = HttpMethod.Get, host = host, port = port, path = "/audio") {
                audioSession = this
                _isAudioConnected.value = true
                Logger.d("WebSocketClient") { "Connected to audio stream" }
                
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            val audioBytes = frame.readBytes()
                            _audioData.emit(audioBytes)
                        }
                        is Frame.Close -> {
                            Logger.d("WebSocketClient") { "Audio stream closed by server" }
                            break
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("WebSocketClient", e) { "Audio connection failed" }
        } finally {
            _isAudioConnected.value = false
            audioSession = null
            Logger.d("WebSocketClient") { "Disconnected from audio stream" }
        }
    }

    suspend fun sendMessage(message: String) {
        textSession?.send(Frame.Text(message))
    }
    
    /**
     * Send audio data to the server (if bidirectional audio is needed).
     */
    suspend fun sendAudio(audioData: ByteArray) {
        audioSession?.send(Frame.Binary(true, audioData))
    }

    suspend fun disconnect() {
        textSession?.close()
        textSession = null
        _isConnected.value = false
    }
    
    suspend fun disconnectAudio() {
        audioSession?.close()
        audioSession = null
        _isAudioConnected.value = false
    }
    
    suspend fun disconnectAll() {
        disconnect()
        disconnectAudio()
        _connectedHost.value = null
    }
}

