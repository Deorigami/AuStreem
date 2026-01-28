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
    private var session: DefaultClientWebSocketSession? = null
    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedHost = MutableStateFlow<String?>(null)
    val connectedHost: StateFlow<String?> = _connectedHost

    suspend fun connect(host: String, port: Int, path: String = "/") {
        try {
            _connectedHost.value = "$host:$port"
            client.webSocket(method = HttpMethod.Get, host = host, port = port, path = path) {
                session = this
                _isConnected.value = true
                Logger.d("WebSocket") { "Connected to $host:$port$path" }
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        _messages.emit(text)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("WebSocket", e) { "Connection failed" }
            // Don't rethrow to prevent app crash on abrupt disconnection (EOF)
        } finally {
            _isConnected.value = false
            _connectedHost.value = null
            session = null
            Logger.d("WebSocket") { "Disconnected" }
        }
    }

    suspend fun sendMessage(message: String) {
        session?.send(Frame.Text(message))
    }

    suspend fun disconnect() {
        session?.close()
        session = null
    }
}
