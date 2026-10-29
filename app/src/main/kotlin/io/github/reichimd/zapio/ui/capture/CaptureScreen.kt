package io.github.reichimd.zapio.ui.capture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.data.TargetFolder
import io.github.reichimd.zapio.tv.CaptureFailure
import io.github.reichimd.zapio.tv.CaptureKind
import io.github.reichimd.zapio.tv.CaptureState
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.KeepScreenOn
import io.github.reichimd.zapio.ui.common.PrimaryButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.TargetFolderCard
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.common.rememberFolderPicker
import io.github.reichimd.zapio.ui.devices.problemText
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Screenshot and video of the TV screen, saved in the phone folder. */
@Composable
fun CaptureScreen(tvs: TvManager, targetFolder: TargetFolder, onBack: () -> Unit) {
    val capture = tvs.capture
    val state by capture.state.collectAsState()
    val context = LocalContext.current
    var noApp by remember { mutableStateOf(false) }
    // What to start once a folder is chosen.
    var waiting by rememberSaveable { mutableStateOf<CaptureKind?>(null) }
    val chooseFolder = rememberFolderPicker(targetFolder) {
        when (waiting) {
            CaptureKind.SCREENSHOT -> capture.screenshot()
            CaptureKind.VIDEO -> capture.startRecording()
            null -> Unit
        }
    }
    val start = { kind: CaptureKind ->
        noApp = false
        if (targetFolder.folder.value == null) {
            waiting = kind
            chooseFolder()
        } else if (kind == CaptureKind.SCREENSHOT) {
            capture.screenshot()
        } else {
            capture.startRecording()
        }
    }
    LaunchedEffect(Unit) { targetFolder.refresh() }
    val busy = state is CaptureState.Preparing || state is CaptureState.Recording || state is CaptureState.Saving
    KeepScreenOn(busy)

    CaptureContent(
        state = state,
        noApp = noApp,
        onBack = onBack,
        folderCard = {
            TargetFolderCard(targetFolder) {
                waiting = null
                chooseFolder()
            }
        },
        onScreenshot = { start(CaptureKind.SCREENSHOT) },
        onRecord = { start(CaptureKind.VIDEO) },
        onStop = capture::stopRecording,
        onCancelWaiting = capture::cancelWaiting,
        onOpen = { saved -> noApp = !open(context, saved) },
    )
}

@Composable
private fun CaptureContent(
    state: CaptureState,
    noApp: Boolean,
    onBack: () -> Unit,
    folderCard: @Composable () -> Unit,
    onScreenshot: () -> Unit,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onCancelWaiting: () -> Unit,
    onOpen: (CaptureState.Saved) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(leading = { BackButton(onBack) }) {
            Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge)
        }
        ZapioCard {
            when (state) {
                CaptureState.Idle -> {
                    Body(stringResource(R.string.capture_intro))
                    Muted(stringResource(R.string.capture_protected))
                }
                is CaptureState.Preparing -> Waiting(
                    stringResource(
                        if (state.kind == CaptureKind.SCREENSHOT) R.string.capture_preparing_screenshot else R.string.capture_preparing_video,
                    ),
                )
                is CaptureState.Recording -> Recording(state)
                is CaptureState.Saving -> Saving(state)
                is CaptureState.Saved -> Saved(state, noApp, onOpen)
                is CaptureState.Failed -> Failed(state)
            }
        }
        when (state) {
            is CaptureState.Recording -> SecondaryButton(
                text = stringResource(R.string.capture_stop),
                onClick = onStop,
                icon = R.drawable.ic_stop,
                danger = true,
                modifier = Modifier.fillMaxWidth(),
            )
            is CaptureState.Preparing -> if (state.waiting) {
                SecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onCancelWaiting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is CaptureState.Saving -> Unit
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.capture_screenshot),
                    onClick = onScreenshot,
                    icon = R.drawable.ic_camera,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.capture_record),
                    onClick = onRecord,
                    icon = R.drawable.ic_record,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        folderCard()
    }
}

@Composable
private fun Recording(state: CaptureState.Recording) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    val limitMs = state.limitSeconds * 1000L
    val elapsed = (now - state.startedAt).coerceIn(0, limitMs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_record), contentDescription = null, tint = ZapioColors.Danger, modifier = Modifier.size(20.dp))
        Text(
            stringResource(if (state.stopping) R.string.capture_stopping else R.string.capture_recording),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    LinearProgressIndicator(
        progress = { elapsed.toFloat() / limitMs },
        modifier = Modifier.fillMaxWidth(),
        color = ZapioColors.Danger,
        trackColor = ZapioColors.Surface2,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    val left = DateUtils.formatElapsedTime((limitMs - elapsed + 999) / 1000)
    Body(stringResource(R.string.capture_time_left, left))
}

@Composable
private fun Saving(state: CaptureState.Saving) {
    val context = LocalContext.current
    Waiting(stringResource(R.string.capture_saving))
    if (state.totalBytes > 0) {
        LinearProgressIndicator(
            progress = { (state.sentBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = ZapioColors.Accent,
            trackColor = ZapioColors.Surface2,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Muted(
            stringResource(
                R.string.transfer_bytes,
                Formatter.formatShortFileSize(context, state.sentBytes),
                Formatter.formatShortFileSize(context, state.totalBytes),
            ),
        )
    }
}

@Composable
private fun Saved(state: CaptureState.Saved, noApp: Boolean, onOpen: (CaptureState.Saved) -> Unit) {
    Outcome(
        R.drawable.ic_check,
        done = true,
        stringResource(if (state.kind == CaptureKind.SCREENSHOT) R.string.capture_saved_screenshot else R.string.capture_saved_video),
    )
    Body(stringResource(R.string.capture_saved_text, state.fileName))
    if (state.kind == CaptureKind.SCREENSHOT) {
        val context = LocalContext.current
        val preview by produceState<ImageBitmap?>(null, state.file) {
            value = withContext(Dispatchers.IO) { loadPreview(context, state.file) }
        }
        preview?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.capture_preview),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
    SecondaryButton(stringResource(R.string.capture_open), onClick = { onOpen(state) }, modifier = Modifier.fillMaxWidth())
    if (noApp) Muted(stringResource(R.string.capture_no_app))
}

@Composable
private fun Failed(state: CaptureState.Failed) {
    Outcome(R.drawable.ic_alert, done = false, stringResource(R.string.capture_failed))
    when (val reason = state.reason) {
        CaptureFailure.NoFolder -> Body(stringResource(R.string.transfer_no_folder))
        CaptureFailure.PhoneFile -> Body(stringResource(R.string.capture_phone_file))
        is CaptureFailure.Connection -> Body(stringResource(problemText(reason.problem)))
        is CaptureFailure.NotTaken -> {
            Body(stringResource(R.string.capture_not_taken))
            if (reason.tvText.isNotBlank()) Muted(stringResource(R.string.install_tv_says, reason.tvText))
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), color = ZapioColors.Accent, strokeWidth = 3.dp)
        Body(text)
    }
}

@Composable
private fun Outcome(icon: Int, done: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (done) ZapioColors.Connected else ZapioColors.Danger,
            modifier = Modifier.size(28.dp),
        )
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ZapioColors.TextMuted)
}

private const val TICK_MS = 250L

/** Longest side of the preview; the full picture stays in the file. */
private const val PREVIEW_PX = 1280

/** Shows the saved file in an app of the phone. Returns false if there is none. */
private fun open(context: Context, saved: CaptureState.Saved): Boolean {
    val type = if (saved.kind == CaptureKind.SCREENSHOT) "image/png" else "video/mp4"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(saved.file, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun loadPreview(context: Context, file: Uri): ImageBitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(file)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    resolver.openInputStream(file)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptureScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            CaptureContent(
                state = CaptureState.Recording(SystemClock.elapsedRealtime() - 42_000, 180),
                noApp = false,
                onBack = {},
                folderCard = {},
                onScreenshot = {},
                onRecord = {},
                onStop = {},
                onCancelWaiting = {},
                onOpen = {},
            )
        }
    }
}
