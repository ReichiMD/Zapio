package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Addresses are from 192.0.2.0/24, the range reserved for documentation. */
class TvAddressTest {

    @Test
    fun ipWithoutPortUsesDefaultPort() {
        assertEquals(TvAddress("192.0.2.10", 5555), TvAddress.parse("192.0.2.10"))
    }

    @Test
    fun ipWithPort() {
        assertEquals(TvAddress("192.0.2.10", 5037), TvAddress.parse(" 192.0.2.10:5037 "))
    }

    @Test
    fun hostName() {
        assertEquals(TvAddress("living-room.local"), TvAddress.parse("living-room.local"))
    }

    @Test
    fun rejectsBrokenInput() {
        listOf(
            "", " ", "192.0.2", "192.0.2.256", "192.0.2.07", "192.0.2.10:", "192.0.2.10:0",
            "192.0.2.10:65536", "192.0.2.10:port", "a:b:c", "-tv.local", "tv..local", "tv local",
        ).forEach { assertNull("should reject '$it'", TvAddress.parse(it)) }
    }

    @Test
    fun printsHostAndPort() {
        assertEquals("192.0.2.10:5555", TvAddress("192.0.2.10").toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsInvalidPort() {
        TvAddress("192.0.2.10", 70000)
    }
}
