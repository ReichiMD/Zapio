package io.github.reichimd.zapio.ui.files

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvFile
import io.github.reichimd.zapio.adb.TvPaths
import io.github.reichimd.zapio.data.TargetFolder
import io.github.reichimd.zapio.tv.FileProblem
import io.github.reichimd.zapio.tv.FolderState
import io.github.reichimd.zapio.tv.TransferState
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.IconTile
import io.github.reichimd.zapio.ui.common.KeepScreenOn
import io.github.reichimd.zapio.ui.common.PrimaryButton
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.common.rememberFolderPicker
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** What the user tapped on a file and still has to confirm or fill in. */
private sealed interface FileDialog {
    data class Delete(val file: TvFile) : FileDialog
    data class Rename(val file: TvFile) : FileDialog
    data object NewFolder : FileDialog
}

/**
 * The TV's storage, one folder at a time, with copies both ways. The back button goes up one folder;
 * the phone side is the system's file dialog.
 */
@Composable
fun FilesScreen(tvs: TvManager, targetFolder: TargetFolder, onBack: () -> Unit) {
    val files = tvs.files
    val folder by files.folder.collectAsState()
    val transfer by files.transfer.collectAsState()
    val problem by files.problem.collectAsState()
    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    // A file to save that waits for the folder dialog.
    var waitingToSave by remember { mutableStateOf<TvFile?>(null) }

    val chooseFolder = rememberFolderPicker(targetFolder) { waitingToSave?.let(files::saveToPhone) }
    val sendPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        files.sendToTv(uris)
    }

    LaunchedEffect(Unit) {
        targetFolder.refresh()
        files.refresh()
    }
    val atStorage = folder.path == TvPaths.STORAGE
    val up = { files.open(TvPaths.parent(folder.path)) }
    BackHandler(enabled = !atStorage, onBack = up)
    KeepScreenOn(transfer is TransferState.Running)

    val running = transfer is TransferState.Running
    FilesContent(
        folder = folder,
        transfer = transfer,
        onBack = if (atStorage) onBack else up,
        onOpen = files::open,
        onRefresh = files::refresh,
        onNewFolder = { dialog = FileDialog.NewFolder },
        onSend = { if (!running) sendPicker.launch(arrayOf("*/*")) },
        onSave = { file ->
            if (targetFolder.folder.value == null) {
                waitingToSave = file
                chooseFolder()
            } else {
                files.saveToPhone(file)
            }
        },
        onRename = { dialog = FileDialog.Rename(it) },
        onDelete = { dialog = FileDialog.Delete(it) },
        onCancelTransfer = files::cancelTransfer,
        onDismissTransfer = files::dismissTransfer,
        onChooseFolder = {
            waitingToSave = null
            chooseFolder()
        },
    )

    when (val open = dialog) {
        is FileDialog.Delete -> DeleteDialog(
            file = open.file,
            onDelete = {
                dialog = null
                files.delete(open.file)
            },
            onDismiss = { dialog = null },
        )
        is FileDialog.Rename -> NameDialog(
            title = stringResource(R.string.files_rename),
            initial = open.file.name,
            confirmText = stringResource(R.string.files_rename),
            onConfirm = { name ->
                dialog = null
                if (name != open.file.name) files.rename(open.file, name)
            },
            onDismiss = { dialog = null },
        )
        FileDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.files_new_folder),
            initial = "",
            confirmText = stringResource(R.string.files_create),
            onConfirm = { name ->
                dialog = null
                files.makeFolder(name)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    problem?.let { ProblemDialog(it, onDismiss = files::dismissProblem) }
}

@Composable
private fun FilesContent(
    folder: FolderState,
    transfer: TransferState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onSend: () -> Unit,
    onSave: (TvFile) -> Unit,
    onRename: (TvFile) -> Unit,
    onDelete: (TvFile) -> Unit,
    onCancelTransfer: () -> Unit,
    onDismissTransfer: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(
            leading = { BackButton(onBack) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(R.drawable.ic_refresh, stringResource(R.string.files_refresh), onRefresh)
                    if (folder is FolderState.Listed) {
                        RoundIconButton(R.drawable.ic_folder_plus, stringResource(R.string.files_new_folder), onNewFolder)
                    }
                }
            },
        ) {
            Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleLarge)
        }
        PathBar(folder.path, onOpen)
        if (transfer !is TransferState.Idle) {
            TransferCard(transfer, onCancel = onCancelTransfer, onDismiss = onDismissTransfer, onChooseFolder = onChooseFolder)
        }
        if (folder is FolderState.Listed && transfer !is TransferState.Running) {
            PrimaryButton(
                text = stringResource(R.string.files_send),
                onClick = onSend,
                icon = R.drawable.ic_upload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.weight(1f)) {
            when (folder) {
                is FolderState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = ZapioColors.Accent, strokeWidth = 3.dp)
                }
                is FolderState.Listed -> if (folder.files.isEmpty()) {
                    Note(stringResource(R.string.files_empty))
                } else {
                    FileList(folder, onOpen, onSave, onRename, onDelete)
                }
                is FolderState.NoAccess -> Message(R.drawable.ic_alert, stringResource(R.string.files_no_access)) {
                    SecondaryButton(stringResource(R.string.common_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
                }
                is FolderState.Missing -> Message(R.drawable.ic_alert, stringResource(R.string.files_missing)) {
                    SecondaryButton(
                        stringResource(R.string.files_to_storage),
                        onClick = { onOpen(TvPaths.STORAGE) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is FolderState.Failed -> Message(R.drawable.ic_alert, stringResource(problemText(folder.problem))) {
                    SecondaryButton(stringResource(R.string.connect_retry), onClick = onRefresh, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** "TV storage / Download / Movies", each part tappable. */
@Composable
private fun PathBar(path: String, onOpen: (String) -> Unit) {
    val parts = path.removePrefix(TvPaths.STORAGE).split('/').filter { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), reverseScrolling = true),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathPart(stringResource(R.string.files_storage), last = parts.isEmpty()) { onOpen(TvPaths.STORAGE) }
        var sub = TvPaths.STORAGE
        parts.forEachIndexed { index, part ->
            sub = TvPaths.child(sub, part)
            val target = sub
            Text(" / ", style = MaterialTheme.typography.bodyMedium, color = ZapioColors.TextMuted)
            PathPart(part, last = index == parts.lastIndex) { onOpen(target) }
        }
    }
}

@Composable
private fun PathPart(text: String, last: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !last, shape = RoundedCornerShape(10.dp), color = ZapioColors.Background) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (last) ZapioColors.Text else ZapioColors.Accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun FileList(
    folder: FolderState.Listed,
    onOpen: (String) -> Unit,
    onSave: (TvFile) -> Unit,
    onRename: (TvFile) -> Unit,
    onDelete: (TvFile) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // No keys: a name with a line break can show up twice, and a repeated key would crash.
        items(folder.files) { file ->
            FileRow(
                file = file,
                onOpenFolder = { onOpen(TvPaths.child(folder.path, file.name)) },
                onSave = { onSave(file) },
                onRename = { onRename(file) },
                onDelete = { onDelete(file) },
            )
        }
    }
}

@Composable
private fun FileRow(file: TvFile, onOpenFolder: () -> Unit, onSave: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Surface(
        // A folder opens; a file shows what can be done with it.
        onClick = { if (file.isFolder) onOpenFolder() else menu = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = if (file.isFolder) R.drawable.ic_folder else R.drawable.ic_file,
                tint = if (file.isFolder) ZapioColors.Accent else ZapioColors.TextMuted,
                size = 44,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val date = DateUtils.formatDateTime(
                    context,
                    file.modifiedMs,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NUMERIC_DATE,
                )
                val kind = if (file.isFolder) stringResource(R.string.files_folder) else Formatter.formatShortFileSize(context, file.size)
                Text(
                    stringResource(R.string.files_info, kind, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZapioColors.TextMuted,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        painterResource(R.drawable.ic_more),
                        contentDescription = stringResource(R.string.files_actions, file.name),
                        tint = ZapioColors.TextMuted,
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = ZapioColors.Surface2,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (!file.isFolder) {
                        MenuItem(R.drawable.ic_install, stringResource(R.string.files_save)) {
                            menu = false
                            onSave()
                        }
                    }
                    MenuItem(R.drawable.ic_edit, stringResource(R.string.files_rename)) {
                        menu = false
                        onRename()
                    }
                    MenuItem(R.drawable.ic_delete, stringResource(R.string.files_delete), danger = true) {
                        menu = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: Int, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) ZapioColors.Danger else ZapioColors.Text
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
    )
}

@Composable
private fun Message(icon: Int, text: String, action: @Composable () -> Unit) {
    ZapioCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = ZapioColors.Danger, modifier = Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        action()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = ZapioColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ProblemDialog(problem: FileProblem, onDismiss: () -> Unit) {
    ZapioDialog(
        title = stringResource(R.string.files_problem_title),
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (problem) {
                FileProblem.NameTaken -> Text(stringResource(R.string.files_problem_name_taken))
                is FileProblem.Connection -> Text(stringResource(problemText(problem.problem)))
                is FileProblem.Refused -> {
                    Text(stringResource(R.string.files_problem_refused))
                    if (problem.tvText.isNotBlank()) {
                        Text(stringResource(R.string.install_tv_says, problem.tvText), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FilesScreenPreview() {
    val files = listOf(
        TvFile("Download", isFolder = true, size = 0, modifiedMs = 1_761_000_000_000),
        TvFile("Movies", isFolder = true, size = 0, modifiedMs = 1_761_000_000_000),
        TvFile("holiday.mp4", isFolder = false, size = 1_400_000_000, modifiedMs = 1_761_000_000_000),
    )
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            FilesContent(
                folder = FolderState.Listed("/sdcard/Download", files),
                transfer = TransferState.Running(io.github.reichimd.zapio.tv.Direction.TO_TV, "holiday.mp4", 400_000_000, 1_400_000_000, 1, 2),
                onBack = {}, onOpen = {}, onRefresh = {}, onNewFolder = {}, onSend = {}, onSave = {},
                onRename = {}, onDelete = {}, onCancelTransfer = {}, onDismissTransfer = {}, onChooseFolder = {},
            )
        }
    }
}
