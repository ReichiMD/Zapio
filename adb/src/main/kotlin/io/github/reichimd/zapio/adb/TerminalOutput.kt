package io.github.reichimd.zapio.adb

/** One line of terminal output; [isError] for the error output. */
data class OutputLine(val text: String, val isError: Boolean)

/**
 * What a [TerminalOutput] holds right now; [droppedLines] were cut off at the start.
 * [changes] counts the pieces added so far, so an unchanged output can be skipped.
 */
data class OutputSnapshot(val lines: List<OutputLine>, val droppedLines: Long, val changes: Long)

/**
 * The output of one command as lines, for the screen. Keeps the last [maxLines], so a command
 * that never ends, like a log, cannot fill the phone's memory. Lines longer than
 * [maxLineLength] are broken. Normal and error output keep their own unfinished line.
 *
 * Filled by the reading thread and read by the screen, so every call is synchronized.
 */
class TerminalOutput(private val maxLines: Int = MAX_LINES, private val maxLineLength: Int = MAX_LINE_LENGTH) {

    private val lines = ArrayDeque<OutputLine>()
    private val open = StringBuilder()
    private val openError = StringBuilder()
    private var dropped = 0L
    private var changes = 0L

    @Synchronized
    fun add(text: String, isError: Boolean) {
        changes++
        val pending = if (isError) openError else open
        for (char in text) {
            when (char) {
                '\n' -> endLine(pending, isError)
                // Windows line ends and progress bars that go back to the line start.
                '\r' -> Unit
                else -> {
                    pending.append(char)
                    if (pending.length >= maxLineLength) endLine(pending, isError)
                }
            }
        }
    }

    /** The command ended: unfinished lines become lines. */
    @Synchronized
    fun finish() {
        if (open.isNotEmpty()) endLine(open, false)
        if (openError.isNotEmpty()) endLine(openError, true)
    }

    /** All lines, unfinished ones at the end. */
    @Synchronized
    fun snapshot(): OutputSnapshot {
        val all = ArrayList<OutputLine>(lines.size + 2)
        all.addAll(lines)
        if (open.isNotEmpty()) all += OutputLine(open.toString(), false)
        if (openError.isNotEmpty()) all += OutputLine(openError.toString(), true)
        return OutputSnapshot(all, dropped, changes)
    }

    private fun endLine(pending: StringBuilder, isError: Boolean) {
        lines.addLast(OutputLine(pending.toString(), isError))
        pending.setLength(0)
        while (lines.size > maxLines) {
            lines.removeFirst()
            dropped++
        }
    }

    companion object {
        const val MAX_LINES = 3_000
        const val MAX_LINE_LENGTH = 1_000
    }
}
