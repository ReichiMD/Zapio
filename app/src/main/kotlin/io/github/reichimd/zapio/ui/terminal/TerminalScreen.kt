package io.github.reichimd.zapio.ui.terminal

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.OutputLine
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.tv.CommandStatus
import io.github.reichimd.zapio.tv.TerminalEntry
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme
import kotlinx.coroutines.launch

/** Longest command typed at once. */
private const val MAX_COMMAND = 1_000

/** Buttons for common commands. */
private enum class QuickCommand(@StringRes val label: Int, val command: String, val confirm: Boolean = false) {
    APPS(R.string.terminal_quick_apps, "pm list packages -3"),
    STORAGE(R.string.terminal_quick_storage, "df -h"),
    PROPERTIES(R.string.terminal_quick_properties, "getprop"),
    PROCESSES(R.string.terminal_quick_processes, "ps -A"),
    SCREEN(R.string.terminal_quick_screen, "wm size; wm density"),
    REBOOT(R.string.terminal_quick_reboot, "reboot", confirm = true),
}

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

/** Terminal for the connected TV: one command at a time, output while it runs, history until the app closes. */
@Composable
fun TerminalScreen(tvs: TvManager, onBack: () -> Unit) {
    val terminal = tvs.terminal
    val entries by terminal.entries.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TerminalContent(
        entries = entries,
        onRun = terminal::run,
        onStop = terminal::stop,
        onClear = terminal::clear,
        onCopy = { entry ->
            val text = entry.lines.joinToString("\n") { it.text }
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(entry.command, text))) }
        },
        onBack = onBack,
    )
}

@Composable
private fun TerminalContent(
    entries: List<TerminalEntry>,
    onRun: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onCopy: (TerminalEntry) -> Unit,
    onBack: () -> Unit,
) {
    val running = entries.lastOrNull()?.status == CommandStatus.Running
    var input by rememberSaveable { mutableStateOf("") }
    var askReboot by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(
            leading = { BackButton(onBack) },
            trailing = {
                if (entries.isNotEmpty() && !running) {
                    RoundIconButton(R.drawable.ic_delete, stringResource(R.string.terminal_clear), onClear)
                }
            },
        ) {
            Text(stringResource(R.string.terminal_title), style = MaterialTheme.typography.titleLarge)
        }
        QuickRow(enabled = !running) { quick ->
            if (quick.confirm) askReboot = true else onRun(quick.command)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = ZapioColors.Surface,
            border = BorderStroke(1.dp, ZapioColors.Line),
        ) {
            if (entries.isEmpty()) {
                Box(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.terminal_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZapioColors.TextMuted,
                    )
                }
            } else {
                OutputList(entries, onPick = { input = it.take(MAX_COMMAND) }, onCopy = onCopy)
            }
        }
        CommandBar(
            text = input,
            onText = { input = it.take(MAX_COMMAND) },
            running = running,
            onRun = {
                if (input.isNotBlank()) {
                    onRun(input)
                    input = ""
                }
            },
            onStop = onStop,
        )
    }

    if (askReboot) {
        ZapioDialog(
            title = stringResource(R.string.terminal_reboot_title),
            confirmText = stringResource(R.string.terminal_reboot_confirm),
            onConfirm = {
                askReboot = false
                onRun(QuickCommand.REBOOT.command)
            },
            onDismiss = { askReboot = false },
            dismissText = stringResource(R.string.common_cancel),
        ) {
            Text(stringResource(R.string.terminal_reboot_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuickRow(enabled: Boolean, onQuick: (QuickCommand) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickCommand.entries.forEach { quick ->
            Surface(
                onClick = { onQuick(quick) },
                enabled = enabled,
                shape = CircleShape,
                color = ZapioColors.Surface,
                contentColor = when {
                    !enabled -> ZapioColors.TextMuted
                    quick.confirm -> ZapioColors.Danger
                    else -> ZapioColors.Text
                },
                border = BorderStroke(1.dp, if (quick.confirm && enabled) ZapioColors.Danger else ZapioColors.Line),
            ) {
                Text(
                    stringResource(quick.label),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** All commands with their output; follows new output while it is scrolled to the end. */
@Composable
private fun OutputList(entries: List<TerminalEntry>, onPick: (String) -> Unit, onCopy: (TerminalEntry) -> Unit) {
    val list = rememberLazyListState()
    val last = entries.last()
    var shownId by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(last.id, last.version) {
        // The layout still shows the output from before: follow only if that was scrolled to its end.
        val info = list.layoutInfo
        val atEnd = info.visibleItemsInfo.lastOrNull()?.let { it.index >= info.totalItemsCount - 1 } ?: true
        // A new command is always shown.
        if (atEnd || last.id != shownId) list.scrollToItem((entries.sumOf(::itemCount) - 1).coerceAtLeast(0))
        shownId = last.id
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            entryItems(entry, first = index == 0, onPick = onPick, onCopy = onCopy)
        }
    }
}

/** How many list items [entryItems] makes for [entry]. */
private fun itemCount(entry: TerminalEntry): Int =
    2 + entry.lines.size + (if (entry.droppedLines > 0) 1 else 0) + (if (showsNoOutput(entry)) 1 else 0)

private fun showsNoOutput(entry: TerminalEntry) = entry.lines.isEmpty() && entry.status != CommandStatus.Running

private fun LazyListScope.entryItems(
    entry: TerminalEntry,
    first: Boolean,
    onPick: (String) -> Unit,
    onCopy: (TerminalEntry) -> Unit,
) {
    item(key = "head-${entry.id}") {
        EntryHead(entry, first, onPick, onCopy)
    }
    if (entry.droppedLines > 0) {
        item(key = "dropped-${entry.id}") {
            val count = entry.droppedLines.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            Text(
                pluralStringResource(R.plurals.terminal_dropped, count, count),
                style = Mono,
                color = ZapioColors.TextMuted,
            )
        }
    }
    items(count = entry.lines.size) { index ->
        OutputText(entry.lines[index])
    }
    if (showsNoOutput(entry)) {
        item(key = "empty-${entry.id}") {
            Text(stringResource(R.string.terminal_no_output), style = Mono, color = ZapioColors.TextMuted)
        }
    }
    item(key = "status-${entry.id}") {
        EntryStatus(entry.status)
    }
}

@Composable
private fun EntryHead(entry: TerminalEntry, first: Boolean, onPick: (String) -> Unit, onCopy: (TerminalEntry) -> Unit) {
    Row(
        modifier = Modifier.padding(top = if (first) 0.dp else 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onPick(entry.command) },
            modifier = Modifier.weight(1f),
            color = ZapioColors.Surface,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                stringResource(R.string.terminal_prompt) + " " + entry.command,
                style = Mono,
                color = ZapioColors.Accent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (entry.lines.isNotEmpty()) {
            Surface(
                onClick = { onCopy(entry) },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = ZapioColors.Surface,
                contentColor = ZapioColors.TextMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.terminal_copy, entry.command),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputText(line: OutputLine) {
    Text(
        line.text.ifEmpty { " " },
        style = Mono,
        color = if (line.isError) ZapioColors.Danger else ZapioColors.Text,
    )
}

@Composable
private fun EntryStatus(status: CommandStatus) {
    val (text, color) = when (status) {
        CommandStatus.Running -> stringResource(R.string.terminal_running) to ZapioColors.Accent
        CommandStatus.Stopped -> stringResource(R.string.terminal_stopped) to ZapioColors.TextMuted
        is CommandStatus.Exited -> when (status.code) {
            0 -> stringResource(R.string.terminal_done) to ZapioColors.Connected
            null -> stringResource(R.string.terminal_ended) to ZapioColors.TextMuted
            else -> stringResource(R.string.terminal_exit_code, status.code) to ZapioColors.Danger
        }
        is CommandStatus.Failed -> stringResource(problemText(status.problem)) to ZapioColors.Danger
    }
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun CommandBar(
    text: String,
    onText: (String) -> Unit,
    running: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    val label = stringResource(R.string.terminal_input_label)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.terminal_prompt), style = Mono, color = ZapioColors.Accent)
            BasicTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                singleLine = true,
                textStyle = Mono.copy(color = ZapioColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(ZapioColors.Accent),
                // Commands are lower case and must not be "corrected".
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { if (!running) onRun() }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.terminal_input_hint),
                                style = Mono.copy(fontSize = 15.sp),
                                color = ZapioColors.TextMuted,
                            )
                        }
                        field()
                    }
                },
            )
            val canRun = !running && text.isNotBlank()
            Surface(
                onClick = if (running) onStop else onRun,
                enabled = running || canRun,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = when {
                    running -> ZapioColors.Danger
                    canRun -> ZapioColors.Accent
                    else -> ZapioColors.Surface2
                },
                contentColor = if (running || canRun) ZapioColors.OnAccent else ZapioColors.TextMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_send),
                        contentDescription = stringResource(if (running) R.string.terminal_stop else R.string.terminal_run),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TerminalPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            TerminalContent(
                entries = listOf(
                    TerminalEntry(
                        1, 1, "ls /sdcard", CommandStatus.Exited(0),
                        listOf(OutputLine("Download", false), OutputLine("Movies", false)),
                    ),
                    TerminalEntry(2, 2, "cat nothing", CommandStatus.Exited(1), listOf(OutputLine("No such file", true))),
                    TerminalEntry(3, 3, "logcat", CommandStatus.Failed(TvProblem.CONNECTION_LOST)),
                ),
                onRun = {}, onStop = {}, onClear = {}, onCopy = {}, onBack = {},
            )
        }
    }
}
