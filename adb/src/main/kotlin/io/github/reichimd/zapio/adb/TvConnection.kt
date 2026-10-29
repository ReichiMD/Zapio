package io.github.reichimd.zapio.adb

import dadb.Dadb
import dadb.InstallResult
import dadb.SyncResult
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

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
 */
class TvConnection private constructor(private val dadb: Dadb) : AutoCloseable {

    fun shell(command: String): ShellResult {
        val response = dadb.shell(command)
        return ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    /** Copies [input] to [remotePath] on the TV. The stream is closed afterwards. */
    fun push(
        input: InputStream,
        remotePath: String,
        mode: Int = DEFAULT_FILE_MODE,
        lastModifiedMs: Long = System.currentTimeMillis(),
    ): TvResult = input.source().use { source ->
        dadb.push(source, remotePath, mode, lastModifiedMs).toTvResult()
    }

    /** Copies [remotePath] from the TV into [output]. The stream is closed afterwards. */
    fun pull(output: OutputStream, remotePath: String): TvResult = output.sink().use { sink ->
        dadb.pull(sink, remotePath).toTvResult()
    }

    /** Installs an APK of [size] bytes read from [input]. The stream is closed afterwards. */
    fun install(input: InputStream, size: Long): TvResult = input.source().use { source ->
        when (val result = dadb.install(source, size)) {
            InstallResult.Success -> TvResult.Success
            is InstallResult.Failure -> TvResult.Failure(result.reason.trim())
        }
    }

    fun sendKey(key: TvKey): ShellResult = shell("input keyevent ${key.code}")

    override fun close() = dadb.close()

    private fun SyncResult.toTvResult(): TvResult = when (this) {
        SyncResult.Success -> TvResult.Success
        is SyncResult.Failure -> TvResult.Failure(reason)
    }

    companion object {
        /** rw-r--r-- */
        const val DEFAULT_FILE_MODE = 420

        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Opens a connection and checks it with a first command, so a refused key fails here. */
        fun connect(address: TvAddress, keys: TvKeys): TvConnection {
            val dadb = Dadb.create(address.host, address.port, keys.keyPair, CONNECT_TIMEOUT_MS)
            val connection = TvConnection(dadb)
            try {
                connection.shell("true")
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return connection
        }
    }
}
