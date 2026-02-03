package app.tktn.core_service.network

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import co.touchlab.kermit.Logger
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.http.ContentType

@Single
class WebSocketServer {
    private var server: EmbeddedServer<*, *>? = null
    
    // Text message sessions (chat/control)
    private val textSessions = mutableSetOf<DefaultWebSocketServerSession>()
    private val textSessionsMutex = Mutex()
    
    // Audio streaming sessions
    private val audioSessions = mutableSetOf<DefaultWebSocketServerSession>()
    private val audioSessionsMutex = Mutex()
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages
    
    // Audio data received from clients (for potential server-side processing)
    private val _receivedAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val receivedAudio: SharedFlow<ByteArray> = _receivedAudio

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients

    private var currentPort: Int = DISCOVERY_PORT
    private var currentName: String = "Server"
    var serverId: String? = null
        private set

    fun start(id: String, name: String) {
        if (server != null) return
        this.currentName = name
        this.currentPort = DISCOVERY_PORT
        this.serverId = id
        
        server = embeddedServer(CIO, port = DISCOVERY_PORT, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                // Discovery endpoint
                get("/discovery") {
                    call.respondText(
                        """{"id": "$serverId", "name": "$currentName", "port": $currentPort}""",
                        contentType = io.ktor.http.ContentType.Application.Json
                    )
                }
                
                // Text/control WebSocket
                webSocket("/") {
                    textSessionsMutex.withLock { textSessions.add(this) }
                    updateClientCount()
                    try {
                        Logger.d("WebSocketServer") { "New text client connected" }
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                _receivedMessages.emit(text)
                                broadcast("Server received: $text")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("WebSocketServer", e) { "Error in text websocket session" }
                    } finally {
                        textSessionsMutex.withLock { textSessions.remove(this) }
                        updateClientCount()
                        Logger.d("WebSocketServer") { "Text client disconnected" }
                    }
                }
                
                // Audio streaming WebSocket - binary frames
                webSocket("/audio") {
                    audioSessionsMutex.withLock { audioSessions.add(this) }
                    updateClientCount()
                    try {
                        Logger.d("WebSocketServer") { "New audio client connected" }
                        for (frame in incoming) {
                            // Server can receive audio from clients if needed
                            if (frame is Frame.Binary) {
                                val audioData = frame.readBytes()
                                _receivedAudio.emit(audioData)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("WebSocketServer", e) { "Error in audio websocket session" }
                    } finally {
                        audioSessionsMutex.withLock { audioSessions.remove(this) }
                        updateClientCount()
                        Logger.d("WebSocketServer") { "Audio client disconnected" }
                    }
                }
            }
        }.start(wait = false)
        _isRunning.value = true
        Logger.d("WebSocketServer") { "Server started on port $DISCOVERY_PORT" }
    }
    
    private fun updateClientCount() {
        _connectedClients.value = textSessions.size + audioSessions.size
    }

    suspend fun broadcast(message: String) {
        val frame = Frame.Text(message)
        val currentSessions = textSessionsMutex.withLock { textSessions.toList() }
        currentSessions.forEach { session ->
            try {
                session.send(frame)
            } catch (e: Exception) {
                textSessionsMutex.withLock { textSessions.remove(session) }
            }
        }
    }
    
    /**
     * Broadcast audio data to all connected audio clients.
     * Called by the audio recorder to stream mic data.
     */
    suspend fun broadcastAudio(audioData: ByteArray) {
        val frame = Frame.Binary(true, audioData)
        val currentSessions = audioSessionsMutex.withLock { audioSessions.toList() }
        currentSessions.forEach { session ->
            try {
                session.send(frame)
            } catch (e: Exception) {
                audioSessionsMutex.withLock { audioSessions.remove(session) }
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        textSessions.clear()
        audioSessions.clear()
        _isRunning.value = false
        _connectedClients.value = 0
        Logger.d("WebSocketServer") { "Server stopped" }
    }
}

