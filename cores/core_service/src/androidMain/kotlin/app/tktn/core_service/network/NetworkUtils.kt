package app.tktn.core_service.network

import java.net.NetworkInterface
import java.net.InetAddress
import java.util.Collections

actual fun getLocalIpAddress(): String? {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress
                    val isIPv4 = sAddr.indexOf(':') < 0
                    if (isIPv4) return sAddr
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}

actual fun getNetworkPrefixes(): List<String> {
    val prefixes = mutableListOf<String>()
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress
                    if (sAddr.indexOf(':') < 0) { // IPv4
                        val lastDot = sAddr.lastIndexOf('.')
                        if (lastDot > 0) {
                            prefixes.add(sAddr.substring(0, lastDot))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {}
    return prefixes.distinct()
}
