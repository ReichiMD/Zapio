package io.github.reichimd.zapio.ui.device

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.adb.TvAddress
import io.github.reichimd.zapio.data.SavedTv
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.IconTile
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.SectionLabel
import io.github.reichimd.zapio.ui.common.StatusLine
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Tools for the connected TV. More tiles join in later stages. */
@Composable
fun DeviceScreen(
    tv: SavedTv,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onRemote: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(leading = { BackButton(onBack) }) {
            Text(tv.title, style = MaterialTheme.typography.titleLarge)
        }
        ZapioCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(icon = R.drawable.ic_tv, size = 64)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tv.title, style = MaterialTheme.typography.titleLarge)
                    Text(tv.address.toString(), style = MaterialTheme.typography.bodyMedium, color = ZapioColors.TextMuted)
                    StatusLine(stringResource(R.string.devices_status_connected), ZapioColors.Connected)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.device_tools))
            ToolTile(
                icon = R.drawable.ic_remote,
                title = stringResource(R.string.remote_title),
                text = stringResource(R.string.remote_text),
                onClick = onRemote,
            )
            ToolTile(
                icon = R.drawable.ic_install,
                title = stringResource(R.string.device_install),
                text = stringResource(R.string.device_install_text),
                onClick = onInstall,
            )
            Text(
                stringResource(R.string.device_more_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = ZapioColors.TextMuted,
            )
        }
        SecondaryButton(
            text = stringResource(R.string.device_disconnect),
            onClick = onDisconnect,
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.device_adb_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = ZapioColors.TextMuted,
        )
    }
}

@Composable
private fun ToolTile(@DrawableRes icon: Int, title: String, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(icon = icon, size = 52)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = ZapioColors.TextMuted)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = ZapioColors.Accent)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DeviceScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            DeviceScreen(SavedTv(TvAddress("192.0.2.20"), null, 0), onBack = {}, onInstall = {}, onRemote = {}, onDisconnect = {})
        }
    }
}
