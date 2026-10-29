package io.github.reichimd.zapio.tv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.reichimd.zapio.adb.InstallProblem
import io.github.reichimd.zapio.adb.PortState
import io.github.reichimd.zapio.adb.TvAddress
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvException
import io.github.reichimd.zapio.adb.TvKeys
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.adb.TvResult
import io.github.reichimd.zapio.adb.TvScanner
import io.github.reichimd.zapio.data.DeviceStore
import io.github.reichimd.zapio.data.SavedTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val address: TvAddress) : ConnectionState
    data class Connected(val tv: SavedTv) : ConnectionState
    data class Failed(val address: TvAddress, val problem: TvProblem) : ConnectionState
}

data class ScanState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val noWifi: Boolean = false,
    val found: List<TvAddress> = emptyList(),
)

sealed interface InstallState {
    data object Idle : InstallState
    data class Sending(val fileName: String, val sentBytes: Long, val totalBytes: Long) : InstallState
    data class Installing(val fileName: String) : InstallState
    data class Done(val fileName: String) : InstallState
    data class Failed(val fileName: String?, val reason: InstallFailure) : InstallState
}

sealed interface InstallFailure {
    data object FileUnreadable : InstallFailure
    data class Connection(val problem: TvProblem) : InstallFailure
    data class Rejected(val problem: InstallProblem, val tvText: String) : InstallFailure
}

/**
 * Everything the app does with TVs: one connection at a time, network search, installing.
 * Lives as long as the app process, so an install keeps running when its screen is left.
 */
class TvManager(private val context: Context, private val store: DeviceStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var connection: TvConnection? = null
    private var connectJob: Job? = null
    private var scanJob: Job? = null
    private var installJob: Job? = null

    private val keyDir = File(context.filesDir, "adb")
    private val privateKey = File(keyDir, "adbkey")
    private val publicKey = File(keyDir, "adbkey.pub")
    private var keys: TvKeys? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _ports = MutableStateFlow<Map<String, PortState>>(emptyMap())

    /** Last known port state of saved TVs, by [SavedTv.key]. Missing while being checked. */
    val ports: StateFlow<Map<String, PortState>> = _ports.asStateFlow()

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install.asStateFlow()

    fun connect(address: TvAddress) {
        val current = _state.value
        if (current is ConnectionState.Connected && current.tv.address == address) return
        connectJob?.cancel()
        _state.value = ConnectionState.Connecting(address)
        connectJob = scope.launch {
            lock.withLock {
                closeConnection()
                try {
                    val tv = runInterruptible { TvConnection.connect(address, keys()) }
                    val name = try {
                        tv.deviceName()
                    } catch (e: TvException) {
                        null
                    }
                    connection = tv
                    val saved = store.remember(address, name)
                    _ports.update { it + (saved.key to PortState.OPEN) }
                    _state.value = ConnectionState.Connected(saved)
                } catch (e: TvException) {
                    _state.value = ConnectionState.Failed(address, e.problem)
                }
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        if (_state.value !is ConnectionState.Connected) _state.value = ConnectionState.Idle
    }

    /** Forgets a failed attempt so its message goes away. */
    fun dismissFailure() {
        if (_state.value is ConnectionState.Failed) _state.value = ConnectionState.Idle
    }

    fun disconnect() {
        connectJob?.cancel()
        installJob?.cancel()
        scope.launch { lock.withLock { closeConnection() } }
        _state.value = ConnectionState.Idle
        _install.value = InstallState.Idle
    }

    fun forget(tv: SavedTv) {
        val current = _state.value
        if (current is ConnectionState.Connected && current.tv.key == tv.key) disconnect()
        store.forget(tv)
    }

    /** Throws away the key; every TV asks again whether to trust this phone. */
    fun newKey() {
        disconnect()
        scope.launch {
            lock.withLock {
                keys = null
                privateKey.delete()
                publicKey.delete()
            }
        }
    }

    /** Looks at the ADB port of every saved TV, to show which ones are ready. */
    fun checkSavedTvs() {
        scope.launch {
            val connectedKey = (_state.value as? ConnectionState.Connected)?.tv?.key
            store.devices.value
                .filter { it.key != connectedKey }
                .map { tv -> async { tv.key to TvScanner.probe(tv.address) } }
                .awaitAll()
                .let { results -> _ports.update { it + results } }
        }
    }

    /** Searches the Wi-Fi for open ADB ports; results appear in [scan] as they come in. */
    fun startScan() {
        if (_scan.value.running) return
        val network = LocalNetwork.find(context)
        if (network == null) {
            _scan.value = ScanState(finished = true, noWifi = true)
            return
        }
        _scan.value = ScanState(running = true)
        scanJob = scope.launch {
            val hosts = TvScanner.hostsAround(network.ownIp, network.prefixLength)
            val found = runInterruptible {
                TvScanner.scan(hosts) { address -> _scan.update { it.copy(found = it.found + address) } }
            }
            _scan.value = ScanState(finished = true, found = found)
            checkSavedTvs()
        }
    }

    fun clearScan() {
        scanJob?.cancel()
        _scan.value = ScanState()
    }

    /** Clears the result of the last install, unless one is still running. */
    fun resetInstall() {
        if (installJob?.isActive != true) _install.value = InstallState.Idle
    }

    fun install(uri: Uri) {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            val fileName = displayName(uri)
            val size = fileSize(uri)
            if (size == null || size <= 0) {
                _install.value = InstallState.Failed(fileName, InstallFailure.FileUnreadable)
                return@launch
            }
            _install.value = InstallState.Sending(fileName ?: "", 0, size)
            lock.withLock {
                val tv = connection
                if (tv == null) {
                    _install.value = InstallState.Failed(fileName, InstallFailure.Connection(TvProblem.CONNECTION_LOST))
                    return@withLock
                }
                _install.value = try {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
                    var lastPercent = -1L
                    val result = runInterruptible {
                        tv.install(input, size) { sent ->
                            val percent = sent * 100 / size
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _install.value = if (sent >= size) {
                                    InstallState.Installing(fileName ?: "")
                                } else {
                                    InstallState.Sending(fileName ?: "", sent, size)
                                }
                            }
                        }
                    }
                    when (result) {
                        TvResult.Success -> InstallState.Done(fileName ?: "")
                        is TvResult.Failure -> InstallState.Failed(
                            fileName,
                            InstallFailure.Rejected(InstallProblem.of(result.reason), result.reason),
                        )
                    }
                } catch (e: TvException) {
                    InstallState.Failed(fileName, InstallFailure.Connection(e.problem))
                } catch (e: IOException) {
                    InstallState.Failed(fileName, InstallFailure.FileUnreadable)
                } catch (e: SecurityException) {
                    InstallState.Failed(fileName, InstallFailure.FileUnreadable)
                }
            }
        }
    }

    private fun keys(): TvKeys = keys ?: TvKeys.loadOrCreate(privateKey, publicKey).also { keys = it }

    private fun closeConnection() {
        connection?.close()
        connection = null
    }

    private fun displayName(uri: Uri): String? = query(uri, OpenableColumns.DISPLAY_NAME) { getString(it) }

    /** Size from the file provider, or from the file itself if the provider does not know it. */
    private fun fileSize(uri: Uri): Long? {
        val known = query(uri, OpenableColumns.SIZE) { if (isNull(it)) null else getLong(it) }
        if (known != null && known > 0) return known
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun <T> query(uri: Uri, column: String, read: android.database.Cursor.(Int) -> T?): T? = try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst()) cursor.read(index) else null
        }
    } catch (e: SecurityException) {
        null
    }
}
