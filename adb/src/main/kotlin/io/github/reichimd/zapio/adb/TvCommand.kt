package io.github.reichimd.zapio.adb

import dadb.AdbShellPacket
import dadb.AdbShellStream
import dadb.AdbTimeoutException
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * A command running on the TV for the terminal, on its own shell stream.
 *
 * The output is handed over while it comes in. [stop] ends the command from another thread:
 * it closes only this stream, so the connection stays for everything else. The TV then ends
 * the program; without a stream it has nowhere to write to.
 */
class TvCommand private constructor(private val tv: TvConnection, private val stream: AdbShellStream) {

    @Volatile
    var isStopped = false
        private set

    /**
     * Reads until the command ends. [onOutput] gets the text as it comes, with `isError` for
     * the error output. Returns the exit code, or null when stopped or the TV gave none.
     */
    fun await(onOutput: (text: String, isError: Boolean) -> Unit): Int? {
        val out = Utf8Pieces()
        val err = Utf8Pieces()
        try {
            while (true) {
                when (val packet = stream.read()) {
                    is AdbShellPacket.StdOut -> out.decode(packet.payload).let { if (it.isNotEmpty()) onOutput(it, false) }
                    is AdbShellPacket.StdError -> err.decode(packet.payload).let { if (it.isNotEmpty()) onOutput(it, true) }
                    is AdbShellPacket.Exit -> {
                        out.finish().let { if (it.isNotEmpty()) onOutput(it, false) }
                        err.finish().let { if (it.isNotEmpty()) onOutput(it, true) }
                        return packet.payload[0].toInt() and 0xFF
                    }
                }
            }
        } catch (e: IOException) {
            if (isStopped) return null
            when {
                // The TV closed the stream without an exit code.
                e is EOFException -> return null
                // Silent for the whole read timeout. Maybe only the command is quiet, so the connection stays.
                e is AdbTimeoutException || e is SocketTimeoutException || e.cause is SocketTimeoutException -> {
                    stop()
                    throw TvException(TvProblem.NO_ANSWER, e)
                }
                else -> {
                    // The library keeps using a connection the TV has dropped; closing it makes the next call open a new one.
                    tv.close()
                    throw TvException(TvProblem.CONNECTION_LOST, e)
                }
            }
        } catch (e: IllegalStateException) {
            // A garbled packet: the connection is out of step.
            if (isStopped) return null
            tv.close()
            throw TvException(TvProblem.CONNECTION_LOST, e)
        } catch (e: IllegalArgumentException) {
            if (isStopped) return null
            tv.close()
            throw TvException(TvProblem.CONNECTION_LOST, e)
        } finally {
            runCatching { stream.close() }
        }
    }

    /** Ends the command. Safe from any thread, also more than once. */
    fun stop() {
        isStopped = true
        runCatching { stream.close() }
    }

    internal companion object {
        /**
         * Shell packet that closes the program's input. adb's shell protocol numbers it 4;
         * the library's own constant says 3, which the TV ignores.
         */
        private const val CLOSE_STDIN = 4

        fun start(tv: TvConnection, command: String): TvCommand {
            val stream = tv.openShellStream(command)
            try {
                // Nothing is typed into a running command, so a program waiting for input ends at once.
                stream.write(CLOSE_STDIN)
            } catch (e: Exception) {
                runCatching { stream.close() }
                tv.close()
                throw TvException(TvProblem.CONNECTION_LOST, e)
            }
            return TvCommand(tv, stream)
        }
    }
}

/**
 * Decodes UTF-8 that arrives in pieces. A character split across two pieces is held back
 * until the rest comes; broken bytes become a replacement character.
 */
internal class Utf8Pieces {

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var rest = ByteArray(0)

    fun decode(bytes: ByteArray): String = run(rest + bytes, endOfInput = false)

    /** What is still held back, at the end of the output. */
    fun finish(): String = run(rest, endOfInput = true)

    private fun run(bytes: ByteArray, endOfInput: Boolean): String {
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(bytes.size + 1)
        decoder.decode(input, output, endOfInput)
        if (endOfInput) {
            decoder.flush(output)
            decoder.reset()
        }
        rest = ByteArray(input.remaining()).also { input.get(it) }
        output.flip()
        return output.toString()
    }
}
