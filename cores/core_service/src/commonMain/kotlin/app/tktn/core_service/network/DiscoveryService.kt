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
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds

@Single
class DiscoveryService(
    private val httpClient: HttpClient = HttpClient {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 1000
            connectTimeoutMillis = 500
        }
    }
) {
    // Reduced from 254 to 50 for better resource management on mobile
    private val scanSemaphore = kotlinx.coroutines.sync.Semaphore(50)
    private val broadcastAddresses = listOf("255.255.255.255", "10.0.2.2")
    
    val selfId = (1000..9999).random().toString()
    
    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.map { list ->
        list.filter { !it.isSelf }
    }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(), emptyList())

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var scanJob: Job? = null
    private var cleanupJob: Job? = null
    private var heartbeatJob: Job? = null  // Dedicated job to keep known servers alive
    
    // Track startup time for adaptive timing
    private var startTime = 0L
    
    private val json = Json { ignoreUnknownKeys = true }

    fun startBroadcasting(serverName: String) {
        if (broadcastJob != null) return
        
        val broadcastStartTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
        
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).udp().bind {
                broadcast = true
            }
            
            Logger.d("DiscoveryService") { "Broadcasting started for $serverName on $DISCOVERY_PORT (SelfID: $selfId)" }
            
            try {
                while (isActive) {
                    val localIp = getLocalIpAddress() ?: ""
                    val info = DiscoveredServer(
                        id = selfId,
                        name = serverName,
                        host = localIp,
                        port = DISCOVERY_PORT
                    )
                    val message = json.encodeToString(info)
                    val packetBytes = message.toByteArray()
                    
                    // 1. General Broadcast
                    socket.send(Datagram(ByteReadPacket(packetBytes), InetSocketAddress("255.255.255.255", UDP_PORT)))
                    
                    // 2. Specific Subnet Broadcasts (e.g., 192.168.1.255)
                    getNetworkPrefixes().forEach { prefix ->
                        try {
                            socket.send(Datagram(ByteReadPacket(packetBytes), InetSocketAddress("$prefix.255", UDP_PORT)))
                        } catch (e: Exception) {}
                    }
                    
                    // 3. Emulator Bridge
                    try {
                        socket.send(Datagram(ByteReadPacket(packetBytes), InetSocketAddress("10.0.2.2", UDP_PORT)))
                    } catch (e: Exception) {}
                    
                    // Adaptive timing: fast burst for first 3 seconds, then throttle down
                    val elapsed = kotlin.time.Clock.System.now().toEpochMilliseconds() - broadcastStartTime
                    val delayMs = when {
                        elapsed < 3000 -> 100L  // Fast burst: 10 broadcasts/sec for first 3s
                        elapsed < 10000 -> 300L // Medium: ~3 broadcasts/sec for next 7s
                        else -> 500L            // Steady state: 2 broadcasts/sec
                    }
                    delay(delayMs)
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
        
        startTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
        
        listenJob = CoroutineScope(Dispatchers.IO).launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", UDP_PORT)) {
                broadcast = true
            }
            
            Logger.d("DiscoveryService") { "Listening for servers on $UDP_PORT..." }
            
            try {
                while (isActive) {
                    val datagram = socket.receive()
                    val text = datagram.packet.readText()
                    val senderIp = (datagram.address as? InetSocketAddress)?.hostname
                    Logger.d("DiscoveryService") { "Received packet from $senderIp: $text" }
                    
                    try {
                        val decoded = json.decodeFromString<DiscoveredServer>(text)
                        val senderAddress = (datagram.address as? InetSocketAddress)
                        val resolvedHost = if (decoded.id == selfId) "localhost" else {
                            if (decoded.host.isEmpty() || decoded.host == "10.0.2.2") {
                                senderAddress?.hostname ?: "localhost"
                            } else {
                                decoded.host
                            }
                        }
                        
                        Logger.d("DiscoveryService") { "Successfully decoded server: ${decoded.name} at $resolvedHost" }
                        
                        val server = decoded.copy(
                            lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                            isSelf = decoded.id == selfId,
                            host = resolvedHost
                        )
                        updateServerList(server)
                    } catch (e: Exception) {
                        Logger.e("DiscoveryService", e) { "Error decoding discovery packet" }
                    }
                }
            } catch (e: Exception) {
                Logger.e("DiscoveryService", e) { "Listen error" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
        
        // Active Scan for Emulators and HTTP fallback
        startScan()
        
        // Heartbeat - continuously ping known servers every 1s to keep them alive
        startHeartbeat()

        // Cleanup - runs every 1s and removes servers not seen for 3s
        cleanupJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                _discoveredServers.update { list ->
                    val filtered = list.filter { now - it.lastSeen < 3000 } // 3s threshold - heartbeat refreshes every 1s
                    if (filtered.size != list.size) {
                        Logger.d("DiscoveryService") { "Cleaned up ${list.size - filtered.size} stale server(s)" }
                    }
                    filtered
                }
                delay(1000)
            }
        }
    }
    
    /**
     * Dedicated heartbeat that pings all known servers every 1 second.
     * This keeps servers alive and detects disconnections quickly.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500) // Initial delay to let discovery find servers first
            
            while (isActive) {
                val servers = _discoveredServers.value.filter { !it.isSelf }
                
                if (servers.isNotEmpty()) {
                    Logger.d("DiscoveryService") { "Heartbeat: pinging ${servers.size} server(s)" }
                    
                    // Ping all known servers in parallel
                    coroutineScope {
                        servers.forEach { server ->
                            launch {
                                pingServer(server)
                            }
                        }
                    }
                }
                
                delay(1000) // Heartbeat every 1 second
            }
        }
    }
    
    /**
     * Ping a server to check if it's still alive and update lastSeen.
     */
    private suspend fun pingServer(server: DiscoveredServer) {
        try {
            val response = withTimeout(800) {
                httpClient.get("http://${server.host}:$DISCOVERY_PORT/discovery")
            }
            if (response.status.value == 200) {
                val text = response.bodyAsText()
                val data = json.decodeFromString<DiscoveredServer>(text)
                // Update lastSeen to keep the server alive
                updateServerList(data.copy(
                    id = data.id,
                    host = server.host,
                    isSelf = false,
                    lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds()
                ))
            }
        } catch (e: Exception) {
            Logger.d("DiscoveryService") { "Heartbeat failed for ${server.name} at ${server.host}" }
            // Don't remove immediately - let the cleanup job handle stale servers
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            var scanCount = 0
            
            while (isActive) {
                val prefixes = getNetworkPrefixes().filter { it != "169.254" }
                if (prefixes.isEmpty()) {
                    Logger.w("DiscoveryService") { "No valid network prefixes found for scanning!" }
                    delay(5000)
                    continue
                }

                val knownHosts = _discoveredServers.value.map { it.host }.toSet()
                val hasKnownServers = knownHosts.isNotEmpty()
                
                // Adaptive scanning: full scan initially or when no servers, quick refresh otherwise
                val doFullScan = scanCount < 3 || !hasKnownServers
                
                if (doFullScan) {
                    Logger.d("DiscoveryService") { "Starting full network sweep #$scanCount on $prefixes" }
                } else {
                    Logger.d("DiscoveryService") { "Quick refresh of ${knownHosts.size} known host(s)" }
                }
                
                coroutineScope {
                    if (doFullScan) {
                        // Full subnet scan
                        prefixes.forEach { prefix ->
                            val myIp = getLocalIpAddress()
                            val mySuffix = myIp?.substringAfterLast('.')?.toIntOrNull() ?: 100
                            // Prioritize IPs near our own, common router ranges, and DHCP ranges
                            val prioritySuffixes = listOf(1, 254, 100, 101, 102) + 
                                (mySuffix - 5..mySuffix + 5).filter { it in 1..254 }
                            val otherSuffixes = (1..254).filter { it !in prioritySuffixes }
                            val suffixes = (prioritySuffixes + otherSuffixes).distinct()
                            
                            suffixes.forEach { i ->
                                val host = "$prefix.$i"
                                if (host == myIp) return@forEach
                                
                                launch {
                                    scanSemaphore.withPermit {
                                        scanHost(host)
                                    }
                                }
                            }
                        }
                    } else {
                        // Quick refresh: only scan known hosts
                        knownHosts.forEach { host ->
                            launch {
                                scanSemaphore.withPermit {
                                    scanHost(host)
                                }
                            }
                        }
                    }

                    // Always scan emulator bridge
                    launch {
                        try {
                            val response = withTimeout(500) { 
                                httpClient.get("http://10.0.2.2:$DISCOVERY_PORT/discovery") 
                            }
                            if (response.status.value == 200) {
                                val text = response.bodyAsText()
                                val data = json.decodeFromString<DiscoveredServer>(text)
                                val isSelf = data.id == selfId
                                updateServerList(data.copy(
                                    id = if (isSelf) "self-$selfId" else "emulator-$DISCOVERY_PORT",
                                    host = "10.0.2.2",
                                    isSelf = isSelf,
                                    lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                ))
                            }
                        } catch (e: Exception) {}
                    }
                }
                
                scanCount++
                
                // Adaptive delay: faster initially, slower in steady state
                val elapsed = kotlin.time.Clock.System.now().toEpochMilliseconds() - startTime
                val scanDelay = when {
                    elapsed < 5000 -> 500L   // First 5s: scan every 500ms
                    elapsed < 15000 -> 1000L // Next 10s: scan every 1s
                    hasKnownServers -> 1500L // Steady state with servers: every 1.5s (ensures 5+ refreshes before 8s expiry)
                    else -> 1500L            // Steady state searching: every 1.5s
                }
                
                Logger.d("DiscoveryService") { "Sweep #$scanCount complete, next in ${scanDelay}ms" }
                delay(scanDelay)
            }
        }
    }
    
    private suspend fun scanHost(host: String) {
        try {
            val response = withTimeout(400) { // 400ms timeout for reliability
                httpClient.get("http://$host:$DISCOVERY_PORT/discovery") 
            }
            if (response.status.value == 200) {
                val text = response.bodyAsText()
                val data = json.decodeFromString<DiscoveredServer>(text)
                Logger.d("DiscoveryService") { "SWEEP FOUND: ${data.name} at $host" }
                updateServerList(data.copy(
                    id = data.id,
                    host = host,
                    isSelf = false,
                    lastSeen = kotlin.time.Clock.System.now().toEpochMilliseconds()
                ))
            }
        } catch (e: Exception) {}
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
        cleanupJob?.cancel()
        cleanupJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
