package app.tktn.core_service.network

expect fun getLocalIpAddress(): String?
expect fun getNetworkPrefixes(): List<String>
