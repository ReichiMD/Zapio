package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTextTest {

    @Test
    fun umlautsBecomeTwoLetters() {
        assertEquals("Groesse", TvText.forTv("Größe"))
        assertEquals("Aerger Oel Uebel", TvText.forTv("Ärger Öl Übel"))
        assertEquals("Groesse", TvText.forTv("Gro\u0308\u00dfe"))
    }

    @Test
    fun accentsAndQuotesBecomePlain() {
        assertEquals("Cafe \"creme\" - it's", TvText.forTv("Café „crème“ – it’s"))
    }

    @Test
    fun untypableCharactersAreLeftOut() {
        assertEquals("ok ", TvText.forTv("ok 😀"))
        assertEquals("a b", TvText.forTv("a\nb"))
    }

    @Test
    fun commandUsesPercentSForSpacesAndQuotesForTheShell() {
        assertEquals("input text 'Hallo%sWelt%s123'", TvText.inputCommand("Hallo Welt 123"))
        assertEquals("input text 'it'\\''s;%srm'", TvText.inputCommand("it's; rm"))
    }

    @Test
    fun nothingTypableGivesNoCommand() {
        assertNull(TvText.inputCommand("😀"))
        assertNull(TvText.inputCommand(""))
    }

    @Test
    fun keysInARowShareOneCommand() {
        val command = TvRemote.commandFor(
            listOf(
                RemoteInput.Key(TvKey.DPAD_DOWN),
                RemoteInput.Key(TvKey.DPAD_DOWN),
                RemoteInput.Text("Größe"),
                RemoteInput.Key(TvKey.ENTER),
            ),
        )
        assertEquals("input keyevent 20 20;input text 'Groesse';input keyevent 66", command)
    }

    @Test
    fun emptyBatchGivesNoCommand() {
        assertNull(TvRemote.commandFor(listOf(RemoteInput.Text("😀"))))
    }
}
