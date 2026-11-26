package am.noz.caelum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.noz.caelum.R
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage

enum class AvatarShape {
    Circle,
    RoundedSquare,
}

/**
 * Shared profile, feed, list, and labeler avatar component matching Bluesky sizing and fallback.
 */
@Composable
fun CaelumAvatar(
    url: String?,
    contentDescription: String?,
    size: Dp = 42.dp,
    shapeType: AvatarShape = AvatarShape.Circle,
    isLive: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showBorder: Boolean = true,
) {
    val palette = LocalAlfPalette.current
    val shape = when (shapeType) {
        AvatarShape.Circle -> CircleShape
        AvatarShape.RoundedSquare -> RoundedCornerShape(size * 0.22f)
    }
    val placeholderBg = palette.contrast100.toComposeColor()
    val borderColor = if (isLive) palette.negative500.toComposeColor() else palette.contrast100.toComposeColor()

    Box(
        modifier = modifier
            .size(size)
            .let { base ->
                if (onClick != null) {
                    val hitArea = if (isLive) base else base.clip(shape)
                    hitArea.clickable(role = Role.Image, onClick = onClick)
                } else {
                    base
                }
            }
            .semantics {
                if (!contentDescription.isNullOrBlank()) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            placeholder = painterResource(R.drawable.default_avatar),
            error = painterResource(R.drawable.default_avatar),
            fallback = painterResource(R.drawable.default_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(placeholderBg)
                .then(
                    if (showBorder) Modifier.border(
                        width = if (isLive) 2.dp else 0.5.dp,
                        color = borderColor,
                        shape = shape,
                    ) else Modifier,
                ),
        )
        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(palette.negative500.toComposeColor(), RoundedCornerShape(4.dp))
                    .border(1.dp, palette.contrast0.toComposeColor(), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = tr("LIVE"),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 9.sp,
                )
            }
        }
    }
}
