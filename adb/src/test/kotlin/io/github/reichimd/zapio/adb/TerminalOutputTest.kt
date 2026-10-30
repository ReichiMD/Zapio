package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalOutputTest {

    @Test
    fun splitsIntoLinesAndKeepsTheUnfinishedOne() {
        val output = TerminalOutput()
        output.add("one\ntw", isError = false)
        output.add("o\r\nthr", isError = false)
        assertEquals(listOf("one", "two", "thr"), output.snapshot().lines.map { it.text })
        output.add("ee", isError = false)
        output.finish()
        assertEquals(listOf("one", "two", "three"), output.snapshot().lines.map { it.text })
    }

    @Test
    fun errorOutputKeepsItsOwnLine() {
        val output = TerminalOutput()
        output.add("nor", isError = false)
        output.add("oops\n", isError = true)
        output.add("mal\n", isError = false)
        assertEquals(
            listOf(OutputLine("oops", true), OutputLine("normal", false)),
            output.snapshot().lines,
        )
    }

    @Test
    fun keepsOnlyTheLastLines() {
        val output = TerminalOutput(maxLines = 3)
        output.add((1..10).joinToString("\n", postfix = "\n"), isError = false)
        val snapshot = output.snapshot()
        assertEquals(listOf("8", "9", "10"), snapshot.lines.map { it.text })
        assertEquals(7L, snapshot.droppedLines)
    }

    @Test
    fun breaksLongLines() {
        val output = TerminalOutput(maxLineLength = 4)
        output.add("abcdefghij", isError = false)
        output.finish()
        assertEquals(listOf("abcd", "efgh", "ij"), output.snapshot().lines.map { it.text })
    }

    @Test
    fun utf8SplitAcrossPiecesStaysWhole() {
        val bytes = "Größe €".toByteArray(Charsets.UTF_8)
        val pieces = Utf8Pieces()
        val text = StringBuilder()
        // One byte at a time cuts every multi-byte character.
        for (byte in bytes) text.append(pieces.decode(byteArrayOf(byte)))
        text.append(pieces.finish())
        assertEquals("Größe €", text.toString())
    }

    @Test
    fun brokenUtf8BecomesReplacement() {
        val pieces = Utf8Pieces()
        val text = pieces.decode(byteArrayOf('a'.code.toByte(), 0xC3.toByte())) + pieces.finish()
        assertEquals("a�", text)
    }
}
