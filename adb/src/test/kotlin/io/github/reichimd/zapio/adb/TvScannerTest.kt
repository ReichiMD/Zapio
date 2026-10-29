package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

/** Addresses are from 192.0.2.0/24 and 198.51.100.0/24, ranges reserved for documentation. */
class TvScannerTest {

    @Test
    fun slash24HasAllHostsButOwn() {
        val hosts = TvScanner.hostsAround("192.0.2.10", 24)
        assertEquals(253, hosts.size)
        assertEquals("192.0.2.1", hosts.first())
        assertEquals("192.0.2.254", hosts.last())
        assertTrue("192.0.2.10" !in hosts)
    }

    @Test
    fun largeNetworkIsCutToOwnSlash24() {
        val hosts = TvScanner.hostsAround("198.51.100.77", 16)
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("198.51.100.") })
    }

    @Test
    fun smallNetwork() {
        assertEquals(listOf("192.0.2.66"), TvScanner.hostsAround("192.0.2.65", 30))
        assertEquals(listOf("192.0.2.65"), TvScanner.hostsAround("192.0.2.66", 30))
    }

    @Test
    fun brokenInputGivesNoHosts() {
        assertEquals(emptyList<String>(), TvScanner.hostsAround("192.0.2", 24))
        assertEquals(emptyList<String>(), TvScanner.hostsAround("192.0.2.300", 24))
        assertEquals(emptyList<String>(), TvScanner.hostsAround("192.0.2.10", 32))
    }

    @Test
    fun probeSeesOpenAndClosedPort() {
        ServerSocket(0).use { server ->
            assertEquals(PortState.OPEN, TvScanner.probe(TvAddress("127.0.0.1", server.localPort)))
        }
        assertEquals(PortState.CLOSED, TvScanner.probe(TvAddress("127.0.0.1", freePort())))
    }

    @Test
    fun scanFindsOpenPort() {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val found = Collections.synchronizedList(mutableListOf<TvAddress>())
            val result = TvScanner.scan(listOf("127.0.0.1", "127.0.0.2"), server.localPort, onFound = { found += it })
            assertEquals(listOf(TvAddress("127.0.0.1", server.localPort)), result)
            assertEquals(result, found.toList())
        }
    }

    @Test
    fun connectToClosedPortSaysAdbOff() {
        val keys = TvKeys.loadOrCreate(
            kotlin.io.path.createTempDirectory().resolve("adbkey").toFile(),
            kotlin.io.path.createTempDirectory().resolve("adbkey.pub").toFile(),
        )
        val error = runCatching { TvConnection.connect(TvAddress("127.0.0.1", freePort()), keys) }.exceptionOrNull()
        assertEquals(TvProblem.ADB_OFF, (error as TvException).problem)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
