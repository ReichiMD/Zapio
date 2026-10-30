package io.github.reichimd.zapio.tv

import io.github.reichimd.zapio.adb.OutputLine
import io.github.reichimd.zapio.adb.TerminalOutput
import io.github.reichimd.zapio.adb.TvCommand
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvException
import io.github.reichimd.zapio.adb.TvProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

sealed interface CommandStatus {
    data object Running : CommandStatus
    data class Exited(val code: Int?) : CommandStatus
    data object Stopped : CommandStatus
    data class Failed(val problem: TvProblem) : CommandStatus
}

/** One command in the terminal with its output. [version] changes with every update, which keeps comparing cheap. */
data class TerminalEntry(
    val version: Long,
    val id: Long,
    val command: String,
    val status: CommandStatus,
    val lines: List<OutputLine> = emptyList(),
    val droppedLines: Long = 0,
)

/**
 * The terminal: runs one command at a time on the TV and shows its output while it runs.
 * The history stays until the app is closed, also across disconnects.
 *
 * Commands do not wait for the install lock: each one has its own stream on the connection.
 */
class TerminalManager internal constructor(
    private val scope: CoroutineScope,
    private val connection: () -> TvConnection?,
) {
    private val _entries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val entries: StateFlow<List<TerminalEntry>> = _entries.asStateFlow()

    private var nextId = 0L
    private var version = 0L

    // The running command and its job; stop() comes from the screen while the job runs on another thread.
    @Volatile
    private var current: Running? = null

    private class Running(val id: Long, val output: TerminalOutput) {
        @Volatile
        var command: TvCommand? = null
        @Volatile
        var stopRequested = false
        var job: Job? = null
        var shownChanges = -1L
    }

    val isRunning: Boolean get() = current != null

    @Synchronized
    fun run(text: String) {
        val command = text.trim()
        if (command.isEmpty() || current != null) return
        val running = Running(++nextId, TerminalOutput())
        current = running
        _entries.update { list -> (list + TerminalEntry(++version, running.id, command, CommandStatus.Running)).takeLast(MAX_ENTRIES) }
        running.job = scope.launch {
            val publisher = launch {
                while (true) {
                    delay(PUBLISH_MS)
                    publish(running, null)
                }
            }
            val end = try {
                val tv = connection() ?: throw TvException(TvProblem.CONNECTION_LOST)
                // The TV may have dropped a connection that sat unused for a while.
                runInterruptible { tv.ensureConnected() }
                if (running.stopRequested) return@launch
                val started = runInterruptible { tv.startCommand(command) }
                running.command = started
                // Stop pressed while the stream opened.
                if (running.stopRequested) started.stop()
                val code = runInterruptible { started.await { chunk, isError -> running.output.add(chunk, isError) } }
                if (started.isStopped) CommandStatus.Stopped else CommandStatus.Exited(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvException) {
                CommandStatus.Failed(e.problem)
            } catch (e: Exception) {
                CommandStatus.Failed(TvProblem.CONNECTION_LOST)
            } finally {
                publisher.cancel()
            }
            running.output.finish()
            publish(running, end)
        }
    }

    /** Stops the running command. The screen shows it stopped at once, even if the TV takes a moment. */
    fun stop() {
        val running = current ?: return
        running.stopRequested = true
        // Closing the stream writes to the network, which must not happen on the screen's thread.
        running.command?.let { command -> scope.launch { command.stop() } }
        running.output.finish()
        publish(running, CommandStatus.Stopped)
    }

    /** Clears the history, unless a command is running. */
    fun clear() {
        if (current == null) _entries.value = emptyList()
    }

    /** After a disconnect: the running command ends, the history stays. */
    internal fun reset() {
        val running = current ?: return
        running.stopRequested = true
        running.command?.let { command -> scope.launch { command.stop() } }
        running.job?.cancel()
        running.output.finish()
        publish(running, CommandStatus.Failed(TvProblem.CONNECTION_LOST))
    }

    /**
     * Shows the output so far; with [end] the command is over. An entry that already ended
     * stays as it is, so a late reader cannot overwrite a stop.
     */
    @Synchronized
    private fun publish(running: Running, end: CommandStatus?) {
        val snapshot = running.output.snapshot()
        // Nothing new: skip, so a quiet command does not redraw the screen all the time.
        if (end == null && snapshot.changes == running.shownChanges) return
        running.shownChanges = snapshot.changes
        _entries.update { list ->
            list.map { entry ->
                if (entry.id != running.id || entry.status != CommandStatus.Running) {
                    entry
                } else {
                    entry.copy(
                        version = ++version,
                        status = end ?: CommandStatus.Running,
                        lines = snapshot.lines,
                        droppedLines = snapshot.droppedLines,
                    )
                }
            }
        }
        if (end != null && current === running) current = null
    }

    private companion object {
        /** Output reaches the screen in steps of this, so a fast command does not flood it. */
        const val PUBLISH_MS = 150L

        /** Older commands leave the history. */
        const val MAX_ENTRIES = 30
    }
}
