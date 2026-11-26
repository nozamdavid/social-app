package am.noz.caelum.ui.composer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.emoji.EmojiSearchResult
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.storage.DraftEntity
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.bskyVector
import am.noz.caelum.ui.post.asQuotedPost
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val composerGalleryIcon = bskyVector(
    "ComposerGallery",
    "M19 15.355c-1.112.696-2.385 1.1-4 1.1-2.81 0-4.796-1.756-6.312-3.39a1 1 0 0 0-1.273-.16L5 14.71V17a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1.646ZM16 9a1 1 0 1 0 0 2 1 1 0 0 0 0-2Zm3-2a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v5.213l1.246-.932.044-.031a3 3 0 0 1 3.862.455c1.468 1.581 2.942 2.75 4.848 2.75 1.704 0 2.854-.558 4-1.621V7Zm-1 2a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm3 8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v10Z",
)

private val composerCameraIcon = bskyVector(
    "ComposerCamera",
    "M8.371 3.89A2 2 0 0 1 10.035 3h3.93a2 2 0 0 1 1.664.89L17.035 6H20a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.965L8.37 3.89ZM13.965 5h-3.93L8.63 7.11A2 2 0 0 1 6.965 8H4v11h16V8h-2.965a2 2 0 0 1-1.664-.89L13.965 5ZM12 11a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm-4 2a4 4 0 1 1 8 0 4 4 0 0 1-8 0Z",
)

private val composerGifIcon = bskyVector(
    "ComposerGif",
    "M6 12.287c0 1.54.92 2.463 2.48 2.463 1.434 0 2.353-.807 2.353-2.06v-.166c0-.578-.267-.834-.884-.834h-.806c-.416 0-.632.182-.632.535 0 .357.22.55.632.55h.146v.063c0 .36-.299.609-.735.609-.597 0-.904-.4-.904-1.168v-.52c0-.775.307-1.155.951-1.155.464 0 .7.31 1.018.477a.8.8 0 0 0 .409.108c.385 0 .656-.263.656-.636 0-.353-.26-.679-.664-.915-.409-.24-.96-.388-1.548-.388C6.955 9.25 6 10.2 6 11.67v.617Zm6.358 2.385c.526 0 .813-.31.813-.872v-3.627c0-.558-.295-.873-.825-.873s-.825.31-.825.873V13.8c0 .558.302.872.837.872Zm2.565 0c.519 0 .802-.306.802-.872v-.993h1.47c.361 0 .608-.252.608-.613 0-.365-.243-.613-.608-.613h-1.47v-.973h1.666c.35 0 .609-.26.609-.629 0-.368-.26-.64-.609-.64h-2.495c-.507 0-.821.314-.821.826V13.8c0 .554.31.872.848.872ZM19 7a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7Zm2 10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v10Z",
)

@Composable
internal fun ComposerSubjectPreview(post: PostView, isReply: Boolean) {
    val palette = LocalAlfPalette.current
    val session by LocalAppContainer.current.sessionManager.state.collectAsState()
    val moderation = remember(post, session.moderationOpts) {
        ModerationEngine.moderatePost(post, session.moderationOpts)
    }
    val profileModeration = remember(post.author, session.moderationOpts) {
        ModerationEngine.moderateProfile(post.author, session.moderationOpts)
    }
    var expanded by remember(post.uri) { mutableStateOf(false) }
    val text = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
    val expandLabel = tr(
        if (isReply) "Expand or collapse the full post you are replying to" else "Expand or collapse the quoted post",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = expandLabel }
            .clickable(role = Role.Button) { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (isReply) 104.dp else 58.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = post.author.avatar.takeUnless { profileModeration.ui(ModerationContext.avatar).blur },
                contentDescription = null,
                size = 42.dp,
            )
            Column(Modifier.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DisplayNameRow(
                    profile = post.author,
                    showHandle = !isReply,
                    showPronouns = !profileModeration.ui(ModerationContext.profileView).blur,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (text.isNotEmpty()) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.contrast900.toComposeColor(),
                                maxLines = if (expanded) Int.MAX_VALUE else 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (isReply && !moderation.ui(ModerationContext.contentMedia).blur) {
                        ReplyMediaPreview(post.embed)
                    }
                }
                if (expanded && !moderation.ui(ModerationContext.contentView).blur) {
                    val record = post.embed?.get("record") as? JsonObject
                    val quotedRecord = (record?.get("record") as? JsonObject) ?: record
                    val quote = remember(quotedRecord) {
                        quotedRecord
                            ?.takeIf { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.embed.record#viewRecord" }
                            ?.asQuotedPost()
                    }
                    val quotedText = remember(quote, session.moderationOpts) {
                        quote?.let {
                            val decision = ModerationEngine.moderatePost(it, session.moderationOpts)
                            val list = decision.ui(ModerationContext.contentList)
                            val view = decision.ui(ModerationContext.contentView)
                            if (list.blur || list.filter || view.blur || view.filter) null
                            else (it.record["text"] as? JsonPrimitive)?.content
                        }
                    }
                    if (!quotedText.isNullOrBlank()) {
                        Text(
                            text = quotedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast700.toComposeColor(),
                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
        if (isReply) HorizontalDivider(color = palette.contrast100.toComposeColor())
    }
    if (isReply) Spacer(Modifier.height(16.dp))
}

@Composable
private fun ReplyMediaPreview(embed: JsonObject?) {
    val media = (embed?.get("media") as? JsonObject) ?: embed
    val items = (media?.get("images") as? JsonArray) ?: (media?.get("items") as? JsonArray)
    val thumbs = items.orEmpty().mapNotNull { item ->
        val image = item as? JsonObject ?: return@mapNotNull null
        val thumb = (image["thumb"] as? JsonPrimitive) ?: (image["thumbnail"] as? JsonPrimitive)
        thumb?.content
    }.take(4)
    Column(
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        thumbs.chunked(if (thumbs.size <= 2) thumbs.size.coerceAtLeast(1) else 2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { thumb ->
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ComposerPostEditor(
    avatar: String?,
    field: TextFieldValue,
    attachments: List<ComposerAttachment>,
    enabled: Boolean,
    reply: Boolean,
    quote: PostView?,
    onActivate: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onRemovePost: (() -> Unit)?,
    onRemoveAttachment: (Int) -> Unit,
    onEditAlt: (Int) -> Unit,
) {
    val palette = LocalAlfPalette.current
    val placeholder = tr(if (reply) "Write your reply" else "What's up?")
    val editStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Normal,
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1f,
        color = palette.contrast1000.toComposeColor(),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CaelumAvatar(url = avatar, contentDescription = null, size = 42.dp)
            BasicTextField(
                value = field,
                onValueChange = {
                    onActivate()
                    onValueChange(it)
                },
                modifier = Modifier.weight(1f).heightIn(min = 100.dp)
                    .onFocusChanged { if (it.isFocused) onActivate() }
                    .semantics { contentDescription = placeholder },
                enabled = enabled,
                textStyle = editStyle,
                cursorBrush = SolidColor(palette.primary500.toComposeColor()),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 90.dp)) {
                        if (field.text.isEmpty()) Text(placeholder, style = editStyle, color = palette.contrast600.toComposeColor())
                        inner()
                    }
                },
            )
            if (onRemovePost != null) {
                IconButton(enabled = enabled, onClick = onRemovePost) {
                    Icon(Icons.Filled.Close, tr("Remove post"))
                }
            }
        }
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(start = 58.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(attachments) { index, attachment ->
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)).background(palette.contrast50.toComposeColor())) {
                        AttachmentPreview(attachment, Modifier.fillMaxSize(), scale = ContentScale.Crop)
                        IconButton(
                            onClick = { onRemoveAttachment(index) },
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .size(40.dp)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.72f), CircleShape),
                        ) {
                            Icon(Icons.Filled.Close, tr("Remove attachment"), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .clickable(
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClickLabel = tr("Add alt text"),
                                    onClick = { onEditAlt(index) },
                                )
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (attachment.altText.isBlank()) "+ ALT" else "ALT",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        if (quote != null) {
            Surface(
                modifier = Modifier.padding(start = 58.dp, bottom = 12.dp).fillMaxWidth(),
                color = palette.contrast25.toComposeColor(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(10.dp)) { ComposerSubjectPreview(quote, isReply = false) }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: ComposerAttachment, modifier: Modifier = Modifier, scale: ContentScale) {
    when (attachment) {
        is ComposerAttachment.Image ->
            AsyncImage(attachment.uri, attachment.altText.ifBlank { tr("Image") }, modifier, contentScale = scale)
        is ComposerAttachment.Gif ->
            AsyncImage(attachment.previewUri, attachment.altText.ifBlank { attachment.title }, modifier, contentScale = scale)
        is ComposerAttachment.Video -> VideoAttachmentPreview(attachment, modifier, scale)
    }
}

@Composable
private fun VideoAttachmentPreview(video: ComposerAttachment.Video, modifier: Modifier, scale: ContentScale) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(null, video.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(context, Uri.parse(video.uri))
                    val largestSide = maxOf(video.width, video.height, 1)
                    if (Build.VERSION.SDK_INT >= 27) {
                        metadata.getScaledFrameAtTime(
                            0L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            (video.width * 320 / largestSide).coerceAtLeast(1),
                            (video.height * 320 / largestSide).coerceAtLeast(1),
                        )
                    } else {
                        metadata.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                } finally {
                    metadata.release()
                }
            }.getOrNull()
        }
    }
    val description = video.altText.ifBlank { tr("Video") }
    Box(modifier, contentAlignment = Alignment.Center) {
        val frame = thumbnail
        if (frame != null) {
            Image(frame.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = scale)
        } else {
            Icon(Icons.Outlined.VideoLibrary, description, Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun ComposerToolbar(
    language: String,
    count: Int,
    enabled: Boolean,
    canPickMedia: Boolean,
    canPickGif: Boolean,
    canAddPost: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onGif: () -> Unit,
    onLanguage: () -> Unit,
    onAddPost: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val i18n = LocalI18n.current
    val languageLabel = tr("Post language selection")
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIcon(composerGalleryIcon, tr("Add media to post"), enabled && canPickMedia, onGallery)
        ToolbarIcon(composerCameraIcon, tr("Camera"), enabled && canPickMedia, onCamera)
        ToolbarIcon(composerGifIcon, tr("Select GIF"), enabled && canPickGif, onGif)
        Spacer(Modifier.weight(1f))
        if (canAddPost) ToolbarIcon(Icons.Filled.Add, tr("Add another post to thread"), enabled, onAddPost)
        TextButton(
            enabled = enabled,
            onClick = onLanguage,
            modifier = Modifier.weight(2f, fill = false).semantics { contentDescription = languageLabel },
        ) {
            Text(
                Locale.forLanguageTag(language).getDisplayLanguage(i18n.locale).ifBlank { language },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val remaining = 300 - count
        Text(
            text = NumberFormat.getIntegerInstance(i18n.locale).format(remaining),
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
            color = if (remaining < 0) palette.negative500.toComposeColor() else palette.contrast1000.toComposeColor(),
            style = MaterialTheme.typography.labelMedium,
        )
        val track = palette.contrast200.toComposeColor()
        val fill = if (remaining < 0) palette.negative500.toComposeColor() else palette.primary500.toComposeColor()
        Canvas(Modifier.size(29.dp)) {
            val stroke = 1.dp.toPx()
            drawCircle(track, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            if (count > 0) {
                drawArc(
                    color = fill,
                    startAngle = -90f,
                    sweepAngle = count.coerceAtMost(300) * 360f / 300f,
                    useCenter = false,
                    style = Stroke(stroke),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(vector: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalAlfPalette.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp, 48.dp)) {
        Icon(
            imageVector = vector,
            contentDescription = description,
            tint = (if (enabled) palette.primary500 else palette.contrast500).toComposeColor(),
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
internal fun ComposerSuggestions(
    emojis: List<EmojiSearchResult>,
    mentions: List<ProfileViewBasic>,
    onEmoji: (EmojiSearchResult) -> Unit,
    onMention: (ProfileViewBasic) -> Unit,
) {
    val palette = LocalAlfPalette.current
    if (emojis.isEmpty() && mentions.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(emojis, key = { it.id }) { item ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.contrast50.toComposeColor(),
                modifier = Modifier.clickable(role = Role.Button) { onEmoji(item) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(item.native)
                    Text(":${item.shortcodes.firstOrNull() ?: item.id}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        items(mentions, key = { it.did }) { actor ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.contrast50.toComposeColor(),
                modifier = Modifier.clickable(role = Role.Button) { onMention(actor) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CaelumAvatar(url = actor.avatar, contentDescription = null, size = 20.dp)
                    Text("@${actor.handle}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun MediaAltTextDialog(attachment: ComposerAttachment, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var alt by remember(attachment) { mutableStateOf(attachment.altText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add alt text")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachmentPreview(attachment, Modifier.fillMaxWidth().height(130.dp), ContentScale.Fit)
                OutlinedTextField(
                    value = alt,
                    onValueChange = { if (it.length <= 2000) alt = it },
                    label = { Text(tr("Descriptive alt text")) },
                    minLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(alt.trim()) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
internal fun SavedDraftsDialog(
    accountDid: String,
    canSave: Boolean,
    currentDraftId: String?,
    onSave: () -> Unit,
    onSelect: (DraftEntity) -> Unit,
    onDelete: (DraftEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val dao = LocalAppContainer.current.database.draftDao()
    val savedDrafts by remember(dao, accountDid) { dao.observeForAccount(accountDid) }.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Drafts")) },
        text = {
            if (savedDrafts.isEmpty()) {
                Text(tr("No drafts yet"))
            } else {
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(savedDrafts, key = { it.id }) { draft ->
                        val payload = remember(draft) { draft.localPayload() }
                        val label = payload?.posts?.firstOrNull { it.isNotBlank() }
                            ?: when {
                                payload == null -> tr("Unable to open this draft")
                                payload.mediaByPost.any { it.isNotEmpty() } -> tr("Post with media")
                                payload.quotePost != null -> tr("Quote post")
                                payload.replyTo != null -> tr("Reply")
                                else -> tr("Untitled draft")
                            }
                        ListItem(
                            headlineContent = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(enabled = draft.id != currentDraftId, onClick = { onDelete(draft) }) {
                                    Icon(Icons.Filled.Close, tr("Delete"))
                                }
                            },
                            modifier = Modifier.clickable(enabled = payload != null, role = Role.Button) { onSelect(draft) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
        dismissButton = {
            if (canSave) TextButton(onClick = onSave) { Text(tr("Save draft")) }
        },
    )
}
