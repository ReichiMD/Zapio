package io.github.reichimd.zapio.ui.apps

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvApp
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.tv.AppAction
import io.github.reichimd.zapio.tv.AppList
import io.github.reichimd.zapio.tv.AppsBusy
import io.github.reichimd.zapio.tv.AppsResult
import io.github.reichimd.zapio.tv.AppsState
import io.github.reichimd.zapio.tv.DetailsState
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.common.zapioTextFieldColors
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Waiting for a yes: uninstall or clear data of [packages]. */
private data class Confirm(val action: AppAction, val packages: List<String>)

/** The user's apps on the TV. The list is loaded fresh each time the screen opens. */
@Composable
fun AppsScreen(tvs: TvManager, onBack: () -> Unit) {
    val apps = tvs.apps
    val state by apps.state.collectAsState()
    val details by apps.details.collectAsState()
    LaunchedEffect(Unit) { apps.load() }
    AppsContent(
        state = state,
        details = details,
        onRefresh = apps::load,
        onSearch = apps::search,
        onToggle = apps::toggle,
        onToggleAll = apps::toggleAllShown,
        onClearSelection = apps::clearSelection,
        onUninstall = apps::uninstall,
        onShowDetails = apps::showDetails,
        onHideDetails = apps::hideDetails,
        onAction = apps::run,
        onDismissResult = apps::dismissResult,
        onBack = onBack,
    )
}

@Composable
private fun AppsContent(
    state: AppsState,
    details: DetailsState?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onClearSelection: () -> Unit,
    onUninstall: (List<String>) -> Unit,
    onShowDetails: (String) -> Unit,
    onHideDetails: () -> Unit,
    onAction: (AppAction, String) -> Unit,
    onDismissResult: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val busy = state.busy != null
    val shown = state.shown

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(
            leading = { BackButton(onBack) },
            trailing = { RoundIconButton(R.drawable.ic_refresh, stringResource(R.string.apps_refresh), onRefresh) },
        ) {
            Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = zapioTextFieldColors(),
        )
        state.busy?.let { BusyCard(it) }
        state.result?.let { ResultCard(it, onDismissResult) }
        when (val list = state.list) {
            AppList.Loading -> Note(stringResource(R.string.apps_loading))
            is AppList.Failed -> Note(stringResource(problemText(list.problem)), danger = true)
            is AppList.Loaded -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.apps_count, shown.size, shown.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZapioColors.TextMuted,
                    )
                    if (shown.isNotEmpty()) {
                        val all = state.selected.containsAll(shown.map { it.packageName })
                        TextButton(onClick = onToggleAll, enabled = !busy) {
                            Text(
                                stringResource(if (all) R.string.apps_select_none else R.string.apps_select_all),
                                color = ZapioColors.Accent,
                            )
                        }
                    }
                }
                when {
                    list.apps.isEmpty() -> Note(stringResource(R.string.apps_empty))
                    shown.isEmpty() -> Note(stringResource(R.string.apps_no_match))
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shown, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                selected = app.packageName in state.selected,
                                enabled = !busy,
                                onToggle = { onToggle(app.packageName) },
                                onOpen = { onShowDetails(app.packageName) },
                            )
                        }
                        item(key = "note") { Note(stringResource(R.string.apps_note)) }
                    }
                }
            }
        }
        if (state.selected.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.apps_select_none),
                    onClick = onClearSelection,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = pluralStringResource(R.plurals.apps_uninstall_selected, state.selected.size, state.selected.size),
                    onClick = { if (!busy) confirm = Confirm(AppAction.UNINSTALL, state.selected.sorted()) },
                    icon = R.drawable.ic_delete,
                    danger = true,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }

    details?.let { shownDetails ->
        val app = shown.firstOrNull { it.packageName == shownDetails.packageName }
            ?: (state.list as? AppList.Loaded)?.apps?.firstOrNull { it.packageName == shownDetails.packageName }
        DetailsDialog(
            details = shownDetails,
            app = app,
            busy = busy,
            onAction = { action ->
                when (action) {
                    AppAction.UNINSTALL, AppAction.CLEAR_DATA -> confirm = Confirm(action, listOf(shownDetails.packageName))
                    else -> onAction(action, shownDetails.packageName)
                }
            },
            onDismiss = onHideDetails,
        )
    }

    confirm?.let { asked ->
        ConfirmDialog(
            confirm = asked,
            onConfirm = {
                confirm = null
                if (asked.action == AppAction.CLEAR_DATA) {
                    onAction(AppAction.CLEAR_DATA, asked.packages.single())
                } else {
                    onUninstall(asked.packages)
                }
                onHideDetails()
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun AppRow(app: TvApp, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val label = stringResource(R.string.apps_select, app.packageName)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) ZapioColors.Surface2 else ZapioColors.Surface,
        border = BorderStroke(1.dp, if (selected) ZapioColors.Accent else ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = ZapioColors.Accent,
                    uncheckedColor = ZapioColors.TextMuted,
                    checkmarkColor = ZapioColors.OnAccent,
                ),
                modifier = Modifier.semantics { contentDescription = label },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                app.apkBytes?.let {
                    Text(
                        Formatter.formatShortFileSize(context, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZapioColors.TextMuted,
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.apps_details),
                tint = ZapioColors.TextMuted,
            )
        }
    }
}

@Composable
private fun BusyCard(busy: AppsBusy) {
    ZapioCard {
        val text = when (busy) {
            is AppsBusy.Uninstalling -> stringResource(R.string.apps_uninstalling, busy.done + 1, busy.total, busy.current)
            is AppsBusy.Working -> stringResource(R.string.apps_working, busy.packageName)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (busy is AppsBusy.Uninstalling) {
            LinearProgressIndicator(
                progress = { busy.done.toFloat() / busy.total },
                modifier = Modifier.fillMaxWidth(),
                color = ZapioColors.Accent,
                trackColor = ZapioColors.Surface2,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = ZapioColors.Accent,
                trackColor = ZapioColors.Surface2,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ResultCard(result: AppsResult, onDismiss: () -> Unit) {
    ZapioCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (result) {
                    is AppsResult.Uninstalled -> {
                        if (result.removed > 0) {
                            Text(
                                pluralStringResource(R.plurals.apps_removed, result.removed, result.removed),
                                color = ZapioColors.Connected,
                            )
                        }
                        result.failed.forEach { (name, reason) ->
                            Text(stringResource(R.string.apps_not_removed, name), color = ZapioColors.Danger)
                            TvText(reason)
                        }
                    }
                    is AppsResult.Done -> Text(
                        stringResource(
                            when (result.action) {
                                AppAction.OPEN -> R.string.apps_done_open
                                AppAction.STOP -> R.string.apps_done_stop
                                else -> R.string.apps_done_clear
                            },
                            result.packageName,
                        ),
                        color = ZapioColors.Connected,
                    )
                    is AppsResult.Refused -> {
                        Text(stringResource(R.string.apps_refused, result.packageName), color = ZapioColors.Danger)
                        TvText(result.tvText)
                    }
                    is AppsResult.Connection -> Text(stringResource(problemText(result.problem)), color = ZapioColors.Danger)
                }
            }
            RoundIconButton(R.drawable.ic_close, stringResource(R.string.common_close), onDismiss)
        }
    }
}

@Composable
private fun TvText(text: String) {
    if (text.isBlank()) return
    Text(
        text.take(MAX_TV_TEXT),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = ZapioColors.TextMuted,
    )
}

@Composable
private fun DetailsDialog(
    details: DetailsState,
    app: TvApp?,
    busy: Boolean,
    onAction: (AppAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ZapioDialog(
        title = details.packageName,
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (details) {
                is DetailsState.Loading -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ZapioColors.Accent,
                    trackColor = ZapioColors.Surface2,
                )
                is DetailsState.Failed -> Text(stringResource(R.string.apps_details_failed))
                is DetailsState.Loaded -> {
                    val info = details.details
                    info.versionName?.let { name ->
                        Text(
                            info.versionCode?.let { stringResource(R.string.apps_version_code, name, it) }
                                ?: stringResource(R.string.apps_version, name),
                        )
                    }
                    info.firstInstall?.let { Text(stringResource(R.string.apps_installed, it)) }
                    info.lastUpdate?.let { Text(stringResource(R.string.apps_updated, it)) }
                }
            }
            app?.apkBytes?.let { Text(stringResource(R.string.apps_size, Formatter.formatShortFileSize(context, it))) }
            if (!busy) {
                SecondaryButton(
                    stringResource(R.string.apps_open),
                    onClick = { onAction(AppAction.OPEN) },
                    icon = R.drawable.ic_tv,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    stringResource(R.string.apps_stop),
                    onClick = { onAction(AppAction.STOP) },
                    icon = R.drawable.ic_stop,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    stringResource(R.string.apps_clear_data),
                    onClick = { onAction(AppAction.CLEAR_DATA) },
                    icon = R.drawable.ic_refresh,
                    danger = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    stringResource(R.string.apps_uninstall),
                    onClick = { onAction(AppAction.UNINSTALL) },
                    icon = R.drawable.ic_delete,
                    danger = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(confirm: Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val one = confirm.packages.singleOrNull()
    val title = when {
        confirm.action == AppAction.CLEAR_DATA -> stringResource(R.string.apps_confirm_clear_title, one.orEmpty())
        one != null -> stringResource(R.string.apps_confirm_uninstall_title, one)
        else -> pluralStringResource(R.plurals.apps_confirm_uninstall_many_title, confirm.packages.size, confirm.packages.size)
    }
    ZapioDialog(
        title = title,
        confirmText = stringResource(if (confirm.action == AppAction.CLEAR_DATA) R.string.apps_clear_data else R.string.apps_uninstall),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    if (confirm.action == AppAction.CLEAR_DATA) R.string.apps_confirm_clear_text else R.string.apps_confirm_uninstall_text,
                ),
            )
            if (one == null) {
                confirm.packages.take(MAX_LISTED).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                val more = confirm.packages.size - MAX_LISTED
                if (more > 0) Text(pluralStringResource(R.plurals.apps_and_more, more, more))
            }
        }
    }
}

@Composable
private fun Note(text: String, danger: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (danger) ZapioColors.Danger else ZapioColors.TextMuted,
    )
}

/** Names shown in the confirmation for many apps. */
private const val MAX_LISTED = 8

/** The TV's own words can be long stack traces; the start says enough. */
private const val MAX_TV_TEXT = 300

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AppsPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            AppsContent(
                state = AppsState(
                    list = AppList.Loaded(
                        listOf(
                            TvApp("com.example.player", "/data/app/a/base.apk", 24_000_000),
                            TvApp("com.example.player.test", "/data/app/b/base.apk", 25_000_000),
                            TvApp("org.example.files", "/data/app/c/base.apk", null),
                        ),
                    ),
                    selected = setOf("com.example.player.test"),
                    result = AppsResult.Connection(TvProblem.CONNECTION_LOST),
                ),
                details = null,
                onRefresh = {}, onSearch = {}, onToggle = {}, onToggleAll = {}, onClearSelection = {},
                onUninstall = {}, onShowDetails = {}, onHideDetails = {}, onAction = { _, _ -> },
                onDismissResult = {}, onBack = {},
            )
        }
    }
}
