package io.github.reichimd.zapio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.data.AppLanguage
import io.github.reichimd.zapio.ui.common.BackButton
import io.github.reichimd.zapio.ui.common.SecondaryButton
import io.github.reichimd.zapio.ui.common.SectionLabel
import io.github.reichimd.zapio.ui.common.TopBar
import io.github.reichimd.zapio.ui.common.ZapioCard
import io.github.reichimd.zapio.ui.common.ZapioDialog
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

/** Language of the app, the phone folder for files from the TV, and the key the app shows to TVs. */
@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onNewKey: () -> Unit,
    onBack: () -> Unit,
    folderCard: @Composable () -> Unit = {},
) {
    var confirmNewKey by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(leading = { BackButton(onBack) }) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.settings_language))
            ZapioCard {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        LanguageRow(
                            text = stringResource(languageName(option)),
                            selected = option == language,
                            onClick = { if (option != language) onLanguage(option) },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.folder_title))
            folderCard()
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.settings_key_title))
            ZapioCard {
                Text(stringResource(R.string.settings_key_text), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(
                    text = stringResource(R.string.settings_key_new),
                    onClick = { confirmNewKey = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmNewKey) {
        ZapioDialog(
            title = stringResource(R.string.settings_key_confirm_title),
            confirmText = stringResource(R.string.settings_key_new),
            onConfirm = {
                confirmNewKey = false
                onNewKey()
            },
            onDismiss = { confirmNewKey = false },
            dismissText = stringResource(R.string.common_cancel),
        ) {
            Text(stringResource(R.string.settings_key_confirm_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LanguageRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = ZapioColors.Accent,
                unselectedColor = ZapioColors.TextMuted,
            ),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
    }
}

private fun languageName(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.GERMAN -> R.string.language_german
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenPreview() {
    ZapioTheme {
        Surface(color = ZapioColors.Background, contentColor = ZapioColors.Text) {
            SettingsScreen(AppLanguage.SYSTEM, onLanguage = {}, onNewKey = {}, onBack = {})
        }
    }
}
