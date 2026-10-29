package io.github.reichimd.zapio.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
