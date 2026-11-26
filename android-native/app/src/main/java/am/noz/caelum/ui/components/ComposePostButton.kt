package am.noz.caelum.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor

@Composable
internal fun ComposePostButton(
    disableHaptics: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val haptics = rememberHaptics(disableHaptics)
    FloatingActionButton(
        onClick = {
            haptics.tap()
            onClick()
        },
        modifier = modifier.padding(end = 8.dp).size(56.dp),
        containerColor = palette.primary500.toComposeColor(),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
    ) {
        Icon(ShellIcons.Compose, contentDescription = tr("New post", context = "action"), modifier = Modifier.size(24.dp))
    }
}
