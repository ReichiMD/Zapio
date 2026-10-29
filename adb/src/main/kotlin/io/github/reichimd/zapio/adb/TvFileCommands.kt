package io.github.reichimd.zapio.adb

import io.github.reichimd.zapio.adb.TvPaths.quote

/*
 * File work on the TV through short shell commands. Copying itself goes through
 * [TvConnection.push] and [TvConnection.pull].
 */

/** Looks into [folder] on the TV. */
fun TvConnection.list(folder: String): TvFolder = TvPaths.parseListing(shellText(TvPaths.listCommand(folder)))

fun TvConnection.exists(path: String): Boolean =
    shell("[ -e ${quote(path)} ] && echo ${TvPaths.MARK_EXISTS}").output.trim() == TvPaths.MARK_EXISTS

/** Size of a file on the TV in bytes, or null if it is not there. */
fun TvConnection.fileSize(path: String): Long? = shell("stat -L -c %s -- ${quote(path)}").output.trim().toLongOrNull()

/** The first of `name`, `name (1)`, `name (2)` … that is still free in [folder]. */
fun TvConnection.freePath(folder: String, name: String): String {
    for (number in 0 until MAX_NUMBERED) {
        val path = TvPaths.child(folder, TvPaths.numbered(name, number))
        if (!exists(path)) return path
    }
    return TvPaths.child(folder, TvPaths.numbered(name, System.currentTimeMillis().toInt() and Int.MAX_VALUE))
}

/** Deletes a file, or a folder with everything in it. */
fun TvConnection.delete(path: String): TvResult = shell("rm -rf -- ${quote(path)}").toTvResult()

/** Renames [path] to [newName] in the same folder; fails if that name is taken. */
fun TvConnection.rename(path: String, newName: String): TvResult {
    val target = TvPaths.child(TvPaths.parent(path), newName)
    // The TV's storage ignores case, so "photo" → "Photo" finds itself; that rename is fine.
    val sameName = TvPaths.name(path).equals(newName, ignoreCase = true)
    if (!sameName) {
        if (exists(target)) return TvResult.Failure(TvPaths.MARK_EXISTS)
        return shell("mv -- ${quote(path)} ${quote(target)}").toTvResult()
    }
    // Same file to the storage, so a direct mv changes nothing; go through a temporary name.
    // If the second step fails, the file goes back to its old name instead of staying hidden.
    val from = quote(path)
    val temporary = quote(TvPaths.child(TvPaths.parent(path), ".zapio-rename-${System.currentTimeMillis()}"))
    return shell("mv -- $from $temporary && { mv -- $temporary ${quote(target)} || { mv -- $temporary $from; false; }; }")
        .toTvResult()
}

/** Makes a new folder; fails if the name is taken. */
fun TvConnection.makeFolder(path: String): TvResult {
    if (exists(path)) return TvResult.Failure(TvPaths.MARK_EXISTS)
    return shell("mkdir -- ${quote(path)}").toTvResult()
}

/** Takes a screenshot of the TV into [remotePath] as PNG. */
fun TvConnection.screenshot(remotePath: String): TvResult = shell("screencap -p ${quote(remotePath)}").toTvResult()

/**
 * Records the TV screen into [remotePath] as MP4 and returns when the recording ends:
 * after [seconds] (at most 180, the limit of the TV's recorder) or after [stopRecording].
 * [size] like `1280x720` lowers the resolution for TVs whose encoder cannot take the full one.
 */
fun TvConnection.record(remotePath: String, seconds: Int, size: String? = null): ShellResult {
    val limit = seconds.coerceIn(1, MAX_RECORD_SECONDS)
    val sizeOption = size?.let { "--size $it " } ?: ""
    return shell("screenrecord --time-limit $limit $sizeOption${quote(remotePath)}")
}

/** Ends a running [record] early; the recorder still finishes the file properly. */
fun TvConnection.stopRecording() {
    shell("pkill -INT screenrecord 2>/dev/null || kill -INT \$(pidof screenrecord) 2>/dev/null")
}

/**
 * Removes screenshots and recordings that an earlier, broken connection left in the scratch space.
 * Ends a recorder that may still run from then.
 */
fun TvConnection.cleanUpCaptures() {
    stopRecording()
    // The old recorder still finishes its file and holds the encoder for a moment; wait up to 3 s.
    shell(
        "for i in 1 2 3; do pidof screenrecord >/dev/null || break; sleep 1; done; " +
            "rm -f ${TvPaths.SCRATCH}/$CAPTURE_PREFIX*",
    )
}

/** Start of every capture file name in the scratch space; [cleanUpCaptures] removes them. */
const val CAPTURE_PREFIX = "zapio-capture-"

/** The longest recording the TV's recorder makes. */
const val MAX_RECORD_SECONDS = 180

private const val MAX_NUMBERED = 100

private fun ShellResult.toTvResult(): TvResult =
    if (isSuccess) TvResult.Success else TvResult.Failure((errorOutput.ifBlank { output }).trim())
