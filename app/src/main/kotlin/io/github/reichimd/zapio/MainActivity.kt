package io.github.reichimd.zapio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.reichimd.zapio.ui.about.AboutScreen
import io.github.reichimd.zapio.ui.about.LicenseScreen
import io.github.reichimd.zapio.ui.about.ThirdPartyNotice
import io.github.reichimd.zapio.ui.devices.DevicesScreen
import io.github.reichimd.zapio.ui.theme.ZapioColors
import io.github.reichimd.zapio.ui.theme.ZapioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(ZapioColors.Background.toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            ZapioTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ZapioColors.Background)
                        .safeDrawingPadding(),
                ) {
                    ZapioApp()
                }
            }
        }
    }
}

/** Screens are kept as a simple stack of names; enough for a handful of screens. */
private const val DEVICES = "devices"
private const val ABOUT = "about"
private const val LICENSE_PREFIX = "license:"

@Composable
private fun ZapioApp() {
    var stack by rememberSaveable { mutableStateOf(listOf(DEVICES)) }
    val current = stack.last()
    val back = { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1, onBack = back)

    when {
        current == DEVICES -> DevicesScreen(onAbout = { stack = stack + ABOUT })
        current == ABOUT -> AboutScreen(
            onBack = back,
            onLicense = { notice -> stack = stack + (LICENSE_PREFIX + notice.id) },
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
