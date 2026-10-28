package io.github.reichimd.zapio.adb

/**
 * Network address of a TV with ADB over Wi-Fi enabled.
 *
 * Only plain host names and IPv4 addresses are accepted; the port defaults to 5555,
 * the port Android TV devices use for ADB over the network.
 */
data class TvAddress(val host: String, val port: Int = DEFAULT_PORT) {

    init {
        require(isValidHost(host)) { "Invalid host: $host" }
        require(port in 1..65535) { "Invalid port: $port" }
    }

    override fun toString(): String = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 5555

        private val HOST_LABEL = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
        private val IPV4_LIKE = Regex("[0-9.]+")

        /** Parses `host` or `host:port`; returns null if the input is not a usable address. */
        fun parse(input: String): TvAddress? {
            val text = input.trim()
            if (text.isEmpty()) return null
            val parts = text.split(':')
            if (parts.size > 2) return null
            val port = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else DEFAULT_PORT
            val host = parts[0]
            if (!isValidHost(host) || port !in 1..65535) return null
            return TvAddress(host, port)
        }

        private fun isValidHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            if (IPV4_LIKE.matches(host)) return isValidIpv4(host)
            return host.split('.').all { HOST_LABEL.matches(it) }
        }

        private fun isValidIpv4(host: String): Boolean {
            val parts = host.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 &&
                    (part == "0" || !part.startsWith("0")) &&
                    part.toInt() in 0..255
            }
        }
    }
}
