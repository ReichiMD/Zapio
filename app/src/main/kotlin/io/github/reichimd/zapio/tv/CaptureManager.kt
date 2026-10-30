package io.github.reichimd.zapio.tv

import android.net.Uri
import android.os.SystemClock
import io.github.reichimd.zapio.adb.CAPTURE_PREFIX
import io.github.reichimd.zapio.adb.MAX_RECORD_SECONDS
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvException
import io.github.reichimd.zapio.adb.TvPaths
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.adb.TvResult
import io.github.reichimd.zapio.adb.cleanUpCaptures
import io.github.reichimd.zapio.adb.delete
import io.github.reichimd.zapio.adb.fileSize
import io.github.reichimd.zapio.adb.record
import io.github.reichimd.zapio.adb.screenshot
import io.github.reichimd.zapio.adb.stopRecording
import io.github.reichimd.zapio.data.TargetFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class CaptureKind { SCREENSHOT, VIDEO }

sealed interface CaptureState {
    data object Idle : CaptureState

    /** Waiting for a copy or install to finish ([waiting], can be cancelled), or taking the screenshot. */
    data class Preparing(val kind: CaptureKind, val waiting: Boolean = true) : CaptureState

    /** Recording since [startedAt] (`SystemClock.elapsedRealtime`); ends by itself after [limitSeconds]. */
    data class Recording(val startedAt: Long, val limitSeconds: Int, val stopping: Boolean = false) : CaptureState

    /** Copying the picture or video to the phone; [totalBytes] is 0 if unknown. */
    data class Saving(val kind: CaptureKind, val sentBytes: Long, val totalBytes: Long) : CaptureState

    data class Saved(val kind: CaptureKind, val file: Uri, val fileName: String) : CaptureState
    data class Failed(val kind: CaptureKind, val reason: CaptureFailure) : CaptureState
}

sealed interface CaptureFailure {
    data object NoFolder : CaptureFailure
    data object PhoneFile : CaptureFailure
    data class Connection(val problem: TvProblem) : CaptureFailure

    /** The TV did not take the picture or video; [tvText] is what it said. */
    data class NotTaken(val tvText: String) : CaptureFailure
}

/**
 * Screenshots and screen videos of the TV. The TV saves them in its scratch space; from there
 * they are copied into the target folder on the phone and removed from the TV.
 */
class CaptureManager internal constructor(
    private val scope: CoroutineScope,
    private val lock: Mutex,
    private val connection: () -> TvConnection?,
    private val phone: PhoneFiles,
    private val target: TargetFolder,
) {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    // Claimed once: by the capture when it gets its turn, or by cancelWaiting before that. Whoever wins decides.
    private val claimed = AtomicBoolean()

    val isRunning: Boolean get() = job?.isActive == true

    fun screenshot() = start(CaptureKind.SCREENSHOT) { tv, folder ->
        val remote = scratchFile("png")
        withTvFile(tv, remote) {
            val taken = runInterruptible { tv.screenshot(remote) }
            if (taken is TvResult.Failure) return@withTvFile notTaken(CaptureKind.SCREENSHOT, taken.reason)
            fetch(tv, remote, CaptureKind.SCREENSHOT, folder)
        }
    }

    /** Records up to [seconds]; [stopRecording] ends it early. */
    fun startRecording(seconds: Int = MAX_RECORD_SECONDS) = start(CaptureKind.VIDEO) { tv, folder ->
        val remote = scratchFile("mp4")
        withTvFile(tv, remote) {
            var result = recordOnce(tv, remote, seconds, size = null)
            // Some TVs cannot encode their full screen size; then the recorder quits at once.
            if (result.failedEarly && !stopRequested) result = recordOnce(tv, remote, seconds, size = FALLBACK_SIZE)
            val size = runInterruptible { tv.fileSize(remote) } ?: 0
            if (size <= 0) return@withTvFile notTaken(CaptureKind.VIDEO, result.tvText)
            fetch(tv, remote, CaptureKind.VIDEO, folder, size)
        }
    }

    fun stopRecording() {
        val recording = _state.value as? CaptureState.Recording ?: return
        if (recording.stopping) return
        stopRequested = true
        _state.value = recording.copy(stopping = true)
        val tv = connection() ?: return
        scope.launch {
            // Right after the start the recorder may not run yet; try a few times while it still records.
            repeat(STOP_TRIES) {
                if (_state.value !is CaptureState.Recording) return@launch
                runCatching { runInterruptible { tv.stopRecording() } }
                delay(STOP_RETRY_MS)
            }
            // Still recording: let the user press Stop again.
            val still = _state.value as? CaptureState.Recording ?: return@launch
            _state.compareAndSet(still, still.copy(stopping = false))
        }
    }

    /** Gives up a screenshot or video that still waits for a copy or install to finish. */
    fun cancelWaiting() {
        if (_state.value !is CaptureState.Preparing || !claimed.compareAndSet(false, true)) return
        job?.cancel()
        _state.value = CaptureState.Idle
    }

    /** Clears the last result, unless something is still running. */
    fun dismiss() {
        if (!isRunning) _state.value = CaptureState.Idle
    }

    /** After a disconnect. A recording on the TV ends by itself at its time limit. */
    internal fun reset() {
        job?.cancel()
        _state.value = CaptureState.Idle
    }

    private class Recorded(val failedEarly: Boolean, val tvText: String)

    private suspend fun recordOnce(tv: TvConnection, remote: String, seconds: Int, size: String?): Recorded {
        val startedAt = SystemClock.elapsedRealtime()
        if (!stopRequested) _state.value = CaptureState.Recording(startedAt, seconds)
        val result = runInterruptible { tv.record(remote, seconds, size) }
        val early = !result.isSuccess && SystemClock.elapsedRealtime() - startedAt < EARLY_FAILURE_MS
        return Recorded(early, (result.errorOutput.ifBlank { result.output }).trim())
    }

    private fun start(kind: CaptureKind, work: suspend (TvConnection, Uri) -> CaptureState) {
        if (isRunning) return
        stopRequested = false
        claimed.set(false)
        _state.value = CaptureState.Preparing(kind)
        job = scope.launch {
            val outcome = lock.withLock {
                // Cancelled while waiting: give up without touching the TV.
                if (!claimed.compareAndSet(false, true)) return@launch
                _state.value = CaptureState.Preparing(kind, waiting = false)
                try {
                    val folder = target.folder.value
                        ?: return@withLock CaptureState.Failed(kind, CaptureFailure.NoFolder)
                    val tv = connection() ?: throw TvException(TvProblem.CONNECTION_LOST)
                    runInterruptible { tv.ensureConnected() }
                    // A capture left over from a broken connection may still run or lie around.
                    runInterruptible { tv.cleanUpCaptures() }
                    work(tv, folder)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TvException) {
                    CaptureState.Failed(kind, CaptureFailure.Connection(e.problem))
                } catch (e: PhoneFileException) {
                    CaptureState.Failed(kind, CaptureFailure.PhoneFile)
                } catch (e: IOException) {
                    CaptureState.Failed(kind, CaptureFailure.PhoneFile)
                } catch (e: SecurityException) {
                    CaptureState.Failed(kind, CaptureFailure.PhoneFile)
                } catch (e: Exception) {
                    CaptureState.Failed(kind, CaptureFailure.Connection(TvProblem.CONNECTION_LOST))
                }
            }
            // After a disconnect the old result stays away.
            ensureActive()
            _state.value = outcome
        }
    }

    /** Runs [block] and removes [remote] from the TV afterwards, however it ends. */
    private suspend fun withTvFile(tv: TvConnection, remote: String, block: suspend () -> CaptureState): CaptureState {
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                if (connection() === tv) runCatching { runInterruptible { tv.delete(remote) } }
            }
        }
    }

    private suspend fun fetch(
        tv: TvConnection,
        remote: String,
        kind: CaptureKind,
        folder: Uri,
        knownSize: Long? = null,
    ): CaptureState {
        val size = knownSize ?: runInterruptible { tv.fileSize(remote) } ?: 0
        _state.value = CaptureState.Saving(kind, 0, size)
        val name = fileName(kind)
        var shown = -1L
        val result = runInterruptible {
            phone.copyFromTv(tv, remote, name, folder) { sent ->
                val percent = if (size > 0) sent * 100 / size else 0
                if (percent != shown) {
                    shown = percent
                    _state.value = CaptureState.Saving(kind, sent, size)
                }
            }
        }
        return when (result) {
            is CopyToPhone.Saved -> CaptureState.Saved(kind, result.file, phone.displayName(result.file) ?: name)
            CopyToPhone.NoFolder -> {
                target.refresh()
                CaptureState.Failed(kind, CaptureFailure.NoFolder)
            }
            is CopyToPhone.Refused -> notTaken(kind, result.tvText)
        }
    }

    /** A new name each time, so a recorder from earlier can never write into this file. */
    private fun scratchFile(extension: String) =
        TvPaths.child(TvPaths.SCRATCH, "$CAPTURE_PREFIX${System.currentTimeMillis()}.$extension")

    private fun notTaken(kind: CaptureKind, tvText: String) = CaptureState.Failed(kind, CaptureFailure.NotTaken(tvText))

    private companion object {
        /** Lower size for TVs whose encoder cannot take the full screen, for example some 4K TVs. */
        const val FALLBACK_SIZE = "1280x720"

        /** A recorder that quits this fast did not record at all. */
        const val EARLY_FAILURE_MS = 5_000L

        const val STOP_TRIES = 3
        const val STOP_RETRY_MS = 2_000L

        fun fileName(kind: CaptureKind): String {
            val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            return when (kind) {
                CaptureKind.SCREENSHOT -> "Zapio_$time.png"
                CaptureKind.VIDEO -> "Zapio_$time.mp4"
            }
        }
    }
}
