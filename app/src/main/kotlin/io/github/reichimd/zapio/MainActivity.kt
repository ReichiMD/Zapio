package io.github.reichimd.zapio

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.reichimd.zapio.data.AppLanguage
import io.github.reichimd.zapio.tv.ConnectionState
import io.github.reichimd.zapio.ui.about.AboutScreen
import io.github.reichimd.zapio.ui.about.LicenseScreen
import io.github.reichimd.zapio.ui.about.ThirdPartyNotice
import io.github.reichimd.zapio.ui.device.DeviceScreen
import io.github.reichimd.zapio.ui.devices.DevicesScreen
import io.github.reichimd.zapio.ui.install.InstallScreen
import io.github.reichimd.zapio.ui.remote.RemoteScreen
import io.github.reichimd.zapio.ui.settings.SettingsScreen
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

class MainActivity : ComponentActivity() {

    // The language chosen in the app's settings wins over the phone's language.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(ZapioColors.Background.toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val app = application as ZapioApplication
        setContent {
            ZapioTheme {
                // Surface sets the text color; without it text outside a card is black.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZapioColors.Background,
                    contentColor = ZapioColors.Text,
                ) {
                    Box(Modifier.safeDrawingPadding()) {
                        ZapioApp(
                            app = app,
                            language = AppLanguage.current(this@MainActivity),
                            onLanguage = { language ->
                                AppLanguage.set(this@MainActivity, language)
                                recreate()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Screens are kept as a simple stack of names; enough for a handful of screens. */
private const val DEVICES = "devices"
private const val DEVICE = "device"
private const val INSTALL = "install"
private const val REMOTE = "remote"
private const val SETTINGS = "settings"
private const val ABOUT = "about"
private const val LICENSE_PREFIX = "license:"

/** Screens that need a connected TV. */
private val NEEDS_TV = setOf(DEVICE, INSTALL, REMOTE)

@Composable
private fun ZapioApp(app: ZapioApplication, language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(DEVICES)) }
    val current = stack.last()
    val back = { if (stack.size > 1) stack = stack.dropLast(1) }
    val open = { screen: String -> stack = stack + screen }
    val state by app.tvs.state.collectAsState()
    val savedTvs by app.devices.devices.collectAsState()

    BackHandler(enabled = stack.size > 1, onBack = back)

    // Without a TV the tool screens make no sense; go back to the list.
    LaunchedEffect(state, current) {
        if (state !is ConnectionState.Connected && current in NEEDS_TV) stack = stack.filterNot { it in NEEDS_TV }
    }

    when {
        current == DEVICES -> DevicesScreen(
            tvs = app.tvs,
            savedTvs = savedTvs,
            onOpenDevice = { open(DEVICE) },
            onOpenRemote = { open(REMOTE) },
            onSettings = { open(SETTINGS) },
            onAbout = { open(ABOUT) },
        )
        current == DEVICE -> (state as? ConnectionState.Connected)?.let { connected ->
            DeviceScreen(
                tv = connected.tv,
                onBack = back,
                onInstall = { open(INSTALL) },
                onRemote = { open(REMOTE) },
                onDisconnect = app.tvs::disconnect,
            )
        }
        current == INSTALL -> InstallScreen(tvs = app.tvs, onBack = back)
        current == REMOTE -> (state as? ConnectionState.Connected)?.let { connected ->
            RemoteScreen(title = connected.tv.title, tvs = app.tvs, onBack = back)
        }
        current == SETTINGS -> SettingsScreen(
            language = language,
            onLanguage = onLanguage,
            onNewKey = app.tvs::newKey,
            onBack = back,
        )
        current == ABOUT -> AboutScreen(
            onBack = back,
            onLicense = { notice -> open(LICENSE_PREFIX + notice.id) },
        )
        current.startsWith(LICENSE_PREFIX) -> {
            val notice = ThirdPartyNotice.byId(current.removePrefix(LICENSE_PREFIX))
            if (notice != null) {
                LicenseScreen(notice = notice, onBack = back)
            } else {
                LaunchedEffect(current) { back() }
            }
        }
    }
}
