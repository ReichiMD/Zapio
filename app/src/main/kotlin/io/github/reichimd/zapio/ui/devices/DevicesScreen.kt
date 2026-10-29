package io.github.reichimd.zapio.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.PortState
import io.github.reichimd.zapio.adb.TvAddress
import io.github.reichimd.zapio.data.SavedTv
import io.github.reichimd.zapio.tv.ConnectionState
import io.github.reichimd.zapio.tv.ScanState
import io.github.reichimd.zapio.tv.TvManager
import io.github.reichimd.zapio.ui.common.IconTile
import io.github.reichimd.zapio.ui.common.PrimaryButton
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.SectionLabel
import io.github.reichimd.zapio.ui.common.StatusLine
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.WordMark
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Start screen: the connected TV, saved TVs, searching the Wi-Fi and adding a TV by address. */
@Composable
fun DevicesScreen(
    tvs: TvManager,
    savedTvs: List<SavedTv>,
    onOpenDevice: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val state by tvs.state.collectAsState()
    val scan by tvs.scan.collectAsState()
    val ports by tvs.ports.collectAsState()
    var askIp by rememberSaveable { mutableStateOf(false) }
    var forgetKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Open the TV's tools once a connection started here is up.
    var openWhenConnected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { tvs.checkSavedTvs() }
    LaunchedEffect(state) {
        if (state is ConnectionState.Connected && openWhenConnected) {
            openWhenConnected = false
            onOpenDevice()
        }
        if (state is ConnectionState.Failed) openWhenConnected = false
    }

    val connect = { address: TvAddress ->
        openWhenConnected = true
        tvs.connect(address)
    }

    DevicesContent(
        state = state,
        savedTvs = savedTvs,
        ports = ports,
        scan = scan,
        onConnect = connect,
        onOpenDevice = onOpenDevice,
        onForget = { forgetKey = it.key },
        onScan = tvs::startScan,
        onClearScan = tvs::clearScan,
        onEnterIp = { askIp = true },
        onSettings = onSettings,
        onAbout = onAbout,
    )

    if (askIp) {
        EnterIpDialog(
            onConnect = { address ->
                askIp = false
                connect(address)
            },
            onDismiss = { askIp = false },
        )
    }
    savedTvs.firstOrNull { it.key == forgetKey }?.let { tv ->
        ForgetDialog(
            tv = tv,
            onForget = {
                forgetKey = null
                tvs.forget(tv)
            },
            onDismiss = { forgetKey = null },
        )
    }
    when (val current = state) {
        is ConnectionState.Connecting -> ConnectingDialog(
            name = savedTvs.firstOrNull { it.address == current.address }?.title ?: current.address.host,
            onCancel = tvs::cancelConnect,
        )
        is ConnectionState.Failed -> ConnectFailedDialog(
            problem = current.problem,
            onRetry = { connect(current.address) },
            onDismiss = tvs::dismissFailure,
        )
        else -> Unit
    }
}

@Composable
private fun DevicesContent(
    state: ConnectionState,
    savedTvs: List<SavedTv>,
    ports: Map<String, PortState>,
    scan: ScanState,
    onConnect: (TvAddress) -> Unit,
    onOpenDevice: () -> Unit,
    onForget: (SavedTv) -> Unit,
    onScan: () -> Unit,
    onClearScan: () -> Unit,
    onEnterIp: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val connected = (state as? ConnectionState.Connected)?.tv
    val others = savedTvs.filter { it.key != connected?.key }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(
                        icon = R.drawable.ic_settings,
                        contentDescription = stringResource(R.string.settings_title),
                        onClick = onSettings,
                    )
                    RoundIconButton(
                        icon = R.drawable.ic_info,
                        contentDescription = stringResource(R.string.devices_about),
                        onClick = onAbout,
                    )
                }
            },
        ) { WordMark() }

        if (connected != null) ActiveTvCard(connected, onOpenDevice)
        if (connected == null && savedTvs.isEmpty()) EmptyDevicesCard()

        if (others.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.devices_saved_title))
                others.forEach { tv ->
                    TvRow(
                        title = tv.title,
                        subtitle = tv.address.toString(),
                        status = { PortStatus(ports[tv.key]) },
                        onClick = { onConnect(tv.address) },
                        onForget = { onForget(tv) },
                    )
                }
            }
        }

        ScanSection(scan, savedTvs, onConnect, onScan, onClearScan, onEnterIp)

        if (savedTvs.isEmpty()) PrepareTvCard()
    }
}

@Composable
private fun ActiveTvCard(tv: SavedTv, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.devices_active_title))
        ZapioCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(icon = R.drawable.ic_tv, size = 64)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tv.title, style = MaterialTheme.typography.titleLarge)
                    StatusLine(stringResource(R.string.devices_status_connected), ZapioColors.Connected)
                }
            }
            PrimaryButton(
                text = stringResource(R.string.devices_all_tools),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScanSection(
    scan: ScanState,
    savedTvs: List<SavedTv>,
    onConnect: (TvAddress) -> Unit,
    onScan: () -> Unit,
    onClearScan: () -> Unit,
    onEnterIp: () -> Unit,
) {
    val newOnes = scan.found.filter { address -> savedTvs.none { it.address == address } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (scan.running || scan.finished) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.devices_scan_found_title), Modifier.weight(1f))
                if (scan.finished) {
                    IconButton(onClick = onClearScan) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.common_close),
                            tint = ZapioColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            newOnes.forEach { address ->
                TvRow(
                    title = address.host,
                    subtitle = stringResource(R.string.devices_scan_new),
                    onClick = { onConnect(address) },
                )
            }
            when {
                scan.running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = ZapioColors.Accent, strokeWidth = 2.dp)
                    Text(stringResource(R.string.devices_scan_running), color = ZapioColors.TextMuted)
                }
                scan.noWifi -> HintText(stringResource(R.string.devices_scan_no_wifi))
                scan.found.isEmpty() -> HintText(stringResource(R.string.devices_scan_none))
                newOnes.isEmpty() -> HintText(stringResource(R.string.devices_scan_only_known))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = stringResource(R.string.devices_scan),
                onClick = onScan,
                icon = R.drawable.ic_wifi,
                enabled = !scan.running,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.devices_enter_ip),
                onClick = onEnterIp,
                icon = R.drawable.ic_plus,
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ZapioColors.TextMuted)
}

@Composable
private fun PortStatus(port: PortState?) {
    val (text, color) = when (port) {
        null -> R.string.devices_status_checking to ZapioColors.TextMuted
        PortState.OPEN -> R.string.devices_status_ready to ZapioColors.Connected
        PortState.CLOSED -> R.string.devices_status_adb_off to ZapioColors.Danger
        PortState.NO_ANSWER -> R.string.devices_status_offline to ZapioColors.TextMuted
    }
    StatusLine(stringResource(text), color)
}

@Composable
private fun TvRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    status: (@Composable () -> Unit)? = null,
    onForget: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(icon = R.drawable.ic_tv, size = 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ZapioColors.TextMuted)
                status?.invoke()
            }
            if (onForget != null) {
                IconButton(onClick = onForget) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.devices_forget, title),
                        tint = ZapioColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = ZapioColors.Accent,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyDevicesCard() {
    ZapioCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(icon = R.drawable.ic_tv, size = 64)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.devices_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.devices_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZapioColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun PrepareTvCard() {
    val steps = listOf(
        R.string.devices_prepare_step_1,
        R.string.devices_prepare_step_2,
        R.string.devices_prepare_step_3,
        R.string.devices_prepare_step_4,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.devices_prepare_title))
        ZapioCard {
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepNumber(index + 1)
                    Text(stringResource(step), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Text(
                stringResource(R.string.devices_prepare_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = ZapioColors.TextMuted,
            )
        }
    }
}

@Composable
private fun StepNumber(number: Int) {
    Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = ZapioColors.Accent) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ZapioColors.OnAccent,
            )
        }
    }
}

/** Addresses in previews are from 192.0.2.0/24, the range reserved for documentation. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DevicesScreenPreview() {
    val living = SavedTv(TvAddress("192.0.2.20"), null, 0)
    val kitchen = SavedTv(TvAddress("192.0.2.21"), null, 0)
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            DevicesContent(
                state = ConnectionState.Connected(living),
                savedTvs = listOf(living, kitchen),
                ports = mapOf(kitchen.key to PortState.CLOSED),
                scan = ScanState(finished = true, found = listOf(TvAddress("192.0.2.30"))),
                onConnect = {}, onOpenDevice = {}, onForget = {}, onScan = {}, onClearScan = {},
                onEnterIp = {}, onSettings = {}, onAbout = {},
            )
        }
    }
}
