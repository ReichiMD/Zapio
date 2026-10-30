package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPathsTest {

    @Test
    fun quotesForTheShell() {
        assertEquals("'a b'", TvPaths.quote("a b"))
        assertEquals("'it'\\''s'", TvPaths.quote("it's"))
        assertEquals("'\$(reboot)'", TvPaths.quote("\$(reboot)"))
    }

    @Test
    fun buildsPaths() {
        assertEquals("/sdcard/Movies", TvPaths.child("/sdcard", "Movies"))
        assertEquals("/sdcard/Movies", TvPaths.child("/sdcard/", "Movies"))
        assertEquals("/sdcard", TvPaths.parent("/sdcard/Movies"))
        assertEquals("/", TvPaths.parent("/sdcard"))
        assertEquals("/", TvPaths.parent("/"))
        assertEquals("Movies", TvPaths.name("/sdcard/Movies/"))
    }

    @Test
    fun checksNames() {
        assertTrue(TvPaths.isValidName("Urlaub 2026 (neu).mp4"))
        assertTrue(TvPaths.isValidName("Größe"))
        assertFalse(TvPaths.isValidName(""))
        assertFalse(TvPaths.isValidName("  "))
        assertFalse(TvPaths.isValidName("."))
        assertFalse(TvPaths.isValidName(".."))
        assertFalse(TvPaths.isValidName("a/b"))
        assertFalse(TvPaths.isValidName("a\nb"))
        assertFalse(TvPaths.isValidName("x".repeat(256)))
    }

    @Test
    fun numbersNames() {
        assertEquals("film.mp4", TvPaths.numbered("film.mp4", 0))
        assertEquals("film (2).mp4", TvPaths.numbered("film.mp4", 2))
        assertEquals("notes (1)", TvPaths.numbered("notes", 1))
        assertEquals(".hidden (1)", TvPaths.numbered(".hidden", 1))
    }

    @Test
    fun readsListing() {
        val output = """
            ZAPIO_LIST
            81a4 1500000000 1761000000 zebra film.mp4
            41f9 3488 1761000100 Movies
            41f9 3488 1761000200 alarms
            81a4 12 1761000300 .hidden
            broken line
        """.trimIndent()
        val listing = TvPaths.parseListing(output) as TvFolder.Listed
        assertEquals(listOf("alarms", "Movies", "zebra film.mp4"), listing.files.map { it.name })
        val film = listing.files.last()
        assertFalse(film.isFolder)
        assertEquals(1_500_000_000L, film.size)
        assertEquals(1_761_000_000_000L, film.modifiedMs)
        assertTrue(listing.files.first().isFolder)
        assertEquals(0L, listing.files.first().size)
    }

    @Test
    fun readsEmptyAndClosedFolders() {
        assertEquals(TvFolder.Listed(emptyList()), TvPaths.parseListing("ZAPIO_LIST\n"))
        assertEquals(TvFolder.NoAccess, TvPaths.parseListing("ZAPIO_NOACCESS\n"))
        assertEquals(TvFolder.Missing, TvPaths.parseListing("ZAPIO_MISSING\n"))
        assertEquals(TvFolder.Missing, TvPaths.parseListing(""))
    }

    @Test
    fun listCommandQuotesTheFolder() {
        val command = TvPaths.listCommand("/sdcard/it's")
        assertTrue(command, command.contains("'/sdcard/it'\\''s'"))
    }
}
