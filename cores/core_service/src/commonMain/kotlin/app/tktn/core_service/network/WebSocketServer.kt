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
    private val sessions = mutableSetOf<DefaultWebSocketServerSession>()
    private val sessionsMutex = Mutex()
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var currentPort: Int = 8080
    private var currentName: String = "Server"
    var serverId: String? = null
        private set

    fun start(id: String, name: String, port: Int = 8080) {
        if (server != null) return
        this.currentName = name
        this.currentPort = port
        this.serverId = id
        
        server = embeddedServer(CIO, port = port) {
            install(WebSockets)
            routing {
                get("/discovery") {
                    call.respondText(
                        """{"id": "$serverId", "name": "$currentName", "port": $currentPort}""",
                        contentType = io.ktor.http.ContentType.Application.Json
                    )
                }
                webSocket("/") {
                    sessionsMutex.withLock { sessions.add(this) }
                    try {
                        Logger.d("WebSocketServer") { "New client connected" }
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                _receivedMessages.emit(text)
                                // Broadcast to other clients
                                broadcast("Server received: $text")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("WebSocketServer", e) { "Error in websocket session" }
                    } finally {
                        sessionsMutex.withLock { sessions.remove(this) }
                        Logger.d("WebSocketServer") { "Client disconnected" }
                    }
                }
            }
        }.start(wait = false)
        _isRunning.value = true
        Logger.d("WebSocketServer") { "Server started on port $port" }
    }

    suspend fun broadcast(message: String) {
        val frame = Frame.Text(message)
        val currentSessions = sessionsMutex.withLock { sessions.toList() }
        currentSessions.forEach { session ->
            try {
                session.send(frame)
            } catch (e: Exception) {
                sessionsMutex.withLock { sessions.remove(session) }
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
        _isRunning.value = false
        Logger.d("WebSocketServer") { "Server stopped" }
    }
}
