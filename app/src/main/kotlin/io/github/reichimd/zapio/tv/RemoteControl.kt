package io.github.reichimd.zapio.tv

import io.github.reichimd.zapio.adb.RemoteInput
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

enum class RemoteStatus {
    /** Opening the shell on the TV. */
    STARTING,
    READY,

    /** The connection broke; trying again every few seconds. */
    RECONNECTING,
}

/**
 * Sends key presses and text to the TV while the remote control screen is open.
 *
 * One worker sends everything in order. Presses that pile up while the TV is busy go out
 * together in one batch. When idle, the worker checks the connection every few seconds,
 * so a broken Wi-Fi is noticed and the connection comes back on its own.
 */
class RemoteControl(private val scope: CoroutineScope) {

    private val inputs = Channel<RemoteInput>(Channel.UNLIMITED)
    private val waiting = AtomicInteger()

    // Started and stopped from the screen and from connection changes on other threads.
    @Volatile
    private var job: Job? = null

    private val _status = MutableStateFlow(RemoteStatus.STARTING)
    val status: StateFlow<RemoteStatus> = _status.asStateFlow()

    @Synchronized
    fun start(tv: TvConnection) {
        stop()
        waiting.set(0)
        _status.value = RemoteStatus.STARTING
        job = scope.launch {
            val remote = tv.remote()
            try {
                // The first round only opens the shell, so the first press is fast.
                var next = emptyList<RemoteInput>()
                while (true) {
                    try {
                        runInterruptible { if (next.isEmpty()) remote.ping() else remote.send(next) }
                        _status.value = RemoteStatus.READY
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Stopped meanwhile: the inputs and the status already belong to the next start.
                        ensureActive()
                        _status.value = RemoteStatus.RECONNECTING
                        // Presses from before the break would arrive late and surprise; drop them.
                        drain()
                        delay(RETRY_MS)
                        next = emptyList()
                        continue
                    }
                    next = nextBatch()
                }
            } finally {
                remote.close()
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        drain()
        _status.value = RemoteStatus.STARTING
    }

    fun press(key: TvKey) = offer(RemoteInput.Key(key))

    /** For a held button: only adds a press when none is waiting, so at most one runs on after letting go. */
    fun repeat(key: TvKey) {
        if (waiting.get() == 0) press(key)
    }

    fun type(text: String) = offer(RemoteInput.Text(text))

    private fun offer(input: RemoteInput) {
        if (job?.isActive != true) return
        waiting.incrementAndGet()
        inputs.trySend(input)
    }

    /** Everything that is waiting, or nothing after [PING_MS] without a press. */
    private suspend fun nextBatch(): List<RemoteInput> {
        val first = withTimeoutOrNull(PING_MS) { inputs.receive() } ?: return emptyList()
        val batch = mutableListOf(first)
        while (true) batch += inputs.tryReceive().getOrNull() ?: break
        waiting.addAndGet(-batch.size)
        return batch
    }

    private fun drain() {
        while (inputs.tryReceive().isSuccess) waiting.decrementAndGet()
    }

    private companion object {
        const val PING_MS = 4_000L
        const val RETRY_MS = 3_000L
    }
}
