package app.tktn.core_service.network

import app.tktn.core_service.base.IO
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import co.touchlab.kermit.Logger
import kotlin.time.Duration.Companion.seconds

@Single
class DiscoveryService(
    private val httpClient: HttpClient = HttpClient()
) {
    private val discoveryPort = 59101
    private val broadcastAddress = "255.255.255.255"
    
    val selfId = (1000..9999).random().toString()
    
    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.map { list ->
        list.filter { !it.isSelf }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.WhileSubscribed(), emptyList())

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var scanJob: Job? = null
    
    private val json = Json { ignoreUnknownKeys = true }

    fun startBroadcasting(serverName: String, serverPort: Int) {
        if (broadcastJob != null) return
        
        broadcastJob = CoroutineScope(Dispatchers.Default).launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).udp().bind {
                broadcast = true
            }
            
            Logger.d("DiscoveryService") { "Broadcasting started for $serverName on $serverPort" }
            
            try {
                while (isActive) {
                    val info = DiscoveredServer(
                        id = selfId,
                        name = serverName,
                        host = "10.0.2.2", 
                        port = serverPort
                    )
                    val message = json.encodeToString(info)
                    val packetBytes = message.toByteArray()
                    
                    socket.send(Datagram(ByteReadPacket(packetBytes), InetSocketAddress(broadcastAddress, discoveryPort)))
                    
                    try {
                        socket.send(Datagram(ByteReadPacket(packetBytes), InetSocketAddress("10.0.2.2", discoveryPort)))
                    } catch (e: Exception) {}
                    
                    delay(2000)
                }
            } catch (e: Exception) {
                Logger.e("DiscoveryService", e) { "Broadcast error" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun startListening() {
        if (listenJob != null) return
        
        listenJob = CoroutineScope(Dispatchers.Default).launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", discoveryPort)) {
                broadcast = true
            }
            
            Logger.d("DiscoveryService") { "Listening for servers on $discoveryPort..." }
            
            try {
                while (isActive) {
                    val datagram = socket.receive()
                    val text = datagram.packet.readText()
                    try {
                        val decoded = json.decodeFromString<DiscoveredServer>(text)
                        val server = decoded.copy(
                            lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                            isSelf = decoded.id == selfId,
                            host = if (decoded.id == selfId) "localhost" else (datagram.address as? InetSocketAddress)?.hostname ?: "localhost"
                        )
                        updateServerList(server)
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Logger.e("DiscoveryService", e) { "Listen error" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
        
        // Active Scan for Emulators
        startScan()

        // Cleanup
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                _discoveredServers.update { list ->
                    list.filter { now - it.lastSeen < 15000 }
                }
                delay(5000)
            }
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                Logger.d("DiscoveryService") { "Scanning for servers on host machine (10.0.2.2)..." }
                // Scan common ports on host machine (10.0.2.2)
                for (port in listOf(2525)) {
                    launch {
                        try {
                            val response = httpClient.get("http://10.0.2.2:$port/discovery")
                            if (response.status.value == 200) {
                                val text = response.bodyAsText()
                                val data = json.decodeFromString<DiscoveredServer>(text)
                                val isSelf = data.id == selfId
                                Logger.d("DiscoveryService") { "Discovered server: ${data.name} on port $port (id: ${data.id}, isSelf: $isSelf)" }
                                updateServerList(data.copy(
                                    id = if (isSelf) "self-$selfId" else "emulator-$port",
                                    host = "10.0.2.2",
                                    isSelf = isSelf,
                                    lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                ))
                            } else {
                                removeServer("emulator-$port")
                            }
                        } catch (e: Exception) {
                            removeServer("emulator-$port")
                        }
                    }
                }
                delay(0.3.seconds)
            }
        }
    }

    private fun updateServerList(server: DiscoveredServer) {
        _discoveredServers.update { list ->
            val existingIndex = list.indexOfFirst { it.id == server.id }
            if (existingIndex >= 0) {
                list.toMutableList().apply { set(existingIndex, server) }
            } else {
                list + server
            }
        }
    }

    private fun removeServer(id: String) {
        _discoveredServers.update { list ->
            list.filter { it.id != id }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        scanJob?.cancel()
        scanJob = null
    }
}
