package am.noz.caelum.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor

@Composable
internal fun PrimaryTabButton(
    label: String,
    icon: ImageVector?,
    iconSize: Dp,
    avatarUrl: String?,
    selected: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val foreground = palette.contrast1000.toComposeColor()
    val badge = if (unreadCount > 99) "99+" else unreadCount.toString()
    val unreadLabel = when {
        unreadCount <= 0 -> null
        unreadCount > 99 -> tr("{0} unread items", mapOf("0" to badge))
        else -> tr(
            "{count, plural, one {# unread item} other {# unread items}}",
            mapOf("count" to unreadCount),
        )
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                hapticFeedbackEnabled = false,
            )
            .semantics {
                contentDescription = label
                this.selected = selected
                if (unreadLabel != null) stateDescription = unreadLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(iconSize).offset(y = 2.dp),
            )
        } else {
            val border = if (selected) Modifier.border(1.dp, foreground, CircleShape) else Modifier
            Box(
                modifier = Modifier.size(28.dp).offset(y = 2.dp).then(border),
                contentAlignment = Alignment.Center,
            ) {
                CaelumAvatar(url = avatarUrl, contentDescription = null, size = if (selected) 25.dp else 26.dp)
            }
        }

        if (unreadCount > 0) {
            Text(
                text = badge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 11.dp, y = 7.dp)
                    .background(palette.primary500.toComposeColor(), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}
