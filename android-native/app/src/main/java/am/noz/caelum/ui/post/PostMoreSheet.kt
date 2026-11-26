package am.noz.caelum.ui.post

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.TimeFormatter
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun PostMoreSheet(
    post: PostView,
    currentUserDid: String?,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onEditPostClick: (PostView) -> Unit,
    onDeletePostClick: (PostView) -> Unit,
    onToggleThreadMuteClick: (PostView) -> Unit,
    onHidePostClick: (PostView) -> Unit,
    onReportPostClick: (PostView) -> Unit,
    onOpenInClick: (PostView) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    contentVisible: Boolean = true,
) {
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val isAuthor = currentUserDid != null && currentUserDid == post.author.did
    var showDeleteConfirm by remember(post.uri, currentUserDid) { mutableStateOf(false) }

    if (showMenu) {
        val createdAt = (post.record["createdAt"] as? JsonPrimitive)?.content ?: post.indexedAt
        val text = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
        val handle = post.author.handle.ifBlank { post.author.did }
        val copyTextLabel = tr("Copy post text")
        val copyLinkLabel = tr("Copy link to post")
        val copyConfirmation = tr("Copied to clipboard")
        var editRemaining by remember(post.uri, createdAt, isAuthor) {
            mutableStateOf(if (isAuthor) TimeFormatter.formatEditCountdown(createdAt) else null)
        }
        LaunchedEffect(post.uri, createdAt, isAuthor) {
            while (editRemaining != null) {
                delay(1000L)
                editRemaining = TimeFormatter.formatEditCountdown(createdAt)
            }
        }

        PostActionsSheet(onDismiss = onDismiss) { close ->
            if (editRemaining != null) {
                PostMenuAction(
                    icon = Icons.Filled.Edit,
                    label = "${tr("Edit")} ($editRemaining)",
                    tint = palette.primary500.toComposeColor(),
                    onClick = { close { onEditPostClick(post) } },
                )
            }
            PostMenuAction(
                icon = Icons.Filled.OpenInNew,
                label = openPostInLabel(),
                onClick = { close { onOpenInClick(post) } },
            )
            if (contentVisible && text.isNotBlank()) {
                PostMenuAction(
                    icon = Icons.Filled.Translate,
                    label = tr("Open in Google Translate"),
                    onClick = {
                        close {
                            val url = "https://translate.google.com/?sl=auto&tl=en&text=${android.net.Uri.encode(text)}"
                            LinkNavigationHelper.openUrl(context, url, false, false, onNavigate)
                        }
                    },
                )
                PostMenuAction(
                    icon = Icons.Filled.ContentCopy,
                    label = copyTextLabel,
                    onClick = { close { LinkNavigationHelper.copyToClipboard(context, copyTextLabel, text, copyConfirmation) } },
                )
            }
            PostMenuAction(
                icon = PostActionIcons.Share,
                label = copyLinkLabel,
                onClick = {
                    close {
                        LinkNavigationHelper.copyToClipboard(
                            context, copyLinkLabel, LinkNavigationHelper.postCanonicalWebUrl(handle, post.uri), copyConfirmation,
                        )
                    }
                },
            )
            if (currentUserDid != null) {
                PostMenuAction(
                    icon = Icons.Filled.VisibilityOff,
                    label = threadMuteLabel(post),
                    onClick = { close { onToggleThreadMuteClick(post) } },
                )
                if (isAuthor) {
                    PostMenuAction(
                        icon = Icons.Filled.DeleteOutline,
                        label = tr("Delete post"),
                        tint = palette.negative500.toComposeColor(),
                        onClick = { close { showDeleteConfirm = true } },
                    )
                } else {
                    PostMenuAction(
                        icon = Icons.Filled.VisibilityOff,
                        label = tr("Hide post for me"),
                        onClick = { close { onHidePostClick(post) } },
                    )
                }
            }
            if (!isAuthor) {
                PostMenuAction(
                    icon = Icons.Filled.Report,
                    label = tr("Report post"),
                    tint = palette.negative500.toComposeColor(),
                    onClick = { close { onReportPostClick(post) } },
                )
            }
        }
    }

    if (showDeleteConfirm && isAuthor) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr("Delete this post?")) },
            text = { Text(tr("If you remove this post, you won't be able to recover it.")) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeletePostClick(post)
                }) {
                    Text(tr("Delete"), color = palette.negative500.toComposeColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(tr("Cancel")) }
            },
        )
    }
}

@Composable
internal fun openPostInLabel(): String {
    val catalog = LocalI18n.current
    val label = catalog.translate("Open in…")
    return if (label == "Open in…" && catalog.locale.language != "en") {
        catalog.translate("Open post") + "…"
    } else label
}

@Composable
internal fun threadMuteLabel(post: PostView): String =
    tr(if (post.viewer?.threadMuted == true) "Unmute thread" else "Mute thread")
