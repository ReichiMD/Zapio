package io.github.reichimd.zapio.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvAddress
import io.github.reichimd.zapio.adb.TvProblem
import io.github.reichimd.zapio.data.SavedTv
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.common.zapioTextFieldColors
import io.github.reichimd.zapio.ui.theme.ZapioColors

@Composable
fun EnterIpDialog(onConnect: (TvAddress) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val submit = {
        val address = TvAddress.parse(text)
        if (address != null) onConnect(address) else showError = true
    }
    ZapioDialog(
        title = stringResource(R.string.ip_title),
        confirmText = stringResource(R.string.ip_connect),
        onConfirm = submit,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ip_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    showError = false
                },
                label = { Text(stringResource(R.string.ip_label)) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.ip_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = zapioTextFieldColors(),
            )
        }
    }
}

@Composable
fun ConnectingDialog(name: String, onCancel: () -> Unit) {
    ZapioDialog(
        title = stringResource(R.string.connect_title, name),
        confirmText = stringResource(R.string.common_cancel),
        onConfirm = onCancel,
        onDismiss = onCancel,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), color = ZapioColors.Accent, strokeWidth = 3.dp)
            Text(stringResource(R.string.connect_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConnectFailedDialog(problem: TvProblem, onRetry: () -> Unit, onDismiss: () -> Unit) {
    ZapioDialog(
        title = stringResource(R.string.connect_failed_title),
        confirmText = stringResource(R.string.connect_retry),
        onConfirm = onRetry,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_close),
    ) {
        Text(stringResource(problemText(problem)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ForgetDialog(tv: SavedTv, onForget: () -> Unit, onDismiss: () -> Unit) {
    ZapioDialog(
        title = stringResource(R.string.devices_forget_title),
        confirmText = stringResource(R.string.devices_forget_confirm),
        onConfirm = onForget,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Text(stringResource(R.string.devices_forget_text, tv.title), style = MaterialTheme.typography.bodyMedium)
    }
}

fun problemText(problem: TvProblem): Int = when (problem) {
    TvProblem.ADB_OFF -> R.string.problem_adb_off
    TvProblem.NO_ANSWER -> R.string.problem_no_answer
    TvProblem.NOT_ALLOWED -> R.string.problem_not_allowed
    TvProblem.CONNECTION_LOST -> R.string.problem_connection_lost
}
