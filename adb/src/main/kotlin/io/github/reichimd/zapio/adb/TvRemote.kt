package io.github.reichimd.zapio.adb

import dadb.AdbShellPacket
import dadb.AdbStreamOpenException
import dadb.AdbShellStream
import java.io.InterruptedIOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One thing to send with the remote control: a key press or a piece of text. */
sealed interface RemoteInput {
    data class Key(val key: TvKey) : RemoteInput
    data class Text(val text: String) : RemoteInput
}

/**
 * The remote control: one shell stays open on the TV, so a key press needs no new stream.
 *
 * Each batch is framed by two markers the shell echoes back. The first one shows within
 * a few seconds that the connection lives; the second one that the TV has handled the batch.
 * A batch is sent again only if writing it failed, so a key never fires twice. Without an
 * answer the shell is given up; the next call opens a new one, and if even that fails the
 * connection is opened again.
 *
 * One sender at a time; the app sends from a single worker.
 */
class TvRemote internal constructor(private val tv: TvConnection) : AutoCloseable {

    private var shell: RemoteShell? = null
    private var batch = 0

    /** Sends [inputs] in order and waits until the TV has handled them. */
    fun send(inputs: List<RemoteInput>) {
        val command = commandFor(inputs) ?: return
        run(command)
    }

    /** Checks the connection and opens it again if it broke. Keeps the shell from going idle. */
    fun ping() = run(":")

    private fun run(command: String) {
        for (attempt in 1..2) {
            shell?.let { old ->
                if (!old.isOpen) {
                    old.close()
                    shell = null
                }
            }
            val open = shell ?: openShell()
            val id = ++batch
            val start = "${MARK}_${id}_A"
            val end = "${MARK}_${id}_B"
            try {
                open.write("echo $start;$command;echo $end\n")
            } catch (e: InterruptedIOException) {
                throw InterruptedException()
            } catch (e: Exception) {
                // IOException, or IllegalStateException when the connection was closed meanwhile.
                // Nothing reached the TV, so sending again through a new shell cannot fire a key twice.
                closeShell()
                if (attempt == 2) throw TvException(tv.problemNow(), e)
                continue
            }
            if (!open.await(start, ANSWER_MS)) {
                // The batch may still arrive late after a Wi-Fi glitch, so it is not sent again.
                // The next call opens a new shell; that fails on a dead connection and connects again.
                closeShell()
                throw TvException(TvProblem.NO_ANSWER)
            }
            if (!open.await(end, RUN_MS)) {
                closeShell()
                throw TvException(TvProblem.NO_ANSWER)
            }
            return
        }
    }

    /**
     * Opens a shell. If that fails, the connection is broken: it is closed and opened once more,
     * the TV already trusts the key. Only here the connection gets closed, because the failed
     * open just used it; an install running on a healthy connection is never touched.
     */
    private fun openShell(): RemoteShell {
        for (attempt in 1..2) {
            val task = FutureTask { tv.dadb.openShell() }
            Thread(task, "zapio-remote-open").apply { isDaemon = true }.start()
            try {
                return RemoteShell(task.get(OPEN_MS, TimeUnit.MILLISECONDS)).also { shell = it }
            } catch (e: TimeoutException) {
                closeWhenDone(task)
                tv.dadb.close()
                if (attempt == 2) throw TvException(tv.problemNow(), e)
            } catch (e: ExecutionException) {
                // The TV refused the shell but the connection is fine; keep it for a running install.
                if (e.cause is AdbStreamOpenException) throw TvException(TvProblem.CONNECTION_LOST, e.cause)
                tv.dadb.close()
                if (attempt == 2) throw TvException(tv.problemNow(), e.cause)
            } catch (e: InterruptedException) {
                closeWhenDone(task)
                throw e
            }
        }
        error("unreachable")
    }

    private fun closeShell() {
        shell?.close()
        shell = null
    }

    override fun close() = closeShell()

    companion object {
        private const val MARK = "ZAPIO"

        /** The echo of the first marker is a round trip through the Wi-Fi. */
        private const val ANSWER_MS = 4_000L

        /** Long enough for a slow TV to type a long text. */
        private const val RUN_MS = 20_000L

        /** Opening a shell, connecting again if needed: TCP connect plus handshake; the TV already trusts the key. */
        private const val OPEN_MS = 8_000L

        /** Keys in a row go into one `input keyevent` call, which saves a program start on the TV. */
        internal fun commandFor(inputs: List<RemoteInput>): String? {
            val commands = mutableListOf<String>()
            val keys = mutableListOf<Int>()
            fun flushKeys() {
                if (keys.isNotEmpty()) commands += "input keyevent " + keys.joinToString(" ")
                keys.clear()
            }
            for (input in inputs) {
                when (input) {
                    is RemoteInput.Key -> keys += input.key.code
                    is RemoteInput.Text -> {
                        flushKeys()
                        TvText.inputCommand(input.text)?.let { commands += it }
                    }
                }
            }
            flushKeys()
            return commands.takeIf { it.isNotEmpty() }?.joinToString(";")
        }

        private fun closeWhenDone(task: FutureTask<AdbShellStream>) {
            Thread({ runCatching { task.get().close() } }, "zapio-remote-cleanup")
                .apply { isDaemon = true }.start()
        }
    }
}

/** An open shell; a thread reads what the TV prints and hands over complete lines. */
private class RemoteShell(private val stream: AdbShellStream) : AutoCloseable {

    private val lines = LinkedBlockingQueue<String>()

    @Volatile
    var isOpen = true
        private set

    init {
        Thread(::readLines, "zapio-remote-read").apply { isDaemon = true }.start()
    }

    fun write(text: String) = stream.write(text)

    /**
     * Waits for a line ending in [marker]. A shell that prints a prompt puts it in front
     * of the marker, so only the last word counts.
     */
    fun await(marker: String, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return false
            val line = lines.poll(left, TimeUnit.NANOSECONDS) ?: return false
            if (line === CLOSED) {
                lines.put(CLOSED)
                return false
            }
            if (line.trim().substringAfterLast(' ') == marker) return true
        }
    }

    private fun readLines() {
        val pending = StringBuilder()
        try {
            while (true) {
                when (val packet = stream.read()) {
                    is AdbShellPacket.StdOut -> {
                        pending.append(String(packet.payload, Charsets.UTF_8))
                        while (true) {
                            val end = pending.indexOf("\n")
                            if (end < 0) break
                            lines.put(pending.substring(0, end))
                            pending.delete(0, end + 1)
                        }
                    }
                    is AdbShellPacket.Exit -> break
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            // Stream or connection ended; the sender notices through CLOSED.
        } finally {
            isOpen = false
            lines.put(CLOSED)
        }
    }

    override fun close() {
        isOpen = false
        runCatching { stream.close() }
    }

    companion object {
        /** Compared by identity, so no line from the TV can look like it. */
        private val CLOSED = String(charArrayOf('\u0000'))
    }
}
