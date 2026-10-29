package io.github.reichimd.zapio.ui.install

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.InstallProblem
import io.github.reichimd.zapio.tv.InstallFailure
import io.github.reichimd.zapio.tv.InstallState
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.PrimaryButton
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** APK types file pickers use; some report APKs only as generic binary files. */
private val APK_TYPES = arrayOf("application/vnd.android.package-archive", "application/octet-stream")

/** Pick an APK on the phone, send it to the TV, show how it went. */
@Composable
fun InstallScreen(tvs: TvManager, onBack: () -> Unit) {
    val state by tvs.install.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) tvs.install(uri)
    }
    LaunchedEffect(Unit) { tvs.resetInstall() }
    InstallContent(state, onBack, onChoose = { picker.launch(APK_TYPES) })
}

@Composable
private fun InstallContent(state: InstallState, onBack: () -> Unit, onChoose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(leading = { BackButton(onBack) }) {
            Text(stringResource(R.string.device_install), style = MaterialTheme.typography.titleLarge)
        }
        ZapioCard {
            when (state) {
                InstallState.Idle -> Body(stringResource(R.string.install_intro))
                is InstallState.Sending -> Progress(state)
                is InstallState.Installing -> {
                    Title(state.fileName)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ZapioColors.Accent,
                        trackColor = ZapioColors.Surface2,
                        strokeCap = StrokeCap.Round,
                    )
                    Body(stringResource(R.string.install_installing))
                }
                is InstallState.Done -> {
                    Outcome(R.drawable.ic_check, ZapioColors.Connected, stringResource(R.string.install_done_title))
                    Body(stringResource(R.string.install_done, state.fileName))
                }
                is InstallState.Failed -> Failure(state)
            }
        }
        val busy = state is InstallState.Sending || state is InstallState.Installing
        if (!busy) {
            PrimaryButton(
                text = stringResource(if (state is InstallState.Idle) R.string.install_choose else R.string.install_another),
                onClick = onChoose,
                icon = R.drawable.ic_install,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Body(stringResource(R.string.install_keep_open))
        }
    }
}

@Composable
private fun Progress(state: InstallState.Sending) {
    val context = LocalContext.current
    Title(state.fileName)
    LinearProgressIndicator(
        progress = { if (state.totalBytes > 0) state.sentBytes.toFloat() / state.totalBytes else 0f },
        modifier = Modifier.fillMaxWidth(),
        color = ZapioColors.Accent,
        trackColor = ZapioColors.Surface2,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    Body(
        stringResource(
            R.string.install_progress,
            Formatter.formatShortFileSize(context, state.sentBytes),
            Formatter.formatShortFileSize(context, state.totalBytes),
        ),
    )
}

@Composable
private fun Failure(state: InstallState.Failed) {
    Outcome(R.drawable.ic_alert, ZapioColors.Danger, stringResource(R.string.install_failed))
    state.fileName?.let { Body(it) }
    when (val reason = state.reason) {
        InstallFailure.FileUnreadable -> Body(stringResource(R.string.install_file_unreadable))
        is InstallFailure.Connection -> Body(stringResource(problemText(reason.problem)))
        is InstallFailure.Rejected -> {
            Body(stringResource(installProblemText(reason.problem)))
            if (reason.tvText.isNotBlank()) {
                Text(
                    stringResource(R.string.install_tv_says, reason.tvText),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZapioColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Outcome(@DrawableRes icon: Int, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

private fun installProblemText(problem: InstallProblem): Int = when (problem) {
    InstallProblem.NOT_AN_APK -> R.string.install_problem_not_an_apk
    InstallProblem.NO_SPACE -> R.string.install_problem_no_space
    InstallProblem.SIGNATURE_MISMATCH -> R.string.install_problem_signature
    InstallProblem.OLDER_VERSION -> R.string.install_problem_older_version
    InstallProblem.TV_TOO_OLD -> R.string.install_problem_tv_too_old
    InstallProblem.APP_TOO_OLD -> R.string.install_problem_app_too_old
    InstallProblem.WRONG_PROCESSOR -> R.string.install_problem_wrong_processor
    InstallProblem.BLOCKED -> R.string.install_problem_blocked
    InstallProblem.OTHER -> R.string.install_problem_other
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InstallScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            InstallContent(InstallState.Sending("app.apk", 12_000_000, 40_000_000), onBack = {}, onChoose = {})
        }
    }
}
