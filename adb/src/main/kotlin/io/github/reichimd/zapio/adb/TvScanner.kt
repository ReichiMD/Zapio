package io.github.reichimd.zapio.adb

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What a quick look at the ADB port of an address shows. */
enum class PortState {
    /** Something listens, most likely ADB. */
    OPEN,

    /** The device answers but refuses: it is on, ADB is off. */
    CLOSED,

    /** No answer: device off, asleep or not in this network. */
    NO_ANSWER,
}

/** Finds TVs with ADB over Wi-Fi by trying the ADB port on every address of the local network. */
object TvScanner {

    const val PROBE_TIMEOUT_MS = 600
    private const val PARALLEL_PROBES = 64

    /**
     * IPv4 addresses next to [ownIp] in a network with [prefixLength], without the phone itself.
     * Networks larger than a /24 are cut down to the /24 around the phone, so a scan stays short.
     */
    fun hostsAround(ownIp: String, prefixLength: Int): List<String> {
        val own = parseIpv4(ownIp) ?: return emptyList()
        if (prefixLength !in 0..30) return emptyList()
        val prefix = maxOf(prefixLength, 24)
        val mask = (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val network = own and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        return (network + 1 until broadcast)
            .filter { it != own }
            .map(::formatIpv4)
    }

    /** Opens and closes a plain TCP connection to see whether the port is open. */
    fun probe(address: TvAddress, timeoutMs: Int = PROBE_TIMEOUT_MS): PortState = try {
        Socket().use { it.connect(InetSocketAddress(address.host, address.port), timeoutMs) }
        PortState.OPEN
    } catch (e: ConnectException) {
        // Refused: the device is there, nothing listens. Some systems report unreachable hosts the same way.
        if (e.message?.contains("refused", ignoreCase = true) == true) PortState.CLOSED else PortState.NO_ANSWER
    } catch (e: IOException) {
        PortState.NO_ANSWER
    }

    /**
     * Tries [port] on all [hosts] in parallel and returns the addresses where it is open,
     * sorted like the input. [onFound] is called from worker threads as soon as one turns up.
     */
    fun scan(
        hosts: List<String>,
        port: Int = TvAddress.DEFAULT_PORT,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        onFound: (TvAddress) -> Unit = {},
    ): List<TvAddress> {
        if (hosts.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(PARALLEL_PROBES, hosts.size))
        try {
            val tasks = hosts.map { host ->
                Callable {
                    val address = TvAddress(host, port)
                    if (probe(address, timeoutMs) == PortState.OPEN) address.also(onFound) else null
                }
            }
            return pool.invokeAll(tasks).mapNotNull { it.get() }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    /**
     * The name the home router knows for [host] (reverse DNS, usually from DHCP), without the
     * network suffix; null if it has none. Blocks while asking the router.
     */
    fun nameOf(host: String): String? = try {
        InetAddress.getByName(host).canonicalHostName
            .takeIf { it != host }
            ?.substringBefore('.')
            ?.takeIf { it.isNotBlank() }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun parseIpv4(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        return parts.fold(0L) { acc, part ->
            val value = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            (acc shl 8) or value.toLong()
        }
    }

    private fun formatIpv4(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 0xFF).toString() }
}
