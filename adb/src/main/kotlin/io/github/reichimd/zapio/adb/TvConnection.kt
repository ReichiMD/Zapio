package io.github.reichimd.zapio.adb

import dadb.AdbTimeoutException
import dadb.Dadb
import dadb.InstallResult
import dadb.SyncResult
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Result of a shell command on the TV. */
data class ShellResult(val output: String, val errorOutput: String, val exitCode: Int) {
    val isSuccess: Boolean get() = exitCode == 0
}

/** Result of an operation that either works or comes back with the TV's reason. */
sealed interface TvResult {
    data object Success : TvResult
    data class Failure(val reason: String) : TvResult
}

/**
 * An open ADB connection to one TV.
 *
 * This is the only place that knows the ADB library, so it can be swapped out
 * without touching the app. Only stream-based library calls are used: the
 * file-based ones read Unix file modes, which Android does not provide.
 *
 * Connection faults come out as [TvException] with a [TvProblem].
 */
class TvConnection private constructor(private val dadb: Dadb) : AutoCloseable {

    fun shell(command: String): ShellResult = guard {
        val response = dadb.shell(command)
        ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    /** Copies [input] to [remotePath] on the TV. The stream is closed afterwards. */
    fun push(
        input: InputStream,
        remotePath: String,
        mode: Int = DEFAULT_FILE_MODE,
        lastModifiedMs: Long = System.currentTimeMillis(),
    ): TvResult = guard {
        input.source().use { source -> dadb.push(source, remotePath, mode, lastModifiedMs).toTvResult() }
    }

    /** Copies [remotePath] from the TV into [output]. The stream is closed afterwards. */
    fun pull(output: OutputStream, remotePath: String): TvResult = guard {
        output.sink().use { sink -> dadb.pull(sink, remotePath).toTvResult() }
    }

    /**
     * Installs an APK of [size] bytes read from [input]; [onProgress] gets the bytes sent so far.
     * Once all bytes are sent the TV still needs a moment to install. The stream is closed afterwards.
     */
    fun install(input: InputStream, size: Long, onProgress: (Long) -> Unit = {}): TvResult = guard {
        CountingInputStream(input, onProgress).use { counting ->
            if (dadb.supportsFeature("cmd")) {
                when (val result = dadb.install(counting.source(), size)) {
                    InstallResult.Success -> TvResult.Success
                    is InstallResult.Failure -> TvResult.Failure(result.reason.trim())
                }
            } else {
                installByPush(counting)
            }
        }
    }

    /**
     * Older systems without `cmd`: copy the APK over and run `pm install`. The library's own
     * fallback goes through a temporary file and its Unix mode, which fails on Android.
     */
    private fun installByPush(input: InputStream): TvResult {
        val remote = "/data/local/tmp/zapio-install.apk"
        val pushed = input.source().use { dadb.push(it, remote, DEFAULT_FILE_MODE, System.currentTimeMillis()) }
        if (pushed is SyncResult.Failure) return TvResult.Failure(pushed.reason)
        try {
            val response = dadb.shell("pm install -r $remote")
            val text = response.allOutput.trim()
            return if (text.lines().any { it.trim() == "Success" }) TvResult.Success else TvResult.Failure(text)
        } finally {
            dadb.shell("rm -f $remote")
        }
    }

    /** The name the TV shows for itself, or its model if it has none. */
    fun deviceName(): String? {
        val name = shell("settings get global device_name").output.trim()
        if (name.isNotEmpty() && name != "null") return name
        return shell("getprop ro.product.model").output.trim().ifEmpty { null }
    }

    fun sendKey(key: TvKey): ShellResult = shell("input keyevent ${key.code}")

    override fun close() = dadb.close()

    private fun SyncResult.toTvResult(): TvResult = when (this) {
        SyncResult.Success -> TvResult.Success
        is SyncResult.Failure -> TvResult.Failure(reason)
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: TvException) {
        throw e
    } catch (e: IOException) {
        val timedOut = e is AdbTimeoutException || e is SocketTimeoutException || e.cause is SocketTimeoutException
        throw TvException(if (timedOut) TvProblem.NO_ANSWER else TvProblem.CONNECTION_LOST, e)
    }

    companion object {
        /** rw-r--r-- */
        const val DEFAULT_FILE_MODE = 420

        private const val CONNECT_TIMEOUT_MS = 5_000

        /**
         * Longest silence on a read before the connection counts as dead. Long enough for a slow
         * TV to finish installing a large app; a later remote control must keep its shell busy.
         */
        private const val READ_TIMEOUT_MS = 300_000

        /** How long the TV's "Allow USB debugging?" prompt may stay unanswered. */
        const val APPROVAL_TIMEOUT_MS = 60_000L

        /**
         * Opens a connection and checks it with a first command, so a refused key fails here.
         *
         * On the first connection the TV asks whether to trust this phone; this waits up to
         * [approvalTimeoutMs] for the answer. The library cannot interrupt that wait, so on
         * timeout or interrupt the half-open connection is closed in the background once the TV
         * answers or the read timeout ends it.
         */
        fun connect(
            address: TvAddress,
            keys: TvKeys,
            approvalTimeoutMs: Long = APPROVAL_TIMEOUT_MS,
        ): TvConnection {
            when (TvScanner.probe(address, CONNECT_TIMEOUT_MS)) {
                PortState.OPEN -> Unit
                PortState.CLOSED -> throw TvException(TvProblem.ADB_OFF)
                PortState.NO_ANSWER -> throw TvException(TvProblem.NO_ANSWER)
            }
            val dadb = Dadb.create(address.host, address.port, keys.keyPair, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, keepAlive = true)
            val handshake = FutureTask { dadb.shell("true") }
            Thread(handshake, "zapio-connect").apply { isDaemon = true }.start()
            try {
                handshake.get(approvalTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: ExecutionException) {
                dadb.close()
                throw TvException(handshakeProblem(e.cause), e.cause)
            } catch (e: TimeoutException) {
                closeWhenDone(handshake, dadb)
                throw TvException(TvProblem.NOT_ALLOWED, e)
            } catch (e: InterruptedException) {
                closeWhenDone(handshake, dadb)
                throw e
            }
            return TvConnection(dadb)
        }

        /** The port was open, so a failing handshake almost always means the TV said no. */
        private fun handshakeProblem(cause: Throwable?): TvProblem =
            if (cause is IOException) TvProblem.NOT_ALLOWED else TvProblem.CONNECTION_LOST

        private fun closeWhenDone(handshake: FutureTask<*>, dadb: Dadb) {
            Thread({
                runCatching { handshake.get() }
                dadb.close()
            }, "zapio-connect-cleanup").apply { isDaemon = true }.start()
        }
    }
}
