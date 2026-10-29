package io.github.reichimd.zapio.ui.remote

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvKey
import io.github.reichimd.zapio.tv.RemoteStatus
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.StatusLine
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Longest text sent at once; the TV types it key by key. */
private const val MAX_TEXT = 300

/** Holding a key: first repeat after this, then every [REPEAT_MS]. */
private const val REPEAT_START_MS = 400L
private const val REPEAT_MS = 110L

/** Remote control for the connected TV. Talks to the TV only while the screen is visible. */
@Composable
fun RemoteScreen(title: String, tvs: TvManager, onBack: () -> Unit) {
    val remote = tvs.remote
    val status by remote.status.collectAsState()
    LifecycleStartEffect(Unit) {
        tvs.startRemote()
        onStopOrDispose { tvs.stopRemote() }
    }
    RemoteContent(
        title = title,
        status = status,
        onPress = remote::press,
        onRepeat = remote::repeat,
        onType = remote::type,
        onBack = onBack,
    )
}

@Composable
private fun RemoteContent(
    title: String,
    status: RemoteStatus,
    onPress: (TvKey) -> Unit,
    onRepeat: (TvKey) -> Unit,
    onType: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(
            leading = { BackButton(onBack) },
            trailing = {
                RemoteKey(
                    icon = R.drawable.ic_power,
                    label = stringResource(R.string.remote_power),
                    onPress = { onPress(TvKey.POWER) },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = ZapioColors.Danger,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                RemoteStatusLine(status)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(36.dp),
            color = ZapioColors.Surface,
            border = BorderStroke(1.dp, ZapioColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DirectionPad(onPress, onRepeat)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RemoteKey(R.drawable.ic_tv_back, stringResource(R.string.remote_back), { onPress(TvKey.BACK) })
                    RemoteKey(R.drawable.ic_home, stringResource(R.string.remote_home), { onPress(TvKey.HOME) })
                    RemoteKey(R.drawable.ic_menu, stringResource(R.string.remote_menu), { onPress(TvKey.MENU) })
                    RemoteKey(
                        R.drawable.ic_play_pause,
                        stringResource(R.string.remote_play_pause),
                        { onPress(TvKey.MEDIA_PLAY_PAUSE) },
                    )
                }
                VolumeRow(onPress, onRepeat)
            }
        }
        TextBar(enabled = status == RemoteStatus.READY, onType = onType)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.remote_delete),
                onClick = { onPress(TvKey.DEL) },
                icon = R.drawable.ic_backspace,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.remote_enter),
                onClick = { onPress(TvKey.ENTER) },
                icon = R.drawable.ic_enter,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.remote_text_note),
            style = MaterialTheme.typography.bodyMedium,
            color = ZapioColors.TextMuted,
        )
    }
}

@Composable
private fun RemoteStatusLine(status: RemoteStatus) {
    val (text, color) = when (status) {
        RemoteStatus.STARTING -> R.string.remote_status_starting to ZapioColors.TextMuted
        RemoteStatus.READY -> R.string.devices_status_connected to ZapioColors.Connected
        RemoteStatus.RECONNECTING -> R.string.remote_status_reconnecting to ZapioColors.Danger
    }
    StatusLine(stringResource(text), color)
}

/** Round pad with four arrows and the amber OK in the middle. */
@Composable
private fun DirectionPad(onPress: (TvKey) -> Unit, onRepeat: (TvKey) -> Unit) {
    Box(
        modifier = Modifier
            .size(236.dp)
            .border(10.dp, ZapioColors.Background.copy(alpha = 0.35f), CircleShape),
    ) {
        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = ZapioColors.Surface2) {}
        val arrows = listOf(
            Triple(TvKey.DPAD_UP, R.drawable.ic_chevron_up, Alignment.TopCenter) to R.string.remote_up,
            Triple(TvKey.DPAD_DOWN, R.drawable.ic_chevron_down, Alignment.BottomCenter) to R.string.remote_down,
            Triple(TvKey.DPAD_LEFT, R.drawable.ic_chevron_left, Alignment.CenterStart) to R.string.remote_left,
            Triple(TvKey.DPAD_RIGHT, R.drawable.ic_chevron_right, Alignment.CenterEnd) to R.string.remote_right,
        )
        arrows.forEach { (arrow, label) ->
            val (key, icon, alignment) = arrow
            Box(Modifier.align(alignment).padding(12.dp)) {
                RemoteKey(
                    icon = icon,
                    label = stringResource(label),
                    onPress = { onPress(key) },
                    onRepeat = { onRepeat(key) },
                    size = 60.dp,
                    iconSize = 26.dp,
                    color = Color.Transparent,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .border(6.dp, ZapioColors.Background, CircleShape)
                .padding(6.dp),
        ) {
            RemoteKey(
                icon = null,
                label = stringResource(R.string.remote_ok),
                onPress = { onPress(TvKey.DPAD_CENTER) },
                size = 84.dp,
                color = ZapioColors.Accent,
                contentColor = ZapioColors.OnAccent,
            ) {
                Text(stringResource(R.string.remote_ok), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VolumeRow(onPress: (TvKey) -> Unit, onRepeat: (TvKey) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(Modifier.weight(1f).height(56.dp), shape = CircleShape, color = ZapioColors.Surface2) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RemoteKey(
                    icon = R.drawable.ic_minus,
                    label = stringResource(R.string.remote_volume_down),
                    onPress = { onPress(TvKey.VOLUME_DOWN) },
                    onRepeat = { onRepeat(TvKey.VOLUME_DOWN) },
                    size = 48.dp,
                )
                Text(
                    stringResource(R.string.remote_volume),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZapioColors.TextMuted,
                )
                RemoteKey(
                    icon = R.drawable.ic_plus,
                    label = stringResource(R.string.remote_volume_up),
                    onPress = { onPress(TvKey.VOLUME_UP) },
                    onRepeat = { onRepeat(TvKey.VOLUME_UP) },
                    size = 48.dp,
                )
            }
        }
        RemoteKey(R.drawable.ic_mute, stringResource(R.string.remote_mute), { onPress(TvKey.VOLUME_MUTE) }, size = 56.dp)
    }
}

/**
 * A remote key. With [onRepeat] holding it repeats, like a real remote. Screen readers get a normal button.
 */
@Composable
private fun RemoteKey(
    @DrawableRes icon: Int?,
    label: String,
    onPress: () -> Unit,
    onRepeat: (() -> Unit)? = null,
    size: Dp = 60.dp,
    iconSize: Dp = 22.dp,
    color: Color = ZapioColors.Surface2,
    contentColor: Color = ZapioColors.Text,
    content: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var down by remember { mutableStateOf(false) }
    // The gesture keeps running across recompositions; it always calls the latest actions.
    val press by rememberUpdatedState(onPress)
    val hold by rememberUpdatedState(onRepeat)
    Surface(
        modifier = Modifier
            .size(size)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    press()
                    true
                }
            }
            .pointerInput(Unit) {
                // Fires on lifting the finger, so starting to scroll on a key sends nothing.
                // Held keys repeat; a hold that turns into scrolling stops the repeat at once.
                var repeated = false
                detectTapGestures(
                    onPress = {
                        down = true
                        repeated = false
                        val repeating = hold?.let {
                            scope.launch {
                                delay(REPEAT_START_MS)
                                repeated = true
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                press()
                                while (true) {
                                    delay(REPEAT_MS)
                                    hold?.invoke()
                                }
                            }
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            repeating?.cancel()
                            down = false
                        }
                    },
                    onTap = {
                        if (!repeated) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            press()
                        }
                    },
                )
            },
        shape = CircleShape,
        color = if (down) ZapioColors.Line else color,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                content != null -> content()
                icon != null -> Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
private fun TextBar(enabled: Boolean, onType: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val label = stringResource(R.string.remote_text_label)
    // Without a connection the text stays in the field instead of getting lost.
    val send = {
        if (enabled && text.isNotBlank()) {
            onType(text)
            text = ""
        }
    }
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
            Icon(
                painterResource(R.drawable.ic_keyboard),
                contentDescription = null,
                tint = ZapioColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TEXT) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ZapioColors.Text),
                cursorBrush = SolidColor(ZapioColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.remote_text_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ZapioColors.TextMuted,
                            )
                        }
                        field()
                    }
                },
            )
            Surface(
                onClick = send,
                enabled = enabled,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (enabled) ZapioColors.Accent else ZapioColors.Surface2,
                contentColor = if (enabled) ZapioColors.OnAccent else ZapioColors.TextMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.remote_text_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RemotePreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            RemoteContent(
                title = "192.0.2.20",
                status = RemoteStatus.READY,
                onPress = {},
                onRepeat = {},
                onType = {},
                onBack = {},
            )
        }
    }
}
