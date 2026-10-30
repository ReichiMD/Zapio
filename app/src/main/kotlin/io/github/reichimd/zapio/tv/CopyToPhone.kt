package io.github.reichimd.zapio.tv

import android.net.Uri
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvResult

/** How copying a TV file to the phone ended. */
internal sealed interface CopyToPhone {
    data class Saved(val file: Uri) : CopyToPhone

    /** The target folder cannot be written (any more); choose it again. */
    data object NoFolder : CopyToPhone
    data class Refused(val tvText: String) : CopyToPhone
}

/**
 * Copies [remotePath] from the TV into a new file [name] in [folder]; [onProgress] gets the bytes so far.
 * A failed or stopped copy leaves no half file on the phone. Blocks; connection faults come out as
 * [io.github.reichimd.zapio.adb.TvException], phone write errors as [PhoneFileException].
 */
internal fun PhoneFiles.copyFromTv(
    tv: TvConnection,
    remotePath: String,
    name: String,
    folder: Uri,
    onProgress: (Long) -> Unit,
): CopyToPhone {
    val file = create(folder, name) ?: return CopyToPhone.NoFolder
    var done = false
    try {
        val result = tv.pull(openWrite(file), remotePath, onProgress)
        if (result is TvResult.Failure) return CopyToPhone.Refused(result.reason)
        done = true
        return CopyToPhone.Saved(file)
    } finally {
        if (!done) delete(file)
    }
}
