package io.github.reichimd.zapio.adb

import java.text.Normalizer

/**
 * Text for the TV's `input text` command, which only types plain ASCII.
 *
 * German umlauts become two letters (ä → ae), accents are dropped (é → e),
 * typographic quotes and dashes become plain ones, anything else that cannot be typed is left out.
 */
object TvText {

    private val replacements = mapOf(
        'ä' to "ae", 'ö' to "oe", 'ü' to "ue",
        'Ä' to "Ae", 'Ö' to "Oe", 'Ü' to "Ue",
        'ß' to "ss", 'ẞ' to "SS",
        '„' to "\"", '“' to "\"", '”' to "\"", '«' to "\"", '»' to "\"",
        '‚' to "'", '‘' to "'", '’' to "'",
        '–' to "-", '—' to "-", '…' to "...", '€' to "EUR",
    )

    private val combiningMarks = Regex("\\p{Mn}+")

    /** [text] as the TV will receive it. */
    fun forTv(text: String): String {
        val out = StringBuilder(text.length)
        // Composed first, so a pasted "o" plus combining umlaut still becomes "oe".
        for (char in Normalizer.normalize(text, Normalizer.Form.NFC)) {
            val replaced = replacements[char]
                ?: if (char.isWhitespace()) " " else Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
            for (c in replaced.replace(combiningMarks, "")) {
                if (c in ' '..'~') out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * The shell command that types [text] on the TV, or null if nothing of it can be typed.
     * `input text` reads `%s` as a space; the whole argument is quoted for the shell.
     */
    fun inputCommand(text: String): String? {
        val typed = forTv(text)
        if (typed.isEmpty()) return null
        val argument = typed.replace(" ", "%s").replace("'", "'\\''")
        return "input text '$argument'"
    }
}
