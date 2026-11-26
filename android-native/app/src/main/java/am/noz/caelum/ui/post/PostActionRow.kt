package am.noz.caelum.ui.post

import android.content.Context
import android.icu.math.BigDecimal.ROUND_DOWN
import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.PostView
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import java.util.Locale
import kotlinx.coroutines.launch

internal val PostView.isAuthorBlocked: Boolean
    get() = author.viewer?.let { it.blockedBy == true || !it.blocking.isNullOrBlank() || it.blockingByList != null } == true

@Composable
internal fun PostActionRow(
    post: PostView,
    signedIn: Boolean,
    disableHaptics: Boolean,
    onReply: () -> Unit,
    onQuote: () -> Unit,
    onRepost: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onMore: () -> Unit,
    onOpenIn: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onSystemShare: (Context, String) -> Unit = { context, url -> LinkNavigationHelper.shareText(context, url) },
) {
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val haptics = rememberHaptics(disableHaptics)
    val iconSize = if (expanded) 22.dp else 18.dp
    val trailingWidth = if (expanded) 40.dp else 33.dp
    val muted = palette.contrast500.toComposeColor()
    val canInteract = !post.isAuthorBlocked
    val canReply = canInteract && post.viewer?.replyDisabled != true
    val canQuote = canInteract && post.viewer?.embeddingDisabled != true
    val canRepost = signedIn && canInteract
    val reposted = !post.viewer?.repost.isNullOrBlank()
    val liked = !post.viewer?.like.isNullOrBlank()
    val saved = post.viewer?.bookmarked == true
    val replyCount = post.replyCount ?: 0
    val repostCount = (post.repostCount ?: 0) + (post.quoteCount ?: 0)
    val likeCount = post.likeCount ?: 0
    val handle = post.author.handle.ifBlank { post.author.did }
    val postUrl = LinkNavigationHelper.postCanonicalWebUrl(handle, post.uri)
    val shareLabel = tr("Share via...")
    val quoteLabel = tr("Quote post")
    val copyLabel = tr("Copy link to post")
    val copyConfirmation = tr("Copied to clipboard")
    val allowQuote by rememberUpdatedState(canQuote)
    val allowRepost by rememberUpdatedState(canRepost)
    var showRepost by remember(post.uri, signedIn) { mutableStateOf(false) }
    var showShare by remember(post.uri, signedIn) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 320.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostActionButton(
                tag = "replyBtn",
                icon = PostActionIcons.Reply,
                iconSize = iconSize,
                tint = muted,
                count = replyCount,
                label = tr(
                    "Reply ({0, plural, one {# reply} other {# replies}})",
                    mapOf("0" to replyCount),
                ),
                enabled = canReply,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tap()
                    onReply()
                },
            )
            PostActionButton(
                tag = "repostBtn",
                icon = PostActionIcons.Repost,
                iconSize = iconSize,
                tint = if (reposted) palette.positive500.toComposeColor() else muted,
                count = repostCount,
                label = tr(
                    if (reposted) "Undo repost ({0, plural, one {# repost} other {# reposts}})"
                    else "Repost ({0, plural, one {# repost} other {# reposts}})",
                    mapOf("0" to repostCount),
                ),
                enabled = canInteract,
                isSelected = reposted,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tap()
                    showRepost = true
                },
                onLongClickLabel = if (canQuote) quoteLabel else tr("Repost or quote post"),
                onLongClick = {
                    haptics.longPress()
                    if (canQuote) onQuote() else showRepost = true
                },
            )
            PostActionButton(
                tag = "likeBtn",
                icon = if (liked) PostActionIcons.Liked else PostActionIcons.Like,
                iconSize = iconSize,
                tint = if (liked) palette.like.toComposeColor() else muted,
                count = likeCount,
                label = tr(
                    if (liked) "Unlike ({0, plural, one {# like} other {# likes}})"
                    else "Like ({0, plural, one {# like} other {# likes}})",
                    mapOf("0" to likeCount),
                ),
                enabled = signedIn && canInteract,
                isSelected = liked,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.toggle(!liked)
                    onLike()
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PostActionButton(
                tag = "postBookmarkBtn",
                icon = if (saved) PostActionIcons.Saved else PostActionIcons.Save,
                iconSize = iconSize,
                tint = if (saved) palette.primary500.toComposeColor() else muted,
                label = tr(if (saved) "Remove from saved posts" else "Add to saved posts"),
                enabled = signedIn,
                isSelected = saved,
                modifier = Modifier.width(trailingWidth),
                onClick = {
                    haptics.toggle(!saved)
                    onSave()
                },
            )
            PostActionButton(
                tag = "postShareBtn",
                icon = PostActionIcons.Share,
                iconSize = iconSize,
                tint = muted,
                label = tr("Open share menu"),
                modifier = Modifier.width(trailingWidth),
                onClick = {
                    haptics.tap()
                    showShare = true
                },
                onLongClickLabel = shareLabel,
                onLongClick = {
                    haptics.longPress()
                    onSystemShare(context, postUrl)
                },
            )
            PostActionButton(
                tag = "postDropdownBtn",
                icon = PostActionIcons.More,
                iconSize = iconSize,
                tint = muted,
                label = tr("Open post options menu"),
                modifier = Modifier.width(trailingWidth),
                onClick = {
                    haptics.tap()
                    onMore()
                },
            )
        }
    }

    if (showRepost && canInteract) {
        PostActionsSheet(onDismiss = { showRepost = false }) { close ->
            PostMenuAction(
                icon = PostActionIcons.Repost,
                label = if (reposted) tr("Remove repost") else tr("Repost", context = "action"),
                enabled = canRepost,
                onClick = {
                    close {
                        if (allowRepost) {
                            if (!reposted) haptics.confirm()
                            onRepost()
                        }
                    }
                },
            )
            PostMenuAction(
                icon = PostActionIcons.Quote,
                label = if (canQuote) quoteLabel else tr("Quote posts disabled"),
                enabled = canQuote,
                onClick = {
                    close {
                        if (allowQuote) {
                            haptics.confirm()
                            onQuote()
                        }
                    }
                },
            )
        }
    }

    if (showShare) {
        PostActionsSheet(onDismiss = { showShare = false }) { close ->
            if (signedIn) {
                PostMenuAction(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = tr("Send via chat"),
                    onClick = { close { onNavigate(AppRoute.SendPostViaChat(post.uri, post.cid)) } },
                )
            }
            PostMenuAction(
                icon = PostActionIcons.Share,
                label = shareLabel,
                onClick = { close { onSystemShare(context, postUrl) } },
            )
            PostMenuAction(
                icon = Icons.Filled.OpenInNew,
                label = openPostInLabel(),
                onClick = { close(onOpenIn) },
            )
            PostMenuAction(
                icon = Icons.Filled.ContentCopy,
                label = copyLabel,
                onClick = { close { LinkNavigationHelper.copyToClipboard(context, copyLabel, postUrl, copyConfirmation) } },
            )
        }
    }
}

@Composable
private fun PostActionButton(
    tag: String,
    icon: ImageVector,
    iconSize: Dp,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
    isSelected: Boolean? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .heightIn(min = if (iconSize == 22.dp) 44.dp else 40.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                hapticFeedbackEnabled = false,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (isSelected != null) selected = isSelected
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (count == null) Arrangement.Center else Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        if (count != null && count > 0) {
            val locale = LocalI18n.current.locale
            val formatted = remember(count, locale) { formatCompactCount(count, locale) }
            Text(
                text = formatted,
                modifier = Modifier.clearAndSetSemantics {},
                style = if (iconSize == 22.dp) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostActionsSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: (afterClose: () -> Unit) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            content { afterClose ->
                if (!isClosing) {
                    isClosing = true
                    scope.launch {
                        try {
                            sheetState.hide()
                            onDismiss()
                            afterClose()
                        } finally {
                            isClosing = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PostMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalAlfPalette.current.contrast800.toComposeColor(),
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.45f)
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) },
        headlineContent = { Text(label, color = color) },
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

private val compactCountFormatter = ThreadLocal<Pair<Locale, CompactDecimalFormat>>()

fun formatCompactCount(count: Int, locale: Locale = Locale.getDefault()): String {
    val formatter = compactCountFormatter.get()?.takeIf { it.first == locale }?.second
        ?: CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).apply {
            setSignificantDigitsUsed(false)
            roundingMode = ROUND_DOWN
            compactCountFormatter.set(locale to this)
        }
    val fractionDigits = if (count >= 10_000) 0 else 1
    if (formatter.maximumFractionDigits != fractionDigits) {
        formatter.maximumFractionDigits = fractionDigits
    }
    return formatter.format(count)
}
