package io.github.reichimd.zapio.tv

import io.github.reichimd.zapio.adb.TvApp
import io.github.reichimd.zapio.adb.TvAppDetails
import io.github.reichimd.zapio.adb.TvConnection
import io.github.reichimd.zapio.adb.TvException
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.adb.TvResult
import io.github.reichimd.zapio.adb.appDetails
import io.github.reichimd.zapio.adb.clearData
import io.github.reichimd.zapio.adb.forceStop
import io.github.reichimd.zapio.adb.listApps
import io.github.reichimd.zapio.adb.openApp
import io.github.reichimd.zapio.adb.uninstall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

sealed interface AppList {
    data object Loading : AppList
    data class Loaded(val apps: List<TvApp>) : AppList
    data class Failed(val problem: TvProblem) : AppList
}

enum class AppAction { OPEN, STOP, CLEAR_DATA, UNINSTALL }

/** Something running on the TV right now. */
sealed interface AppsBusy {
    /** Removing [total] apps; [done] are through, [current] is the one being removed. */
    data class Uninstalling(val done: Int, val total: Int, val current: String) : AppsBusy
    data class Working(val action: AppAction, val packageName: String) : AppsBusy
}

/** What the last action came to, shown until dismissed. */
sealed interface AppsResult {
    /** [removed] apps are gone; [failed] maps the others to what the TV said. */
    data class Uninstalled(val removed: Int, val failed: Map<String, String>) : AppsResult
    data class Done(val action: AppAction, val packageName: String) : AppsResult
    data class Refused(val action: AppAction, val packageName: String, val tvText: String) : AppsResult
    data class Connection(val problem: TvProblem) : AppsResult
}

sealed interface DetailsState {
    val packageName: String

    data class Loading(override val packageName: String) : DetailsState
    data class Loaded(override val packageName: String, val details: TvAppDetails) : DetailsState
    data class Failed(override val packageName: String) : DetailsState
}

data class AppsState(
    val list: AppList = AppList.Loading,
    val search: String = "",
    val selected: Set<String> = emptySet(),
    val busy: AppsBusy? = null,
    val result: AppsResult? = null,
) {
    /** Apps that match the search, by package name. */
    val shown: List<TvApp>
        get() {
            val apps = (list as? AppList.Loaded)?.apps ?: return emptyList()
            val words = search.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            return apps.filter { app -> words.all { app.packageName.lowercase().contains(it) } }
        }
}

/**
 * The apps the user installed on the TV: list, search, select, uninstall several at once,
 * and open, stop or reset one. One action at a time.
 *
 * It does not wait for the install lock: the package manager on the TV keeps its own order.
 */
class AppsManager internal constructor(
    private val scope: CoroutineScope,
    private val connection: () -> TvConnection?,
) {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    private val _details = MutableStateFlow<DetailsState?>(null)
    val details: StateFlow<DetailsState?> = _details.asStateFlow()

    // Raised by every reset; a job from before a disconnect must not write into the new state.
    @Volatile
    private var session = 0

    private var listJob: Job? = null
    private var actionJob: Job? = null
    private var detailsJob: Job? = null

    /** Loads the list; not while apps are being removed, it would bring them back. */
    fun load() {
        // Read from the state, not the job: the uninstall itself reloads at its very end.
        if (_state.value.busy != null) return
        listJob?.cancel()
        val mine = session
        _state.update { it.copy(list = AppList.Loading) }
        listJob = scope.launch {
            val list = try {
                val tv = tv()
                runInterruptible { tv.ensureConnected() }
                AppList.Loaded(runInterruptible { tv.listApps() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvException) {
                AppList.Failed(e.problem)
            } catch (e: Exception) {
                AppList.Failed(TvProblem.CONNECTION_LOST)
            }
            ensureActive()
            if (session != mine) return@launch
            _state.update { state ->
                // Apps that are gone cannot stay selected.
                val names = (list as? AppList.Loaded)?.apps?.map { it.packageName }?.toSet().orEmpty()
                state.copy(list = list, selected = state.selected intersect names)
            }
        }
    }

    fun search(text: String) = _state.update { it.copy(search = text) }

    fun toggle(packageName: String) = _state.update {
        it.copy(selected = if (packageName in it.selected) it.selected - packageName else it.selected + packageName)
    }

    /** Selects all apps that match the search, or clears the selection if they all are already. */
    fun toggleAllShown() = _state.update { state ->
        val shown = state.shown.map { it.packageName }.toSet()
        state.copy(selected = if (state.selected.containsAll(shown)) state.selected - shown else state.selected + shown)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun dismissResult() = _state.update { it.copy(result = null) }

    /** Removes [packages] one after the other. */
    fun uninstall(packages: List<String>) {
        if (packages.isEmpty() || isBusy()) return
        val mine = session
        // A list still loading could bring the removed apps back; it is loaded again afterwards.
        val reload = listJob?.isActive == true
        listJob?.cancel()
        _state.update { it.copy(busy = AppsBusy.Uninstalling(0, packages.size, packages.first())) }
        actionJob = scope.launch {
            val failed = mutableMapOf<String, String>()
            // Kept outside the try: after a broken connection the ones already removed still leave the list.
            val gone = mutableSetOf<String>()
            val outcome = try {
                val tv = tv()
                runInterruptible { tv.ensureConnected() }
                for ((index, name) in packages.withIndex()) {
                    _state.update { it.copy(busy = AppsBusy.Uninstalling(index, packages.size, name)) }
                    when (val result = runInterruptible { tv.uninstall(name) }) {
                        TvResult.Success -> gone += name
                        is TvResult.Failure -> failed[name] = result.reason
                    }
                }
                AppsResult.Uninstalled(gone.size, failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvException) {
                AppsResult.Connection(e.problem)
            } catch (e: Exception) {
                AppsResult.Connection(TvProblem.CONNECTION_LOST)
            }
            ensureActive()
            if (session != mine) return@launch
            _state.update { state ->
                val apps = (state.list as? AppList.Loaded)?.apps
                state.copy(
                    busy = null,
                    result = outcome,
                    selected = state.selected - gone,
                    list = apps?.let { list -> AppList.Loaded(list.filter { it.packageName !in gone }) } ?: state.list,
                )
            }
            if (_details.value?.packageName in packages) _details.value = null
            if (reload) load()
        }
    }

    /** Opens, stops or resets one app. Uninstalling one goes through [uninstall]. */
    fun run(action: AppAction, packageName: String) {
        if (action == AppAction.UNINSTALL) return uninstall(listOf(packageName))
        if (isBusy()) return
        val mine = session
        _state.update { it.copy(busy = AppsBusy.Working(action, packageName)) }
        actionJob = scope.launch {
            val outcome = try {
                val tv = tv()
                runInterruptible { tv.ensureConnected() }
                val result = runInterruptible {
                    when (action) {
                        AppAction.OPEN -> tv.openApp(packageName)
                        AppAction.STOP -> tv.forceStop(packageName)
                        AppAction.CLEAR_DATA -> tv.clearData(packageName)
                        AppAction.UNINSTALL -> error("handled above")
                    }
                }
                when (result) {
                    TvResult.Success -> AppsResult.Done(action, packageName)
                    is TvResult.Failure -> AppsResult.Refused(action, packageName, result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvException) {
                AppsResult.Connection(e.problem)
            } catch (e: Exception) {
                AppsResult.Connection(TvProblem.CONNECTION_LOST)
            }
            ensureActive()
            if (session != mine) return@launch
            _state.update { it.copy(busy = null, result = outcome) }
        }
    }

    /** Asks the TV for version and install dates of one app. */
    fun showDetails(packageName: String) {
        detailsJob?.cancel()
        _details.value = DetailsState.Loading(packageName)
        detailsJob = scope.launch {
            val loaded = try {
                val tv = tv()
                DetailsState.Loaded(packageName, runInterruptible { tv.appDetails(packageName) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailsState.Failed(packageName)
            }
            ensureActive()
            _details.value = loaded
        }
    }

    fun hideDetails() {
        detailsJob?.cancel()
        _details.value = null
    }

    /** After a disconnect everything starts over. */
    internal fun reset() {
        session++
        listJob?.cancel()
        actionJob?.cancel()
        detailsJob?.cancel()
        _state.value = AppsState()
        _details.value = null
    }

    private fun isBusy() = actionJob?.isActive == true

    private fun tv(): TvConnection = connection() ?: throw TvException(TvProblem.CONNECTION_LOST)
}
