package io.github.reichimd.zapio.adb

/** One entry of a TV folder. */
data class TvFile(val name: String, val isFolder: Boolean, val size: Long, val modifiedMs: Long)

/** What looking into a TV folder showed. */
sealed interface TvFolder {
    /** Folders first, then files, each sorted by name. */
    data class Listed(val files: List<TvFile>) : TvFolder

    /** The folder exists, but the TV does not let ADB look inside (for example `Android/data`). */
    data object NoAccess : TvFolder

    /** The folder is gone, or it is a file. */
    data object Missing : TvFolder
}

/** Paths and shell commands for files on the TV. Everything a user typed goes through [quote]. */
object TvPaths {

    /** The shared storage of the TV, the start of the file browser. */
    const val STORAGE = "/sdcard"

    /** Scratch space of the ADB shell; screenshots and recordings wait here until they are fetched. */
    const val SCRATCH = "/data/local/tmp"

    private const val MAX_NAME_BYTES = 255
    private const val FOLDER_MODE_BITS = 0xF000
    private const val FOLDER_MODE = 0x4000

    internal const val MARK_LIST = "ZAPIO_LIST"
    internal const val MARK_NO_ACCESS = "ZAPIO_NOACCESS"
    internal const val MARK_MISSING = "ZAPIO_MISSING"
    /** The reason of a rename or new folder that failed because the name is taken. */
    const val MARK_EXISTS = "ZAPIO_EXISTS"

    /** [text] as one shell word: single quotes around it, single quotes inside escaped. */
    fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    fun child(folder: String, name: String): String = folder.trimEnd('/') + "/" + name

    /** The folder above [path]; `/` stays `/`. */
    fun parent(path: String): String {
        val trimmed = path.trimEnd('/')
        val cut = trimmed.lastIndexOf('/')
        return if (cut <= 0) "/" else trimmed.substring(0, cut)
    }

    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** A name a file or folder can have: no slash, no line break, not `.` or `..`, not too long. */
    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\n' || it == '\r' || it == '\u0000' } &&
            name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES

    /** `name.ext`, then `name (1).ext`, `name (2).ext` … for finding a free name. */
    fun numbered(name: String, number: Int): String {
        if (number == 0) return name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)} ($number)${name.substring(dot)}" else "$name ($number)"
    }

    /**
     * Lists a folder with one call: one `stat` line per entry, `mode size time name`.
     * `-L` follows links, so `/sdcard` and linked folders show as folders. Hidden entries are left out.
     */
    internal fun listCommand(folder: String): String {
        val dir = quote(folder)
        return "if [ ! -d $dir ]; then echo $MARK_MISSING; " +
            // ls fails for a folder it cannot read at all, but also when only some entries are locked.
            "elif ! cd $dir 2>/dev/null || { ! ls >/dev/null 2>&1 && [ -z \"\$(ls 2>/dev/null)\" ]; }; " +
            "then echo $MARK_NO_ACCESS; " +
            "else echo $MARK_LIST; stat -L -c '%f %s %Y %n' -- * 2>/dev/null; fi"
    }

    /** Reads what [listCommand] printed; lines that do not fit are skipped. */
    internal fun parseListing(output: String): TvFolder {
        val lines = output.lines()
        val start = lines.indexOfFirst { it.trim() == MARK_LIST }
        if (start < 0) {
            return if (lines.any { it.trim() == MARK_NO_ACCESS }) TvFolder.NoAccess else TvFolder.Missing
        }
        val files = lines.drop(start + 1).mapNotNull(::parseLine)
        return TvFolder.Listed(
            files.sortedWith(compareBy<TvFile> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
        )
    }

    private fun parseLine(line: String): TvFile? {
        val parts = line.split(' ', limit = 4)
        if (parts.size < 4) return null
        val mode = parts[0].toIntOrNull(16) ?: return null
        val size = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toLongOrNull() ?: return null
        val name = parts[3]
        if (name.isEmpty() || name.startsWith('.')) return null
        val isFolder = mode and FOLDER_MODE_BITS == FOLDER_MODE
        return TvFile(name, isFolder, if (isFolder) 0 else size, seconds * 1000)
    }
}
