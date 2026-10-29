package io.github.reichimd.zapio.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.reichimd.zapio.BuildConfig
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.ui.common.IconTile
import io.github.reichimd.zapio.ui.common.RoundIconButton
import io.github.reichimd.zapio.ui.common.SectionLabel
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.WordMark
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Version, own license and the licenses of everything the app ships with. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicense: (ThirdPartyNotice) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TopBar(leading = { BackButton(onBack) }) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item { AppCard() }
        item {
            Column(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(stringResource(R.string.about_licenses_title))
                Text(
                    stringResource(R.string.about_licenses_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZapioColors.TextMuted,
                )
            }
        }
        items(ThirdPartyNotice.entries) { notice -> NoticeRow(notice, onClick = { onLicense(notice) }) }
    }
}

@Composable
private fun AppCard() {
    ZapioCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(icon = R.drawable.ic_bolt, size = 64)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WordMark()
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZapioColors.TextMuted,
                )
            }
        }
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_app_license), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.about_source, stringResource(R.string.source_url)),
            style = MaterialTheme.typography.bodyMedium,
            color = ZapioColors.TextMuted,
        )
    }
}

@Composable
private fun NoticeRow(notice: ThirdPartyNotice, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(notice.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_notice_line, stringResource(notice.author), stringResource(notice.licenseName)),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZapioColors.TextMuted,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.about_show_license),
                tint = ZapioColors.Accent,
            )
        }
    }
}

/** Full license text of one entry. */
@Composable
fun LicenseScreen(notice: ThirdPartyNotice, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(notice) {
        context.resources.openRawResource(notice.licenseText).bufferedReader().use { it.readText() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(leading = { BackButton(onBack) }) {
            Column {
                Text(stringResource(notice.title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(notice.licenseName),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZapioColors.TextMuted,
                )
            }
        }
        ZapioCard(modifier = Modifier.weight(1f)) {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = ZapioColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    RoundIconButton(
        icon = R.drawable.ic_arrow_back,
        contentDescription = stringResource(R.string.about_back),
        onClick = onBack,
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background) { AboutScreen(onBack = {}, onLicense = {}) }
    }
}
