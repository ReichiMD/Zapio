package io.github.reichimd.zapio.ui.files

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvFile
import io.github.reichimd.zapio.adb.TvPaths
import io.github.reichimd.zapio.tv.Direction
import io.github.reichimd.zapio.tv.TransferFailure
import io.github.reichimd.zapio.tv.TransferState
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.common.zapioTextFieldColors
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors

/** Progress or result of the last copy between phone and TV. */
@Composable
fun TransferCard(transfer: TransferState, onCancel: () -> Unit, onDismiss: () -> Unit, onChooseFolder: () -> Unit) {
    val context = LocalContext.current
    ZapioCard {
        when (transfer) {
            TransferState.Idle -> Unit
            is TransferState.Running -> {
                val heading = stringResource(
                    if (transfer.direction == Direction.TO_TV) R.string.transfer_to_tv else R.string.transfer_to_phone,
                )
                Text(heading, style = MaterialTheme.typography.labelSmall, color = ZapioColors.TextMuted)
                if (transfer.fileName.isNotEmpty()) Text(transfer.fileName, style = MaterialTheme.typography.titleMedium)
                if (transfer.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (transfer.sentBytes.toFloat() / transfer.totalBytes).coerceIn(0f, 1f) },
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
                val sent = Formatter.formatShortFileSize(context, transfer.sentBytes)
                val bytes = if (transfer.totalBytes > 0) {
                    stringResource(R.string.transfer_bytes, sent, Formatter.formatShortFileSize(context, transfer.totalBytes))
                } else {
                    sent
                }
                val line = if (transfer.count > 1) {
                    stringResource(R.string.files_info, stringResource(R.string.transfer_count, transfer.number, transfer.count), bytes)
                } else {
                    bytes
                }
                Text(line, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.transfer_keep_open), style = MaterialTheme.typography.bodySmall, color = ZapioColors.TextMuted)
                SecondaryButton(stringResource(R.string.transfer_cancel), onClick = onCancel, danger = true, modifier = Modifier.fillMaxWidth())
            }
            is TransferState.Done -> {
                Outcome(R.drawable.ic_check, done = true, stringResource(R.string.transfer_done_title))
                val text = when {
                    transfer.direction == Direction.TO_PHONE -> stringResource(R.string.transfer_done_to_phone, transfer.fileName)
                    transfer.count > 1 -> pluralStringResource(R.plurals.transfer_done_to_tv_many, transfer.count, transfer.count)
                    else -> stringResource(R.string.transfer_done_to_tv, transfer.fileName)
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(stringResource(R.string.common_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
            is TransferState.Cancelled -> {
                Text(stringResource(R.string.transfer_cancelled), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(stringResource(R.string.common_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
            is TransferState.Failed -> {
                Outcome(R.drawable.ic_alert, done = false, stringResource(R.string.transfer_failed))
                transfer.fileName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                when (val reason = transfer.reason) {
                    TransferFailure.NoFolder -> Text(stringResource(R.string.transfer_no_folder))
                    TransferFailure.PhoneFile -> Text(stringResource(R.string.transfer_phone_file))
                    is TransferFailure.Connection -> Text(stringResource(problemText(reason.problem)))
                    is TransferFailure.Refused -> {
                        Text(stringResource(R.string.transfer_refused))
                        if (reason.tvText.isNotBlank()) {
                            Text(
                                stringResource(R.string.install_tv_says, reason.tvText),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZapioColors.TextMuted,
                            )
                        }
                    }
                }
                if (transfer.reason == TransferFailure.NoFolder) {
                    SecondaryButton(stringResource(R.string.folder_choose), onClick = onChooseFolder, modifier = Modifier.fillMaxWidth())
                }
                SecondaryButton(stringResource(R.string.common_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Outcome(icon: Int, done: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (done) ZapioColors.Connected else ZapioColors.Danger,
            modifier = Modifier.size(24.dp),
        )
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun DeleteDialog(file: TvFile, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ZapioDialog(
        title = stringResource(R.string.files_delete_title, file.name),
        confirmText = stringResource(R.string.files_delete),
        onConfirm = onDelete,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Text(
            stringResource(if (file.isFolder) R.string.files_delete_folder_text else R.string.files_delete_file_text),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Asks for a file or folder name; only names the TV can take are let through. */
@Composable
fun NameDialog(title: String, initial: String, confirmText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val submit = {
        val name = text.trim()
        if (TvPaths.isValidName(name)) onConfirm(name) else showError = true
    }
    ZapioDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = submit,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    showError = false
                },
                label = { Text(stringResource(R.string.files_name_label)) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.files_name_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = zapioTextFieldColors(),
            )
        }
    }
}
