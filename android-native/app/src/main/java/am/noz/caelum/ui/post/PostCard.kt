package am.noz.caelum.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ActorStatusView
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LinkMismatchWarningDialog
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * A post in a feed or thread, with moderation shields, rich text, embeds, and actions.
 */
@Composable
fun PostCard(
    post: PostView,
    moderationOpts: ModerationOpts,
    currentUserDid: String?,
    reason: JsonObject? = null,
    replyParentAuthorHandle: String? = null,
    showTopThreadLine: Boolean = false,
    showBottomThreadLine: Boolean = false,
    largeAltBadge: Boolean = false,
    disableAutoplay: Boolean = false,
    disableHaptics: Boolean = false,
    useLinkRedirects: Boolean = true,
    openBskyLinksInCaelum: Boolean = true,
    onPostClick: (PostView) -> Unit,
    onReplyClick: (PostView) -> Unit,
    onQuoteClick: (PostView) -> Unit,
    onRepostClick: (PostView) -> Unit,
    onLikeClick: (PostView) -> Unit,
    onBookmarkClick: (PostView) -> Unit,
    onEditPostClick: (PostView) -> Unit,
    onDeletePostClick: (PostView) -> Unit,
    onToggleThreadMuteClick: (PostView) -> Unit,
    onHidePostClick: (PostView) -> Unit,
    onReportPostClick: (PostView) -> Unit,
    onOpenInClick: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    inThread: Boolean = false,
) {
    val context = LocalContext.current
    val palette = LocalAlfPalette.current

    val modDecision = remember(post.uri, post.record, post.embed, post.labels, post.author, moderationOpts) {
        ModerationEngine.moderatePost(post, moderationOpts)
    }
    val listUi = remember(modDecision) { modDecision.ui(ModerationContext.contentList) }
    val mediaUi = remember(modDecision) { modDecision.ui(ModerationContext.contentMedia) }
    val avatarBlurred = remember(post.author, moderationOpts) {
        ModerationEngine.moderateProfile(post.author, moderationOpts).ui(ModerationContext.avatar).blur
    }

    var shieldExpanded by remember(post.uri) { mutableStateOf(false) }
    var showMoreSheet by remember(post.uri, currentUserDid) { mutableStateOf(false) }
    var pendingExternalMismatch by remember(post.uri) { mutableStateOf<Pair<String, String>?>(null) }

    val text = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
    val createdAt = (post.record["createdAt"] as? JsonPrimitive)?.content ?: post.indexedAt
    val updatedAt = (post.record["updatedAt"] as? JsonPrimitive)?.content
    val facets: List<RichTextFacet> = remember(post.record["facets"]) {
        (post.record["facets"] as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
        }.orEmpty()
    }

    val borderColor = palette.contrast100.toComposeColor()
    val mutedColor = palette.contrast600.toComposeColor()
    val handleForNav = post.author.handle.ifBlank { post.author.did }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable { onPostClick(post) },
    ) {
        if (listUi.blur && (!shieldExpanded || listUi.noOverride)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.contrast25.toComposeColor())
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable(enabled = !listUi.noOverride) { shieldExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = mutedColor, modifier = Modifier.size(18.dp))
                    Text(
                        text = postModerationCauseTitle(listUi.blurs.firstOrNull()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                    )
                }
                if (!listUi.noOverride) {
                    Text(
                        text = tr("Show"),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primary500.toComposeColor(),
                    )
                }
            }
            if (!showBottomThreadLine) {
                HorizontalDivider(color = borderColor, thickness = 0.5.dp)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (inThread) 16.dp else 18.dp,
                    end = if (inThread) 16.dp else 15.dp,
                    top = if (showTopThreadLine) 2.dp else 10.dp,
                    bottom = if (showBottomThreadLine) 2.dp else 4.dp,
                ),
        ) {
            // Repost or Pinned header
            val reasonType = (reason?.get("\$type") as? JsonPrimitive)?.content
            if (reasonType == "app.bsky.feed.defs#reasonRepost") {
                val byObj = reason["by"] as? JsonObject
                val byName = (byObj?.get("displayName") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: (byObj?.get("handle") as? JsonPrimitive)?.content
                if (!byName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.padding(start = 34.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(PostActionIcons.Repost, contentDescription = null, tint = mutedColor, modifier = Modifier.size(14.dp))
                        Text(
                            tr("Reposted by {reposter}", mapOf("reposter" to byName)),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor,
                        )
                    }
                }
            } else if (reasonType == "app.bsky.feed.defs#reasonPin") {
                Row(
                    modifier = Modifier.padding(start = 34.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = mutedColor, modifier = Modifier.size(14.dp))
                    Text(tr("Pinned"), style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
            }

            val isLive = !avatarBlurred && post.author.status.isLiveAvatarStatus()
            val railColor = palette.contrast200.toComposeColor()
            Row(
                modifier = Modifier.fillMaxWidth().drawBehind {
                    val x = if (layoutDirection == LayoutDirection.Ltr) 21.dp.toPx() else size.width - 21.dp.toPx()
                    val avatarTop = if (showTopThreadLine) 8.dp.toPx() else 0f
                    if (showTopThreadLine) {
                        drawLine(railColor, Offset(x, -2.dp.toPx()), Offset(x, avatarTop - 3.dp.toPx()), 2.dp.toPx())
                    }
                    val railStart = avatarTop + (if (isLive) 50.dp else 46.dp).toPx()
                    if (showBottomThreadLine && size.height + 2.dp.toPx() > railStart) {
                        drawLine(railColor, Offset(x, railStart), Offset(x, size.height + 2.dp.toPx()), 2.dp.toPx())
                    }
                },
            ) {
                CaelumAvatar(
                    url = post.author.avatar.takeUnless { avatarBlurred },
                    contentDescription = post.author.displayName ?: post.author.handle,
                    size = 42.dp,
                    shapeType = if (post.author.associated?.labeler == true) AvatarShape.RoundedSquare else AvatarShape.Circle,
                    isLive = isLive,
                    onClick = { onNavigate(AppRoute.Profile(handleForNav)) },
                    modifier = Modifier.padding(top = if (showTopThreadLine) 8.dp else 0.dp),
                )

                Spacer(Modifier.width(if (inThread) 12.dp else 10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DisplayNameRow(
                            profile = post.author,
                            timestamp = LocalI18n.current.formatRelativeShort(createdAt),
                            showHandle = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (updatedAt != null) {
                            Text(
                                text = "· ${tr("Edited")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedColor,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }

                    if (!replyParentAuthorHandle.isNullOrBlank()) {
                        Text(
                            text = tr("Reply to {handle}", mapOf("handle" to "@$replyParentAuthorHandle")),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                            modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
                        )
                    }

                    PostAlertPills(
                        moderation = listUi,
                        moderationOpts = moderationOpts,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    if (text.isNotBlank()) {
                        var showFullText by rememberSaveable(post.uri, text) { mutableStateOf(false) }
                        var hasMoreText by remember(post.uri, text) { mutableStateOf(false) }
                        RichTextView(
                            text = text,
                            facets = facets,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = palette.contrast1000.toComposeColor(),
                            ),
                            modifier = Modifier.padding(top = 4.dp).testTag("postText"),
                            maxLines = if (showFullText) Int.MAX_VALUE else 25,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!showFullText) hasMoreText = it.didOverflowHeight },
                            onMentionClick = { didOrHandle -> onNavigate(AppRoute.Profile(didOrHandle)) },
                            onTagClick = { tag -> onNavigate(AppRoute.Hashtag(tag)) },
                            onLinkClick = { displayText, uri ->
                                if (LinkNavigationHelper.isPotentiallyMisleadingLink(displayText, uri)) {
                                    pendingExternalMismatch = displayText to uri
                                } else {
                                    LinkNavigationHelper.openUrl(
                                        context = context,
                                        rawUrl = uri,
                                        useLinkRedirects = useLinkRedirects,
                                        openBskyLinksInCaelum = openBskyLinksInCaelum,
                                        onInternalRoute = onNavigate,
                                    )
                                }
                            },
                        )
                        if (hasMoreText && !showFullText) {
                            Text(
                                text = tr("Show more"),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.primary500.toComposeColor(),
                                modifier = Modifier.heightIn(min = 36.dp).wrapContentHeight()
                                    .clickable(role = Role.Button) { showFullText = true },
                            )
                        }
                    }

                    if (post.embed != null) {
                        Spacer(Modifier.height(8.dp))
                        PostEmbeds(
                            embed = post.embed,
                            mediaModeration = mediaUi,
                            moderationOpts = moderationOpts,
                            blurReason = postModerationCauseTitle(mediaUi.blurs.firstOrNull()),
                            largeAltBadge = largeAltBadge,
                            disableAutoplay = disableAutoplay,
                            onOpenLightbox = onOpenLightbox,
                            onOpenVideoLightbox = onOpenVideoLightbox,
                            onNavigate = onNavigate,
                            onOpenExternalLink = { uri ->
                                LinkNavigationHelper.openUrl(
                                    context = context,
                                    rawUrl = uri,
                                    useLinkRedirects = useLinkRedirects,
                                    openBskyLinksInCaelum = openBskyLinksInCaelum,
                                    onInternalRoute = onNavigate,
                                )
                            },
                        )
                    }

                    PostActionRow(
                        post = post,
                        signedIn = currentUserDid != null,
                        disableHaptics = disableHaptics,
                        onReply = { onReplyClick(post) },
                        onQuote = { onQuoteClick(post) },
                        onRepost = { onRepostClick(post) },
                        onLike = { onLikeClick(post) },
                        onSave = { onBookmarkClick(post) },
                        onMore = { showMoreSheet = true },
                        onOpenIn = { onOpenInClick(post) },
                        onNavigate = onNavigate,
                    )
                }
            }
        }

        if (!showBottomThreadLine) {
            HorizontalDivider(color = borderColor, thickness = 0.5.dp)
        }
    }

    PostMoreSheet(
        post = post,
        currentUserDid = currentUserDid,
        showMenu = showMoreSheet,
        onDismiss = { showMoreSheet = false },
        onEditPostClick = onEditPostClick,
        onDeletePostClick = onDeletePostClick,
        onToggleThreadMuteClick = onToggleThreadMuteClick,
        onHidePostClick = onHidePostClick,
        onReportPostClick = onReportPostClick,
        onOpenInClick = onOpenInClick,
        onNavigate = onNavigate,
        contentVisible = !listUi.blur || shieldExpanded && !listUi.noOverride,
    )

    pendingExternalMismatch?.let { (disp, uri) ->
        LinkMismatchWarningDialog(
            displayText = disp,
            targetUri = uri,
            onConfirm = {
                pendingExternalMismatch = null
                LinkNavigationHelper.openUrl(context, uri, useLinkRedirects, openBskyLinksInCaelum, onNavigate)
            },
            onDismiss = { pendingExternalMismatch = null },
        )
    }
}

internal fun ActorStatusView?.isLiveAvatarStatus(): Boolean =
    this != null && status == "app.bsky.actor.status#live" && isActive == true &&
        (expiresAt == null || runCatching { Instant.parse(expiresAt).isAfter(Instant.now()) }.getOrDefault(false))
