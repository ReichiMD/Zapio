package io.github.reichimd.zapio.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.ui.common.IconTile
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.SectionLabel
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.WordMark
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Start screen. Without saved TVs it explains how to get the TV ready. */
@Composable
fun DevicesScreen(onAbout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(
            trailing = {
                RoundIconButton(
                    icon = R.drawable.ic_info,
                    contentDescription = stringResource(R.string.devices_about),
                    onClick = onAbout,
                )
            },
        ) { WordMark() }

        EmptyDevicesCard()
        PrepareTvCard()
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

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DevicesScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background) { DevicesScreen(onAbout = {}) }
    }
}
