package io.github.reichimd.zapio.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's own IPv4 address in the home network and the size of that network. */
data class LocalNetwork(val ownIp: String, val prefixLength: Int) {

    companion object {
        /** Finds the Wi-Fi (or Ethernet) network, even when a VPN is the active one; null if there is none. */
        fun find(context: Context): LocalNetwork? {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            @Suppress("DEPRECATION") // allNetworks is the simple way to look past a VPN.
            val networks = listOfNotNull(manager.activeNetwork) + manager.allNetworks
            return networks.distinct().firstNotNullOfOrNull { network -> fromNetwork(manager, network) }
        }

        private fun fromNetwork(manager: ConnectivityManager, network: Network): LocalNetwork? {
            val caps = manager.getNetworkCapabilities(network) ?: return null
            val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
            val link = manager.getLinkProperties(network) ?: return null
            val ipv4 = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            val ip = ipv4.address.hostAddress ?: return null
            return LocalNetwork(ip, ipv4.prefixLength)
        }
    }
}
