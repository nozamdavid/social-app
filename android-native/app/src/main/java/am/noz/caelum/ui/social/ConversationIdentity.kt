package am.noz.caelum.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.VerificationBadge
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.stringValue(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

internal fun ConvoView.isGroupConvo(): Boolean {
    val type = when (val value = kind) {
        is JsonObject -> value.stringValue("\$type")
        is JsonPrimitive -> value.contentOrNull
        else -> null
    }
    return when (type?.substringAfterLast('#')?.lowercase()) {
        "groupconvo", "group" -> true
        "directconvo", "direct" -> false
        else -> members.size > 2 || groupProperty("name") != null
    }
}

internal fun ConvoView.groupProperty(key: String): String? =
    (kind as? JsonObject)?.stringValue(key) ?: (details as? JsonObject)?.stringValue(key)

internal fun ConvoView.isLockedGroup(): Boolean =
    isGroupConvo() && groupProperty("lockStatus") in setOf("locked", "locked-permanently")

internal fun ConvoView.directPeer(currentDid: String?): ProfileViewBasic? =
    if (isGroupConvo()) null else members.firstOrNull { it.did != currentDid }

@Composable
internal fun chatProfileName(profile: ProfileViewBasic, moderation: ModerationOpts): String {
    return when {
        profile.handle == "missing.invalid" -> tr("Deleted Account")
        ModerationEngine.moderateProfile(profile, moderation).ui(ModerationContext.displayName).blur ->
            profile.chatHandle() ?: tr("Unknown sender")
        else -> profile.displayName?.takeIf { it.isNotBlank() } ?: profile.chatHandle() ?: tr("Unknown sender")
    }
}

internal fun ProfileViewBasic.chatHandle(): String? = handle
    .takeUnless { it.isBlank() || it == "handle.invalid" || it == "missing.invalid" }
    ?.let { "@$it" }

@Composable
internal fun conversationName(convo: ConvoView, currentDid: String?, moderation: ModerationOpts): String =
    if (convo.isGroupConvo()) {
        convo.groupProperty("name")?.takeIf { it.isNotBlank() } ?: tr("Group chat")
    } else {
        convo.directPeer(currentDid)?.let { chatProfileName(it, moderation) } ?: tr("Conversation")
    }

@Composable
internal fun ConversationAvatar(convo: ConvoView, currentDid: String?, moderation: ModerationOpts, size: Dp) {
    if (!convo.isGroupConvo()) {
        ChatProfileAvatar(convo.directPeer(currentDid), moderation, size)
        return
    }

    val profiles = if (convo.members.size > 2) convo.members.filterNot { it.did == currentDid } else convo.members
    val bubbles = when {
        profiles.size >= 4 -> listOf(Bubble(68, -2, -2), Bubble(56, 60, 49), Bubble(42, 14, 74), Bubble(32, 72, 9))
        profiles.size == 3 -> listOf(Bubble(68, -2, -2), Bubble(56, 38, 62), Bubble(46, 71, 18))
        else -> listOf(Bubble(76, -2, -2), Bubble(76, 42, 42))
    }
    val palette = LocalAlfPalette.current
    val scale = size.value / 120f
    val overlap = bubbles.size == 2
    Box(Modifier.size(size)) {
        bubbles.forEachIndexed { index, bubble ->
            val edge = if (overlap) (2 * scale).dp else 0.dp
            val inset = if (size < 120.dp) 2 else 4
            Box(
                Modifier
                    .offset(x = (inset + bubble.x * scale).dp, y = (inset + bubble.y * scale).dp)
                    .zIndex(if (overlap && index == 0) 1f else 0f)
                    .background(palette.contrast0.toComposeColor(), CircleShape)
                    .padding(edge),
            ) {
                ChatProfileAvatar(profiles.getOrNull(index), moderation, (bubble.size * scale).dp)
            }
        }
    }
}

private data class Bubble(val size: Int, val x: Int, val y: Int)

@Composable
internal fun ChatProfileAvatar(profile: ProfileViewBasic?, moderation: ModerationOpts, size: Dp) {
    val showAvatar = profile != null && !ModerationEngine.moderateProfile(profile, moderation)
        .ui(ModerationContext.avatar).blur
    CaelumAvatar(url = profile?.avatar?.takeIf { showAvatar }, contentDescription = null, size = size)
}

@Composable
internal fun ConversationIdentity(
    convo: ConvoView,
    currentDid: String?,
    moderation: ModerationOpts,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    val palette = LocalAlfPalette.current
    val peer = convo.directPeer(currentDid)
    val hasUnread = showPreview && convo.unreadCount > 0
    val unreadDescription = tr("Unread")
    val time = if (showPreview) LocalI18n.current.formatRelativeShort(convo.lastMessage?.stringValue("sentAt")) else ""
    Row(
        modifier = if (hasUnread) modifier.semantics { stateDescription = unreadDescription } else modifier,
        verticalAlignment = if (peer != null && showPreview) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConversationAvatar(convo, currentDid, moderation, size = if (showPreview) 52.dp else 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = conversationName(convo, currentDid, moderation),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                VerificationBadge(peer?.verification)
                if (time.isNotBlank()) {
                    Text(time, color = palette.contrast600.toComposeColor(), style = MaterialTheme.typography.bodySmall)
                }
                if (convo.muted) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = tr("Muted"),
                        tint = palette.contrast600.toComposeColor(),
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (hasUnread) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(palette.primary500.toComposeColor(), CircleShape),
                    )
                }
            }
            peer?.chatHandle()?.let {
                Text(
                    text = it,
                    color = palette.contrast600.toComposeColor(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showPreview) {
                Text(
                    text = chatPreview(convo, currentDid, moderation),
                    color = palette.contrast800.toComposeColor(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun chatPreview(convo: ConvoView, currentDid: String?, moderation: ModerationOpts): String {
    if (convo.isLockedGroup()) return tr("This chat is locked")
    val last = convo.lastMessage ?: return tr("No messages yet")
    if (last.stringValue("\$type") == "chat.bsky.convo.defs#deletedMessageView") return tr("Message deleted")
    val senderDid = (last["sender"] as? JsonObject)?.stringValue("did")
    val sender = convo.members.firstOrNull { it.did == senderDid }
    if (sender != null && ModerationEngine.moderateProfile(sender, moderation).blocked) return tr("This message is hidden")
    val message = last.stringValue("text")?.takeIf { it.isNotBlank() }
        ?: if (last["embed"] is JsonObject) tr("(contains embedded content)") else tr("No messages yet")
    return when {
        senderDid != null && senderDid == currentDid -> tr("You: {message}", mapOf("message" to message))
        convo.isGroupConvo() && sender != null -> {
            val name = chatProfileName(sender, moderation)
            tr("{name}: {message}", mapOf("name" to name, "message" to message))
        }
        else -> message
    }
}
