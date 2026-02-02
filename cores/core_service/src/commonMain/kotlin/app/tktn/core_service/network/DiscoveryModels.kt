package app.tktn.core_service.network

import kotlinx.serialization.Serializable

const val DISCOVERY_PORT = 2525
const val UDP_PORT = 59101

@Serializable
data class DiscoveredServer(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val port: Int = 8080,
    val lastSeen: Long = 0L,
    val isSelf: Boolean = false
)
