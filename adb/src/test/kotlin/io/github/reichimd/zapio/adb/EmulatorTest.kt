package io.github.reichimd.zapio.adb

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Talks to a running Android emulator the same way the app talks to a TV.
 *
 * Skipped unless ZAPIO_EMULATOR=1. CI starts an emulator and sets it, plus
 * ZAPIO_TEST_APK pointing at the app's debug APK. The emulator trusts the
 * host's ADB key, so that key is used here instead of the app's own.
 */
class EmulatorTest {

    private lateinit var tv: TvConnection

    @Before
    fun connect() {
        assumeTrue("needs a running emulator", System.getenv("ZAPIO_EMULATOR") == "1")
        val adbDir = File(System.getProperty("user.home"), ".android")
        val keys = TvKeys.load(File(adbDir, "adbkey"), File(adbDir, "adbkey.pub"))
        tv = TvConnection.connect(TvAddress("localhost", 5555), keys)
    }

    @After
    fun disconnect() {
        if (::tv.isInitialized) tv.close()
    }

    @Test
    fun shellReturnsOutput() {
        val result = tv.shell("echo zapio")
        assertEquals(0, result.exitCode)
        assertEquals("zapio", result.output.trim())
    }

    @Test
    fun pushAndPullRoundTrip() {
        val data = Random(42).nextBytes(512 * 1024)
        val remote = "/data/local/tmp/zapio-roundtrip.bin"
        assertEquals(TvResult.Success, tv.push(data.inputStream(), remote))
        val copy = ByteArrayOutputStream()
        assertEquals(TvResult.Success, tv.pull(copy, remote))
        assertArrayEquals(data, copy.toByteArray())
        tv.shell("rm $remote")
    }

    @Test
    fun pullOfMissingFileFails() {
        val result = tv.pull(ByteArrayOutputStream(), "/data/local/tmp/does-not-exist")
        assertTrue(result is TvResult.Failure)
    }

    @Test
    fun installsApk() {
        val apk = File(requireNotNull(System.getenv("ZAPIO_TEST_APK")) { "ZAPIO_TEST_APK not set" })
        var sent = 0L
        assertEquals(TvResult.Success, tv.install(apk.inputStream(), apk.length()) { sent = it })
        assertEquals(apk.length(), sent)
        val packages = tv.shell("pm list packages io.github.reichimd.zapio").output
        assertTrue(packages, packages.contains("package:io.github.reichimd.zapio"))
        tv.shell("pm uninstall io.github.reichimd.zapio")
    }

    @Test
    fun brokenApkReportsReason() {
        val junk = ByteArray(1024) { 7 }
        val result = tv.install(junk.inputStream(), junk.size.toLong())
        assertTrue(result is TvResult.Failure)
        val reason = (result as TvResult.Failure).reason
        assertEquals(reason, InstallProblem.NOT_AN_APK, InstallProblem.of(reason))
    }

    @Test
    fun reconnectsAfterDroppedConnection() {
        tv.close()
        tv.ensureConnected()
        assertEquals("zapio", tv.shell("echo zapio").output.trim())
    }

    @Test
    fun knowsItsCpuTypes() {
        assertTrue(tv.cpuTypes().contains("x86_64"))
    }

    @Test
    fun knowsItsName() {
        assertTrue(!tv.deviceName().isNullOrBlank())
    }

    @Test
    fun sendsKey() {
        assertEquals(0, tv.sendKey(TvKey.HOME).exitCode)
    }
}
