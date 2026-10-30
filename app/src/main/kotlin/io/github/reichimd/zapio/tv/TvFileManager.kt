package io.github.reichimd.zapio.tv

import android.net.Uri
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvException
import io.github.reichimd.zapio.adb.TvFile
import io.github.reichimd.zapio.adb.TvFolder
import io.github.reichimd.zapio.adb.TvPaths
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.adb.TvResult
import io.github.reichimd.zapio.adb.delete
import io.github.reichimd.zapio.adb.freePath
import io.github.reichimd.zapio.adb.list
import io.github.reichimd.zapio.adb.makeFolder
import io.github.reichimd.zapio.adb.rename
import io.github.reichimd.zapio.data.TargetFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** What the file browser shows for the folder at [path]. */
sealed interface FolderState {
    val path: String

    data class Loading(override val path: String) : FolderState
    data class Listed(override val path: String, val files: List<TvFile>) : FolderState
    data class NoAccess(override val path: String) : FolderState
    data class Missing(override val path: String) : FolderState
    data class Failed(override val path: String, val problem: TvProblem) : FolderState
}

enum class Direction { TO_PHONE, TO_TV }

sealed interface TransferState {
    data object Idle : TransferState

    /** File [number] of [count]; [totalBytes] is 0 when the phone does not know the size. */
    data class Running(
        val direction: Direction,
        val fileName: String,
        val sentBytes: Long,
        val totalBytes: Long,
        val number: Int,
        val count: Int,
    ) : TransferState

    data class Done(val direction: Direction, val fileName: String, val count: Int) : TransferState
    data class Cancelled(val direction: Direction) : TransferState
    data class Failed(val direction: Direction, val fileName: String?, val reason: TransferFailure) : TransferState
}

sealed interface TransferFailure {
    /** The target folder on the phone is not chosen or cannot be written. */
    data object NoFolder : TransferFailure
    data object PhoneFile : TransferFailure
    data class Connection(val problem: TvProblem) : TransferFailure
    data class Refused(val tvText: String) : TransferFailure
}

/** Thrown inside a copy that [TvFileManager.cancelTransfer] stopped before it began. */
private class TransferStopped : RuntimeException()

/** Why deleting, renaming or making a folder did not work. */
sealed interface FileProblem {
    data object NameTaken : FileProblem
    data class Refused(val tvText: String) : FileProblem
    data class Connection(val problem: TvProblem) : FileProblem
}

/**
 * The file browser: one TV folder at a time, and copies between phone and TV.
 *
 * Copies wait for [lock], so they never run next to an install or a recording. Looking into
 * folders does not wait; ADB can do that while a copy runs.
 */
class TvFileManager internal constructor(
    private val scope: CoroutineScope,
    private val lock: Mutex,
    private val connection: () -> TvConnection?,
    private val phone: PhoneFiles,
    private val target: TargetFolder,
) {
    private val _folder = MutableStateFlow<FolderState>(FolderState.Loading(TvPaths.STORAGE))
    val folder: StateFlow<FolderState> = _folder.asStateFlow()

    private val _transfer = MutableStateFlow<TransferState>(TransferState.Idle)
    val transfer: StateFlow<TransferState> = _transfer.asStateFlow()

    private val _problem = MutableStateFlow<FileProblem?>(null)

    /** The last failed delete, rename or new folder, until [dismissProblem]. */
    val problem: StateFlow<FileProblem?> = _problem.asStateFlow()

    // Counted up for every folder opened and for every disconnect; a result that comes back
    // for an older count belongs to a folder or TV that is no longer on screen and is dropped.
    private val folderCount = AtomicInteger()
    private val tvCount = AtomicInteger()

    @Volatile
    private var transferJob: Job? = null

    // Set from the screen, read by the copy on another thread.
    @Volatile
    private var cancelled = false
    @Volatile
    private var copying: TvConnection? = null
    private var direction = Direction.TO_TV

    val isTransferRunning: Boolean get() = transferJob?.isActive == true

    /** Shows [path]; going above the TV's storage is not offered. */
    fun open(path: String) {
        val id = synchronized(this) {
            _folder.value = FolderState.Loading(path)
            folderCount.incrementAndGet()
        }
        scope.launch {
            val state = try {
                when (val listing = tvCall { list(path) }) {
                    is TvFolder.Listed -> FolderState.Listed(path, listing.files)
                    TvFolder.NoAccess -> FolderState.NoAccess(path)
                    TvFolder.Missing -> FolderState.Missing(path)
                }
            } catch (e: TvException) {
                FolderState.Failed(path, e.problem)
            }
            synchronized(this@TvFileManager) {
                if (id == folderCount.get()) _folder.value = state
            }
        }
    }

    fun refresh() = open(_folder.value.path)

    fun delete(file: TvFile) = change { path -> delete(TvPaths.child(path, file.name)) }

    fun rename(file: TvFile, newName: String) = change { path -> rename(TvPaths.child(path, file.name), newName) }

    fun makeFolder(name: String) = change { path -> makeFolder(TvPaths.child(path, name)) }

    fun dismissProblem() {
        _problem.value = null
    }

    /** Copies phone files into the folder that is open now. Existing files on the TV are kept; the copy gets a number. */
    fun sendToTv(files: List<Uri>) {
        if (files.isEmpty() || isTransferRunning) return
        val folder = _folder.value.path
        startTransfer(Direction.TO_TV, firstName = "", count = files.size) {
            var lastName = ""
            for ((index, uri) in files.withIndex()) {
                val name = safeName(phone.displayName(uri))
                lastName = name
                val size = phone.size(uri) ?: 0
                _transfer.value = TransferState.Running(Direction.TO_TV, name, 0, size, index + 1, files.size)
                val tv = prepare()
                val remote = runInterruptible { tv.freePath(folder, name) }
                val progress = progress(Direction.TO_TV, name, size, index + 1, files.size)
                val result = copy(tv, cleanup = { tv.delete(remote) }) {
                    tv.push(phone.openRead(uri), remote, onProgress = progress)
                }
                if (result is TvResult.Failure) {
                    runCatching { runInterruptible { tv.delete(remote) } }
                    return@startTransfer TransferState.Failed(Direction.TO_TV, name, TransferFailure.Refused(result.reason))
                }
            }
            if (_folder.value.path == folder) refresh()
            TransferState.Done(Direction.TO_TV, lastName, files.size)
        }
    }

    /** Copies a TV file of the open folder into the target folder on the phone. */
    fun saveToPhone(file: TvFile) {
        if (file.isFolder || isTransferRunning) return
        val remote = TvPaths.child(_folder.value.path, file.name)
        startTransfer(Direction.TO_PHONE, firstName = file.name, count = 1) {
            val folder = target.folder.value
                ?: return@startTransfer TransferState.Failed(Direction.TO_PHONE, file.name, TransferFailure.NoFolder)
            _transfer.value = TransferState.Running(Direction.TO_PHONE, file.name, 0, file.size, 1, 1)
            val tv = prepare()
            val progress = progress(Direction.TO_PHONE, file.name, file.size, 1, 1)
            when (val result = copy(tv, cleanup = {}) { phone.copyFromTv(tv, remote, file.name, folder, progress) }) {
                is CopyToPhone.Saved -> TransferState.Done(Direction.TO_PHONE, file.name, 1)
                CopyToPhone.NoFolder -> {
                    target.refresh()
                    TransferState.Failed(Direction.TO_PHONE, file.name, TransferFailure.NoFolder)
                }
                is CopyToPhone.Refused ->
                    TransferState.Failed(Direction.TO_PHONE, file.name, TransferFailure.Refused(result.tvText))
            }
        }
    }

    /** Stops a running copy; the half copied file is removed. */
    fun cancelTransfer() {
        if (!isTransferRunning) return
        cancelled = true
        // A copy blocks on the network; closing the connection frees it. The next call connects again.
        val tv = copying
        if (tv != null) {
            tv.close()
        } else {
            // Still waiting for its turn or checking the connection: nothing to clean up yet.
            transferJob?.cancel()
            _transfer.value = TransferState.Cancelled(direction)
        }
    }

    /** Clears the result of the last copy, unless one is still running. */
    fun dismissTransfer() {
        if (!isTransferRunning) _transfer.value = TransferState.Idle
    }

    /** After a disconnect: nothing of the old TV stays on screen. */
    internal fun reset() {
        cancelled = true
        tvCount.incrementAndGet()
        transferJob?.cancel()
        synchronized(this) {
            folderCount.incrementAndGet()
            _folder.value = FolderState.Loading(TvPaths.STORAGE)
        }
        _transfer.value = TransferState.Idle
        _problem.value = null
    }

    private fun change(action: TvConnection.(path: String) -> TvResult) {
        val path = _folder.value.path
        val tv = tvCount.get()
        _folder.value = FolderState.Loading(path)
        scope.launch {
            try {
                val result = tvCall { action(path) }
                if (tv != tvCount.get()) return@launch
                if (result is TvResult.Failure) {
                    _problem.value = if (result.reason == TvPaths.MARK_EXISTS) {
                        FileProblem.NameTaken
                    } else {
                        FileProblem.Refused(result.reason)
                    }
                }
            } catch (e: TvException) {
                if (tv != tvCount.get()) return@launch
                _problem.value = FileProblem.Connection(e.problem)
            }
            if (_folder.value.path == path) open(path)
        }
    }

    /**
     * Runs a short command; checks the connection first unless a copy or install is using it right now.
     * Not interruptible on purpose: an interrupt while reading would break the connection a copy shares.
     * A result nobody waits for any more is dropped by the callers.
     */
    private suspend fun <T> tvCall(block: TvConnection.() -> T): T {
        val tv = connection() ?: throw TvException(TvProblem.CONNECTION_LOST)
        return withContext(Dispatchers.IO) {
            if (!lock.isLocked) tv.ensureConnected()
            tv.block()
        }
    }

    private fun startTransfer(direction: Direction, firstName: String, count: Int, work: suspend () -> TransferState) {
        cancelled = false
        this.direction = direction
        // Shown at once, with its stop button, also while an install or recording still holds the lock.
        _transfer.value = TransferState.Running(direction, firstName, 0, 0, 1, count)
        transferJob = scope.launch {
            val outcome = lock.withLock {
                try {
                    if (cancelled) TransferState.Cancelled(direction) else work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    when {
                        cancelled -> TransferState.Cancelled(direction)
                        e is TvException -> TransferState.Failed(direction, null, TransferFailure.Connection(e.problem))
                        e is PhoneFileException || e is IOException || e is SecurityException ->
                            TransferState.Failed(direction, null, TransferFailure.PhoneFile)
                        else -> TransferState.Failed(direction, null, TransferFailure.Connection(TvProblem.CONNECTION_LOST))
                    }
                }
            }
            // After a disconnect the old result stays away.
            ensureActive()
            _transfer.value = withFileName(outcome)
        }
    }

    /** Keeps the name of the file that was being copied when the failure came without one. */
    private fun withFileName(outcome: TransferState): TransferState {
        val running = _transfer.value as? TransferState.Running ?: return outcome
        return if (outcome is TransferState.Failed && outcome.fileName == null) {
            outcome.copy(fileName = running.fileName.ifEmpty { null })
        } else {
            outcome
        }
    }

    private suspend fun prepare(): TvConnection {
        val tv = connection() ?: throw TvException(TvProblem.CONNECTION_LOST)
        // The TV may have dropped a connection that sat unused for a while.
        runInterruptible { tv.ensureConnected() }
        return tv
    }

    /**
     * Runs one copy so that [cancelTransfer] can stop it. When it does not finish,
     * [cleanup] removes the half file on the TV, as long as the TV is still the connected one.
     */
    private suspend fun <T> copy(tv: TvConnection, cleanup: TvConnection.() -> Unit, block: () -> T): T {
        var finished = false
        try {
            copying = tv
            if (cancelled) throw TransferStopped()
            return runInterruptible { block() }.also { finished = true }
        } finally {
            copying = null
            if (!finished) {
                withContext(NonCancellable) {
                    if (connection() === tv) runCatching { runInterruptible { tv.cleanup() } }
                }
            }
        }
    }

    private fun progress(direction: Direction, name: String, total: Long, number: Int, count: Int): (Long) -> Unit {
        var shown = -1L
        return { sent ->
            // Every percent, or every megabyte when the size is unknown; more would only keep the screen busy.
            val step = if (total > 0) sent * 100 / total else sent / MEGABYTE
            if (step != shown && !cancelled) {
                shown = step
                _transfer.value = TransferState.Running(direction, name, sent, total, number, count)
            }
        }
    }

    private companion object {
        const val MEGABYTE = 1_048_576L

        /** A phone file name the TV can take; odd characters become `_`. */
        fun safeName(name: String?): String {
            val cleaned = (name ?: "").map { if (it == '/' || it.isISOControl()) '_' else it }.joinToString("").trim()
            return cleaned.takeIf { TvPaths.isValidName(it) && !it.startsWith('.') } ?: "file_${System.currentTimeMillis()}"
        }
    }
}
