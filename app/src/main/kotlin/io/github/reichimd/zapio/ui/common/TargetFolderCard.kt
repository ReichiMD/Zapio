package io.github.reichimd.zapio.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.data.TargetFolder
import io.github.reichimd.zapio.ui.theme.ZapioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the system's folder dialog. A chosen folder becomes the target folder, then [onChosen] runs;
 * nothing happens when the dialog is closed without a choice.
 */
@Composable
fun rememberFolderPicker(targetFolder: TargetFolder, onChosen: () -> Unit = {}): () -> Unit {
    val chosen by rememberUpdatedState(onChosen)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            targetFolder.set(uri)
            if (targetFolder.folder.value != null) chosen()
        }
    }
    return { picker.launch(null) }
}

/** Shows the target folder on the phone with a button to choose another one. */
@Composable
fun TargetFolderCard(targetFolder: TargetFolder, onChoose: () -> Unit) {
    val folder by targetFolder.folder.collectAsState()
    val name by produceState<String?>(null, folder) {
        value = folder?.let { withContext(Dispatchers.IO) { targetFolder.name() } }
    }
    ZapioCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon = R.drawable.ic_folder, size = 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (folder == null) stringResource(R.string.folder_none) else name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (folder == null) ZapioColors.TextMuted else ZapioColors.Accent,
                )
            }
        }
        Text(stringResource(R.string.folder_text), style = MaterialTheme.typography.bodySmall, color = ZapioColors.TextMuted)
        SecondaryButton(
            text = stringResource(if (folder == null) R.string.folder_choose else R.string.folder_change),
            onClick = onChoose,
            icon = R.drawable.ic_folder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
