package am.noz.caelum.ui.profile

import am.noz.caelum.ui.components.bskyVector
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ProfileActionHeight = 36.dp
private val ProfileActionTouchSize = 48.dp

@Composable
internal fun ProfileActionButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val palette = LocalAlfPalette.current
    val background = if (primary) palette.primary500.toComposeColor() else palette.contrast50.toComposeColor()
    val foreground = if (primary) Color.White else palette.contrast900.toComposeColor()
    val opacity = if (enabled) 1f else 0.5f

    ProfileActionTarget(onClick = onClick, enabled = enabled) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = ProfileActionHeight, minHeight = ProfileActionHeight)
                .background(background.copy(alpha = opacity), CircleShape)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = foreground.copy(alpha = opacity), modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground.copy(alpha = opacity),
            )
        }
    }
}

@Composable
internal fun ProfileActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    pending: Boolean = false,
) {
    val palette = LocalAlfPalette.current
    ProfileActionTarget(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier.size(ProfileActionHeight)
                .background(palette.contrast50.toComposeColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, tint = palette.contrast700.toComposeColor(), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProfileActionTarget(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = ProfileActionTouchSize, minHeight = ProfileActionTouchSize)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

internal object ProfileHeaderIcons {
    val Plus = bskyVector(
        "Follow",
        "M12 3a1 1 0 0 1 1 1v7h7a1 1 0 1 1 0 2h-7v7a1 1 0 1 1-2 0v-7H4a1 1 0 1 1 0-2h7V4a1 1 0 0 1 1-1Z",
    )
    val Message = bskyVector(
        "Message",
        "M19.55 12a7.55 7.55 0 1 0-14.33 3.329c.142.287.193.623.125.957l-.618 3.007 3.131-.585.12-.018c.277-.029.552.022.793.137h0A7.5 7.5 0 0 0 12 19.55 7.55 7.55 0 0 0 19.55 12Zm2 0a9.55 9.55 0 0 1-13.48 8.703l-3.741.7a1.476 1.476 0 0 1-1.717-1.746l.741-3.608A9.55 9.55 0 1 1 21.55 12Z",
    )
    val BellPlus = bskyVector(
        "Get notified",
        "M12 2a1 1 0 1 1 0 2 5.307 5.307 0 0 0-5.3 5.042l-.18 3.589a3 3 0 0 1-.313 1.168L5.118 16h13.764l-1.089-2.178a3 3 0 0 1-.312-1.168 1 1 0 1 1 1.996-.1q.012.209.105.398l1.227 2.454A1.81 1.81 0 0 1 19.191 18H16.9a5.002 5.002 0 0 1-9.8 0H4.809a1.81 1.81 0 0 1-1.618-2.618l1.227-2.454a1 1 0 0 0 .104-.397l.18-3.588A7.31 7.31 0 0 1 12 2ZM9.17 18a3 3 0 0 0 5.658 0H9.171ZM16.5 2a1 1 0 0 1 1 1v2.5H20a1 1 0 1 1 0 2h-2.5V10a1 1 0 1 1-2 0V7.5H13a1 1 0 1 1 0-2h2.5V3a1 1 0 0 1 1-1Z",
    )
    val BellRinging = bskyVector(
        "Notifications enabled",
        "M12 2.5a7.31 7.31 0 0 1 7.298 6.943l.18 3.589q.012.209.104.397l1.227 2.454a1.81 1.81 0 0 1-1.618 2.618H16.9a5.002 5.002 0 0 1-9.8 0H4.81a1.81 1.81 0 0 1-1.618-2.618l1.227-2.454a1 1 0 0 0 .104-.397l.18-3.589A7.31 7.31 0 0 1 11.999 2.5Zm-2.83 16a3.002 3.002 0 0 0 5.66 0H9.17Zm8.695-16.662a1 1 0 0 1 1.412-.088 11 11 0 0 1 3.6 6.602 1 1 0 0 1-1.977.297 8.98 8.98 0 0 0-2.946-5.399 1 1 0 0 1-.09-1.412ZM4.723 1.75a1.001 1.001 0 0 1 1.324 1.5 8.98 8.98 0 0 0-2.946 5.4 1 1 0 0 1-1.978-.297 11 11 0 0 1 3.6-6.602Z",
    )
}
