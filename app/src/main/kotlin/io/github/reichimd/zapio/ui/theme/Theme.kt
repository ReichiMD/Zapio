package io.github.reichimd.zapio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.reichimd.zapio.R

/** Design "Night studio": dark ground, one amber accent. */
object ZapioColors {
    val Background = Color(0xFF0F1217)
    val Surface = Color(0xFF1A1F27)
    val Surface2 = Color(0xFF252C37)
    val Line = Color(0xFF2E3642)
    val Text = Color(0xFFEEF1F5)
    val TextMuted = Color(0xFFA3ADBA)
    val Accent = Color(0xFFFFB020)
    val OnAccent = Color(0xFF1A1300)
    val Connected = Color(0xFF7EE0A6)
    val Danger = Color(0xFFFF9A9A)
}

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private val colorScheme = darkColorScheme(
    primary = ZapioColors.Accent,
    onPrimary = ZapioColors.OnAccent,
    secondary = ZapioColors.Surface2,
    onSecondary = ZapioColors.Text,
    background = ZapioColors.Background,
    onBackground = ZapioColors.Text,
    surface = ZapioColors.Surface,
    onSurface = ZapioColors.Text,
    surfaceVariant = ZapioColors.Surface2,
    onSurfaceVariant = ZapioColors.TextMuted,
    outline = ZapioColors.Line,
    error = ZapioColors.Danger,
)

private val typography = Typography().let { base ->
    fun TextStyle.grotesk() = copy(fontFamily = SpaceGrotesk)
    Typography(
        displayLarge = base.displayLarge.grotesk(),
        displayMedium = base.displayMedium.grotesk(),
        displaySmall = base.displaySmall.grotesk(),
        headlineLarge = base.headlineLarge.grotesk(),
        headlineMedium = base.headlineMedium.grotesk(),
        headlineSmall = base.headlineSmall.grotesk(),
        titleLarge = base.titleLarge.grotesk().copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.grotesk().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.grotesk(),
        bodyLarge = base.bodyLarge.grotesk(),
        bodyMedium = base.bodyMedium.grotesk(),
        bodySmall = base.bodySmall.grotesk(),
        labelLarge = base.labelLarge.grotesk().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.grotesk(),
        labelSmall = base.labelSmall.grotesk().copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
        ),
    )
}

@Composable
fun ZapioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
