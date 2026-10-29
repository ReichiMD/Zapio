package io.github.reichimd.zapio.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.reichimd.zapio.R
import io.github.reichimd.zapio.ui.theme.ZapioColors

/** Round 44 dp button with a line border, as in the top bar. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, ZapioColors.Line),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = ZapioColors.Surface,
            contentColor = ZapioColors.Text,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** Large rounded card on the surface color. */
@Composable
fun ZapioCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ZapioColors.Surface,
        border = BorderStroke(1.dp, ZapioColors.Line),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Small uppercase heading above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = ZapioColors.TextMuted,
    )
}

/** "zapio." word mark with the accent dot. */
@Composable
fun WordMark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.app_name).lowercase())
            withStyle(SpanStyle(color = ZapioColors.Accent)) { append(".") }
        },
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** Top bar: optional leading button, title or word mark, optional trailing button. */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Row(Modifier.weight(1f)) { title() }
        trailing?.invoke()
    }
}

/** Icon on a rounded tile, used for TVs and list entries. */
@Composable
fun IconTile(@DrawableRes icon: Int, tint: Color = ZapioColors.Accent, size: Int = 56) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = RoundedCornerShape((size * 0.3f).dp),
        color = ZapioColors.Surface2,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

/** Main action: amber pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ZapioColors.Accent,
            contentColor = ZapioColors.OnAccent,
            disabledContainerColor = ZapioColors.Surface2,
            disabledContentColor = ZapioColors.TextMuted,
        ),
    ) { ButtonContent(text, icon) }
}

/** Second action: outlined pill; [danger] colors it for disconnecting or deleting. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 22.dp),
        border = BorderStroke(1.dp, if (danger) ZapioColors.Danger else ZapioColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ZapioColors.Surface,
            contentColor = if (danger) ZapioColors.Danger else ZapioColors.Text,
        ),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small colored dot with a status word next to it. */
@Composable
fun StatusLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Dialog in the app's colors. Without [dismissText] there is only one button. */
@Composable
fun ZapioDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZapioColors.Surface,
        titleContentColor = ZapioColors.Text,
        textContentColor = ZapioColors.TextMuted,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = ZapioColors.Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, color = ZapioColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

@Composable
fun BackButton(onBack: () -> Unit) {
    RoundIconButton(
        icon = R.drawable.ic_arrow_back,
        contentDescription = stringResource(R.string.common_back),
        onClick = onBack,
    )
}

/** Keeps the phone screen on while [active], so a long copy or recording is not cut short. */
@Composable
fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

/** Colors for text fields in dialogs. */
@Composable
fun zapioTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ZapioColors.Text,
    unfocusedTextColor = ZapioColors.Text,
    focusedBorderColor = ZapioColors.Accent,
    unfocusedBorderColor = ZapioColors.Line,
    focusedLabelColor = ZapioColors.Accent,
    unfocusedLabelColor = ZapioColors.TextMuted,
    cursorColor = ZapioColors.Accent,
    errorBorderColor = ZapioColors.Danger,
    errorLabelColor = ZapioColors.Danger,
    errorSupportingTextColor = ZapioColors.Danger,
    errorTextColor = ZapioColors.Text,
)
