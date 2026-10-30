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

    @Test
    fun remoteSendsKeysAndText() {
        tv.remote().use { remote ->
            remote.ping()
            remote.send(
                listOf(
                    RemoteInput.Key(TvKey.HOME),
                    RemoteInput.Text("Hallo Welt 123 Größe"),
                    RemoteInput.Key(TvKey.DEL),
                ),
            )
            // Shows in the CI log how fast a single key gets through the open shell.
            val count = 10
            val started = System.nanoTime()
            repeat(count) { remote.send(listOf(RemoteInput.Key(TvKey.DPAD_DOWN))) }
            val each = (System.nanoTime() - started) / 1_000_000 / count
            println("Remote: one key in $each ms on average")
        }
    }

    @Test
    fun remoteReconnectsAfterDroppedConnection() {
        tv.remote().use { remote ->
            remote.ping()
            tv.close()
            remote.ping()
            remote.send(listOf(RemoteInput.Key(TvKey.HOME)))
        }
    }

    @Test
    fun pushAndPullReportProgress() {
        val data = Random(7).nextBytes(3 * 1024 * 1024)
        val remote = "/data/local/tmp/zapio-progress.bin"
        var sent = 0L
        assertEquals(TvResult.Success, tv.push(data.inputStream(), remote, onProgress = { sent = it }))
        assertEquals(data.size.toLong(), sent)
        assertEquals(data.size.toLong(), tv.fileSize(remote))
        var received = 0L
        val copy = ByteArrayOutputStream()
        assertEquals(TvResult.Success, tv.pull(copy, remote) { received = it })
        assertEquals(data.size.toLong(), received)
        assertArrayEquals(data, copy.toByteArray())
        tv.delete(remote)
    }

    @Test
    fun managesFilesInStorage() {
        val root = TvPaths.child(TvPaths.STORAGE, "zapio-test it's")
        tv.delete(root)
        assertEquals(TvResult.Success, tv.makeFolder(root))
        assertTrue(tv.makeFolder(root) is TvResult.Failure)
        assertEquals(TvResult.Success, tv.push("hello".byteInputStream(), TvPaths.child(root, "a file.txt")))
        assertEquals(TvResult.Success, tv.makeFolder(TvPaths.child(root, "sub")))

        val listing = tv.list(root) as TvFolder.Listed
        assertEquals(listOf("sub", "a file.txt"), listing.files.map { it.name })
        assertTrue(listing.files[0].isFolder)
        assertEquals(5L, listing.files[1].size)

        assertEquals(TvPaths.child(root, "a file (1).txt"), tv.freePath(root, "a file.txt"))
        assertEquals(TvResult.Success, tv.rename(TvPaths.child(root, "a file.txt"), "renamed.txt"))
        assertTrue(tv.rename(TvPaths.child(root, "renamed.txt"), "sub") is TvResult.Failure)
        assertTrue(tv.exists(TvPaths.child(root, "renamed.txt")))

        assertEquals(TvResult.Success, tv.delete(root))
        assertEquals(TvFolder.Missing, tv.list(root))
    }

    @Test
    fun listsStorage() {
        val listing = tv.list(TvPaths.STORAGE)
        assertTrue(listing.toString(), listing is TvFolder.Listed)
        assertTrue((listing as TvFolder.Listed).files.any { it.name == "Download" && it.isFolder })
    }

    @Test
    fun takesScreenshot() {
        val remote = TvPaths.child(TvPaths.SCRATCH, "zapio-test.png")
        assertEquals(TvResult.Success, tv.screenshot(remote))
        val copy = ByteArrayOutputStream()
        assertEquals(TvResult.Success, tv.pull(copy, remote))
        val png = copy.toByteArray()
        assertTrue(png.size > 1000)
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])
        tv.delete(remote)
        assertTrue(!tv.exists(remote))
    }

    @Test
    fun recordsAndStopsEarly() {
        val remote = TvPaths.child(TvPaths.SCRATCH, "zapio-test.mp4")
        tv.delete(remote)
        val stopper = Thread {
            Thread.sleep(4_000)
            tv.stopRecording()
        }
        stopper.start()
        val started = System.nanoTime()
        val result = tv.record(remote, 60)
        val seconds = (System.nanoTime() - started) / 1_000_000_000
        stopper.join()
        println("Record: exit ${result.exitCode} after $seconds s, ${result.errorOutput.trim()}")
        assertTrue("stopped early, took $seconds s", seconds < 30)
        assertTrue((tv.fileSize(remote) ?: 0) > 0)
        tv.delete(remote)
    }
}
